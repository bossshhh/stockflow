package com.stockflow.user;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockflow.common.exception.InvalidRequestParameterException;
import com.stockflow.user.dto.CreateUserRequest;
import com.stockflow.user.dto.RegisterRequest;
import com.stockflow.user.dto.UserResponse;
import com.stockflow.user.dto.UserValidation;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /** Public self-registration: always a CUSTOMER. */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        return create(request.email(), request.password(), request.fullName(), Role.CUSTOMER);
    }

    /** Admin creates WAREHOUSE, STAFF or ADMIN accounts. CUSTOMER accounts come only from self-registration. */
    @Transactional
    public UserResponse createStaffAccount(CreateUserRequest request) {
        if (request.role() == Role.CUSTOMER) {
            throw new InvalidRequestParameterException(
                    "Customers register themselves; role must be WAREHOUSE, STAFF or ADMIN");
        }
        return create(request.email(), request.password(), request.fullName(), request.role());
    }

    /** Creates the configured first ADMIN if that email is not registered yet. Safe to call on every start. */
    @Transactional
    public void ensureAdminExists(String email, String password) {
        if (userRepository.existsByEmail(normalizeEmail(email))) {
            return;
        }
        UserResponse admin = create(email, password, "Administrator", Role.ADMIN);
        log.info("Created bootstrap admin account id={}", admin.id());
    }

    @Transactional(readOnly = true)
    public UserResponse getById(long id) {
        return userRepository.findById(id).map(UserResponse::from).orElseThrow(() -> new UserNotFoundException(id));
    }

    /** For token refresh: the account behind a refresh token. Empty when it no longer exists. */
    @Transactional(readOnly = true)
    public Optional<UserCredentials> findCredentialsById(long id) {
        return userRepository.findById(id)
                .map(user -> new UserCredentials(user.getId(), user.getEmail(), user.getPasswordHash(),
                        user.getRole(), user.isEnabled()));
    }

    /** For login only. Empty when no account has this email. */
    @Transactional(readOnly = true)
    public Optional<UserCredentials> findCredentialsByEmail(String email) {
        return userRepository.findByEmail(normalizeEmail(email))
                .map(user -> new UserCredentials(user.getId(), user.getEmail(), user.getPasswordHash(),
                        user.getRole(), user.isEnabled()));
    }

    private UserResponse create(String rawEmail, String password, String fullName, Role role) {
        requirePasswordFitsBcrypt(password);
        String email = normalizeEmail(rawEmail);
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        User user = new User(email, passwordEncoder.encode(password), fullName.strip(), role, clock.instant());
        try {
            User saved = userRepository.saveAndFlush(user);
            log.info("Created user id={} role={}", saved.getId(), saved.getRole());
            return UserResponse.from(saved);
        } catch (DataIntegrityViolationException ex) {
            // Two registrations with the same email at the same moment: the UNIQUE constraint decides.
            if (ex.getCause() instanceof ConstraintViolationException violation
                    && User.EMAIL_UNIQUE_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName())) {
                throw new EmailAlreadyRegisteredException();
            }
            throw ex;
        }
    }

    /** Bean Validation counts characters; BCrypt's limit is in bytes, and "é" is 2 bytes in UTF-8. */
    static void requirePasswordFitsBcrypt(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > UserValidation.PASSWORD_MAX_BYTES) {
            throw new InvalidRequestParameterException(
                    "password must be at most " + UserValidation.PASSWORD_MAX_BYTES + " bytes");
        }
    }

    /** The form emails are stored and compared in; login rate limits are keyed by it too. */
    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}

package com.stockflow.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.stockflow.common.exception.InvalidRequestParameterException;
import com.stockflow.user.dto.CreateUserRequest;
import com.stockflow.user.dto.RegisterRequest;
import com.stockflow.user.dto.UserResponse;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final String PASSWORD = "a-long-enough-password";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void registerStoresHashLowercaseEmailAndCustomerRole() {
        when(userRepository.existsByEmail("ada@example.com")).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("$2a$hash");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(call -> withId(call.getArgument(0)));

        UserResponse response = userService.register(new RegisterRequest(" Ada@Example.COM ", PASSWORD, " Ada "));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("$2a$hash");
        assertThat(saved.getValue().getEmail()).isEqualTo("ada@example.com");
        assertThat(response.role()).isEqualTo(Role.CUSTOMER);
        assertThat(response.fullName()).isEqualTo("Ada");
    }

    @Test
    void duplicateEmailIsRejectedBeforeHashing() {
        when(userRepository.existsByEmail("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(new RegisterRequest("ada@example.com", PASSWORD, "Ada")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void passwordOver72BytesIsRejected() {
        String seventyThreeBytes = "a".repeat(71) + "é";

        assertThatThrownBy(() -> userService.register(new RegisterRequest("ada@example.com", seventyThreeBytes, "Ada")))
                .isInstanceOf(InvalidRequestParameterException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void adminCannotCreateCustomerAccounts() {
        assertThatThrownBy(() -> userService.createStaffAccount(
                new CreateUserRequest("cat@example.com", PASSWORD, "Cat", Role.CUSTOMER)))
                .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void ensureAdminExistsDoesNothingWhenAlreadyRegistered() {
        when(userRepository.existsByEmail("admin@stockflow.local")).thenReturn(true);

        userService.ensureAdminExists("Admin@StockFlow.local", PASSWORD);

        verify(userRepository, never()).saveAndFlush(any());
    }

    private static User withId(User user) {
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }
}

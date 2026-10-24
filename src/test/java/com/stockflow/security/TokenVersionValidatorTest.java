package com.stockflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import com.stockflow.user.Role;
import com.stockflow.user.UserCredentials;
import com.stockflow.user.UserService;

class TokenVersionValidatorTest {

    private static final long USER_ID = 7;

    private final UserService userService = mock(UserService.class);
    private TokenVersionValidator validator;

    @BeforeEach
    void setUp() {
        SecurityProperties properties = new SecurityProperties(
                new SecurityProperties.JwtSettings("secret", Duration.ofMinutes(15), "stockflow", Duration.ofSeconds(10)),
                null, 4, null, null, null);
        validator = new TokenVersionValidator(userService, properties);
    }

    @Test
    void aTokenWithTheCurrentVersionOfAnEnabledUserIsValid() {
        user(3, true);
        assertThat(validator.validate(token(3)).hasErrors()).isFalse();
    }

    @Test
    void anOlderVersionOrADisabledAccountOrAnUnknownUserIsRejected() {
        user(3, true);
        assertThat(validator.validate(token(2)).hasErrors()).isTrue();

        validator.evict(USER_ID);
        user(3, false);
        assertThat(validator.validate(token(3)).hasErrors()).isTrue();

        validator.evict(USER_ID);
        when(userService.findCredentialsById(USER_ID)).thenReturn(Optional.empty());
        assertThat(validator.validate(token(3)).hasErrors()).isTrue();
    }

    @Test
    void tokensFromBeforeVersionsExistedCountAsVersionZero() {
        user(0, true);
        assertThat(validator.validate(token(null)).hasErrors()).isFalse();
    }

    @Test
    void theLookupIsCachedUntilEvicted() {
        user(3, true);
        validator.validate(token(3));
        validator.validate(token(3));
        verify(userService, times(1)).findCredentialsById(USER_ID);

        user(4, true);
        assertThat(validator.validate(token(3)).hasErrors()).as("still cached").isFalse();
        validator.evict(USER_ID);
        assertThat(validator.validate(token(3)).hasErrors()).as("after eviction").isTrue();
    }

    private void user(int tokenVersion, boolean enabled) {
        when(userService.findCredentialsById(USER_ID)).thenReturn(Optional.of(
                new UserCredentials(USER_ID, "ada@example.com", "hash", Role.CUSTOMER, enabled, tokenVersion)));
    }

    private static Jwt token(Integer tokenVersion) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "HS256")
                .subject(String.valueOf(USER_ID)).issuedAt(Instant.now()).claim("roles", List.of("CUSTOMER"));
        if (tokenVersion != null) {
            builder.claim(JwtTokenService.TOKEN_VERSION_CLAIM, tokenVersion);
        }
        return builder.build();
    }
}

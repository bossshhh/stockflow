package com.stockflow.security;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.stockflow.security.dto.TokenResponse;
import com.stockflow.user.CurrentUser;
import com.stockflow.user.Role;

/**
 * Issues signed access tokens. A JWT is signed, NOT encrypted: anyone holding it can read the claims, so it
 * carries only the user id and role, never an email or other personal data.
 */
@Service
public class JwtTokenService {

    private static final String TOKEN_TYPE = "Bearer";

    private final JwtEncoder jwtEncoder;
    private final SecurityProperties properties;
    private final Clock clock;

    public JwtTokenService(JwtEncoder jwtEncoder, SecurityProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    /** Claim with the user's token version; see {@link TokenVersionValidator}. */
    public static final String TOKEN_VERSION_CLAIM = "tv";

    public TokenResponse issue(long userId, Role role, int tokenVersion) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plus(properties.jwt().ttl()))
                .claim(CurrentUser.ROLES_CLAIM, List.of(role.name()))
                .claim(TOKEN_VERSION_CLAIM, tokenVersion)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, TOKEN_TYPE, properties.jwt().ttl().toSeconds());
    }
}

package com.stockflow.security;

import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.stockflow.common.web.ApiHeaders;
import com.stockflow.user.CurrentUser;
import com.stockflow.user.Role;

/**
 * The whole access policy in one place. Coarse rules (which ROLE may call which URL) live here; ownership
 * rules (a customer may only see their own orders) live in the services, where the data is.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

    private static final String CUSTOMER = Role.CUSTOMER.name();
    private static final String WAREHOUSE = Role.WAREHOUSE.name();
    private static final String STAFF = Role.STAFF.name();
    private static final String ADMIN = Role.ADMIN.name();

    private static final int MIN_SECRET_BYTES = 32;
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String ROLE_PREFIX = "ROLE_";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter,
                                            SecurityErrorHandler errorHandler,
                                            CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                // CSRF attacks ride on cookies the browser sends automatically. We use bearer tokens that our
                // own client code adds, and no session cookie, so there is nothing to forge.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // Public
                        .requestMatchers(EndpointRequest.to(HealthEndpoint.class, InfoEndpoint.class)).permitAll()
                        // refresh/logout authenticate with the HttpOnly cookie, not a bearer token
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/", "/api/v1/catalog", "/api/v1/catalog/*").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        // Admin
                        .requestMatchers("/api/v1/admin/**").hasRole(ADMIN)
                        // Products: everyone signed in may browse; only staff may change
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/**").authenticated()
                        .requestMatchers("/api/v1/products/**").hasAnyRole(STAFF, ADMIN)
                        // Inventory: the warehouse may read it; only staff may change it. Never customers.
                        .requestMatchers(HttpMethod.GET, "/api/v1/inventory/**").hasAnyRole(WAREHOUSE, STAFF, ADMIN)
                        .requestMatchers("/api/v1/inventory/**").hasAnyRole(STAFF, ADMIN)
                        // Orders
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders").hasRole(CUSTOMER)
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders/*/confirm").hasAnyRole(STAFF, ADMIN)
                        // Fulfilment: the warehouse's job; staff can do it too
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders/*/pack", "/api/v1/orders/*/ship",
                                "/api/v1/orders/*/deliver").hasAnyRole(WAREHOUSE, STAFF, ADMIN)
                        // Cancelling: the customer (own orders, checked in OrderService) or staff, not the warehouse
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders/*/cancel").hasAnyRole(CUSTOMER, STAFF, ADMIN)
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders/summary").hasAnyRole(STAFF, ADMIN)
                        // Viewing and searching: any role; OrderService restricts customers to their own
                        .requestMatchers("/api/v1/orders/**").authenticated()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler));
        return http.build();
    }

    /**
     * First filter of all: a request without the edge secret never reaches Spring Security, and the rate
     * limiter sees the visitor's address. Does nothing unless the edge secret is configured.
     */
    @Bean
    FilterRegistrationBean<EdgeGatewayFilter> edgeGatewayFilter(SecurityProperties properties,
                                                                 SecurityErrorHandler errorHandler) {
        FilterRegistrationBean<EdgeGatewayFilter> registration =
                new FilterRegistrationBean<>(new EdgeGatewayFilter(properties, errorHandler));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    PasswordEncoder passwordEncoder(SecurityProperties properties) {
        return new BCryptPasswordEncoder(properties.bcryptStrength());
    }

    /**
     * Spring's DaoAuthenticationProvider: loads the user, checks the BCrypt hash, and hashes a dummy password
     * when the email does not exist, so response time does not reveal which emails are registered.
     */
    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    JwtEncoder jwtEncoder(SecurityProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(signingKey(properties)));
    }

    /** Checks signature (HS256 only), expiry against the app Clock, and the issuer. */
    @Bean
    JwtDecoder jwtDecoder(SecurityProperties properties, Clock clock, TokenVersionValidator tokenVersions) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(signingKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator();
        timestamps.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestamps, new JwtIssuerValidator(properties.jwt().issuer()), tokenVersions));
        return decoder;
    }

    /** Turns the token's "roles" claim (e.g. ["STAFF"]) into Spring authorities (ROLE_STAFF). */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(CurrentUser.ROLES_CLAIM);
        authorities.setAuthorityPrefix(ROLE_PREFIX);
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /** Only the configured origins may call the API from a browser; never a wildcard. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        List<String> origins = properties.cors().allowedOrigins();
        if (origins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalStateException("CORS allowed origins must be explicit; wildcards are not allowed");
        }
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of(HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.PUT.name(),
                HttpMethod.DELETE.name(), HttpMethod.OPTIONS.name()));
        config.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, ApiHeaders.IDEMPOTENCY_KEY));
        config.setExposedHeaders(List.of(HttpHeaders.LOCATION, HttpHeaders.RETRY_AFTER, ApiHeaders.IDEMPOTENT_REPLAYED));
        // Tokens travel in a header, not cookies, so the browser never needs to send credentials.
        config.setAllowCredentials(false);
        config.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /** Fails at startup, not at first login, if the secret is missing, not base64, or too short for HS256. */
    static SecretKey signingKey(SecurityProperties properties) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(properties.jwt().secret());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("stockflow.security.jwt.secret must be base64", ex);
        }
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("stockflow.security.jwt.secret must decode to at least "
                    + MIN_SECRET_BYTES + " bytes for HS256");
        }
        return new SecretKeySpec(bytes, HMAC_SHA256);
    }
}

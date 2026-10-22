package com.stockflow.security;

import java.io.IOException;
import java.time.Clock;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.common.exception.ApiError;
import com.stockflow.common.exception.ErrorCode;

/**
 * 401 and 403 happen in the security filter chain, before any controller runs, so the
 * {@code @RestControllerAdvice} never sees them. This writes the same {@link ApiError} body for them.
 */
@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final String BEARER = "Bearer";

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SecurityErrorHandler(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** No token, or a token that is invalid or expired. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, BEARER);
        String message = exception instanceof InvalidBearerTokenException
                ? "Access token is invalid or expired"
                : "Authentication is required";
        write(response, request, ErrorCode.UNAUTHORIZED, message);
    }

    /** Valid token, but the role is not allowed to do this. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        write(response, request, ErrorCode.FORBIDDEN, "You do not have permission to perform this action");
    }

    private void write(HttpServletResponse response, HttpServletRequest request, ErrorCode code, String message)
            throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError body = new ApiError(clock.instant(), code.status().value(), code.name(), message,
                request.getRequestURI(), List.of());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}

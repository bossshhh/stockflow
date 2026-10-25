package com.stockflow.security;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

import com.stockflow.common.exception.ErrorCode;

/**
 * Free-tier deployment: the API project's public Vercel URL must only answer requests that came through the Vercel
 * middleware, which adds a shared secret and the visitor's IP. Without the secret anyone could call that URL
 * directly with a made-up client IP and get unlimited password guesses (QUESTIONS Q13), so the IP header is
 * believed only next to the right secret. Off when no secret is configured; runs before Spring Security.
 */
public class EdgeGatewayFilter extends OncePerRequestFilter {

    public static final String SECRET_HEADER = "X-StockFlow-Edge-Secret";
    public static final String CLIENT_IP_HEADER = "X-StockFlow-Client-IP";

    private static final String HEALTH_PATH = "/actuator/health";
    // Only these two shapes reach InetAddress, which would do a DNS lookup for anything it reads as a name
    // ("beef.cafe" is all hex letters) and accepts old shorthand like "1.2.3". IPv6 has a colon, which makes
    // InetAddress parse it as a literal, never look it up.
    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");
    private static final int MAX_OCTET = 255;

    private final SecurityProperties.EdgeSettings settings;
    private final SecurityErrorHandler errors;

    public EdgeGatewayFilter(SecurityProperties properties, SecurityErrorHandler errors) {
        this.settings = properties.edge();
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // The CI deploy check and uptime monitors call the health check directly, without the secret.
        return !settings.enabled() || path.equals(HEALTH_PATH) || path.startsWith(HEALTH_PATH + "/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!hasRightSecret(request.getHeader(SECRET_HEADER))) {
            errors.write(response, request, ErrorCode.FORBIDDEN, "Requests must come through the StockFlow front end");
            return;
        }
        String claimedIp = request.getHeader(CLIENT_IP_HEADER);
        if (claimedIp == null) {
            chain.doFilter(request, response);
            return;
        }
        Optional<String> clientIp = normalizedIp(claimedIp);
        if (clientIp.isEmpty()) {
            errors.write(response, request, ErrorCode.MALFORMED_REQUEST, "Invalid client address");
            return;
        }
        chain.doFilter(new ClientAddressRequest(request, clientIp.get()), response);
    }

    /** Constant-time comparison, so response timing does not reveal how much of a guess was right. */
    private boolean hasRightSecret(String sent) {
        return sent != null && MessageDigest.isEqual(
                sent.getBytes(StandardCharsets.UTF_8), settings.secret().getBytes(StandardCharsets.UTF_8));
    }

    private static Optional<String> normalizedIp(String value) {
        Matcher ipv4 = IPV4.matcher(value);
        boolean isIpv4 = ipv4.matches();
        if (isIpv4) {
            for (int group = 1; group <= ipv4.groupCount(); group++) {
                if (Integer.parseInt(ipv4.group(group)) > MAX_OCTET) {
                    return Optional.empty();
                }
            }
        } else if (value.indexOf(':') < 0 || !IPV6.matcher(value).matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(InetAddress.getByName(value).getHostAddress());
        } catch (UnknownHostException ex) {
            return Optional.empty();
        }
    }

    /** The rate limiter and logs read getRemoteAddr(); here that is the visitor, not the Vercel proxy in between. */
    private static final class ClientAddressRequest extends HttpServletRequestWrapper {

        private final String clientIp;

        ClientAddressRequest(HttpServletRequest request, String clientIp) {
            super(request);
            this.clientIp = clientIp;
        }

        @Override
        public String getRemoteAddr() {
            return clientIp;
        }

        @Override
        public String getRemoteHost() {
            return clientIp;
        }
    }
}

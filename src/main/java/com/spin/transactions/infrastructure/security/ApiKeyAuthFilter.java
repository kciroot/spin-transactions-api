package com.spin.transactions.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Service-to-service authentication with a static API key in the X-API-Key header.
 * Keys are kept only as SHA-256 digests and compared in constant time (MessageDigest.isEqual),
 * so response timing does not leak how many characters matched.
 * This filter only AUTHENTICATES when the key is valid; rejecting is the job of the authorization rules.
 */
class ApiKeyAuthFilter extends OncePerRequestFilter {

    static final String HEADER = "X-API-Key";

    private final List<byte[]> validKeyDigests;

    ApiKeyAuthFilter(List<String> validKeys) {
        this.validKeyDigests = validKeys.stream().map(ApiKeyAuthFilter::sha256).toList();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        if (presented != null && isValid(presented)) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "api-client", null, List.of(new SimpleGrantedAuthority("ROLE_CLIENT"))));
        }
        chain.doFilter(request, response);
    }

    private boolean isValid(String presented) {
        byte[] digest = sha256(presented);
        boolean match = false;
        for (byte[] valid : validKeyDigests) {
            match |= MessageDigest.isEqual(digest, valid); // no early exit: constant work per key
        }
        return match;
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

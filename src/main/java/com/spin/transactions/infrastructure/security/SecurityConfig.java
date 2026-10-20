package com.spin.transactions.infrastructure.security;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Deny by default: everything needs a valid API key except health probes and the API docs.
 * Stateless (no session, no cookies) so CSRF protection does not apply and any pod can serve any request.
 * Fail-safe: the application refuses to start without at least one configured API key.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProperties properties,
                                            @Value("${provider.mock.enabled:false}") boolean mockProviderEnabled)
            throws Exception {
        List<String> keys = properties.validApiKeys();
        if (keys.isEmpty()) {
            throw new IllegalStateException(
                    "No API key configured: set security.api-keys (env SECURITY_API_KEYS) to at least one value");
        }

        List<String> publicPaths = new ArrayList<>(List.of(
                "/actuator/health/**", "/actuator/info", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"));
        if (mockProviderEnabled) {
            publicPaths.add("/provider/**"); // the embedded mock plays the role of a third party
        }

        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(publicPaths.toArray(new String[0])).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(new ProblemAuthenticationEntryPoint()))
                .addFilterBefore(new ApiKeyAuthFilter(keys), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}

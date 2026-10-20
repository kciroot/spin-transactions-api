package com.spin.transactions.infrastructure.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** security.api-keys (env SECURITY_API_KEYS, comma separated). Several keys allow zero-downtime rotation. */
@ConfigurationProperties("security")
public record SecurityProperties(List<String> apiKeys) {

    public List<String> validApiKeys() {
        return apiKeys == null ? List.of() : apiKeys.stream().map(String::trim).filter(k -> !k.isEmpty()).toList();
    }
}

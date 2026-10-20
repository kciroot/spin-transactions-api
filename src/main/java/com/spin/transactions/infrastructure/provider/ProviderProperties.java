package com.spin.transactions.infrastructure.provider;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("provider")
public record ProviderProperties(
        @DefaultValue("http://localhost:8080") String baseUrl,
        @DefaultValue("1s") Duration connectTimeout,
        @DefaultValue("2s") Duration readTimeout,
        @DefaultValue Retry retry,
        @DefaultValue CircuitBreaker circuitBreaker) {

    public record Retry(@DefaultValue("3") int maxAttempts, @DefaultValue("200ms") Duration initialBackoff) {
    }

    public record CircuitBreaker(@DefaultValue("50") float failureRateThreshold,
                                 @DefaultValue("20") int slidingWindowSize,
                                 @DefaultValue("10") int minimumNumberOfCalls,
                                 @DefaultValue("10s") Duration openStateDuration) {
    }
}

package com.spin.transactions.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.spin.transactions.domain.ProviderGateway;
import com.spin.transactions.domain.ProviderRequest;
import com.spin.transactions.domain.ProviderResult;
import com.spin.transactions.domain.ProviderUnavailableException;
import com.spin.transactions.domain.TransactionType;
import com.spin.transactions.infrastructure.provider.ResilientProviderGateway;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ResilientProviderGatewayTest {

    private static final ProviderResult APPROVED = new ProviderResult.Approved("txn-1", new BigDecimal("10.00"), Instant.now());
    private static final ProviderRequest REQUEST = new ProviderRequest(UUID.randomUUID(), "acc-1",
            TransactionType.CREDIT, new BigDecimal("100.00"), "MXN");

    private final AtomicInteger calls = new AtomicInteger();

    private Retry retry(int maxAttempts) {
        return Retry.of("test", RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .waitDuration(Duration.ofMillis(1))
                .retryOnException(e -> e instanceof ProviderUnavailableException)
                .build());
    }

    private CircuitBreaker breaker(int window) {
        return CircuitBreaker.of("test", CircuitBreakerConfig.custom()
                .slidingWindowSize(window)
                .minimumNumberOfCalls(window)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .recordException(e -> e instanceof ProviderUnavailableException)
                .build());
    }

    private ProviderGateway failingTimes(int failures) {
        return request -> {
            if (calls.incrementAndGet() <= failures) {
                throw new ProviderUnavailableException("boom");
            }
            return APPROVED;
        };
    }

    @Test
    void retriesTransientFailuresUntilSuccess() {
        ResilientProviderGateway gateway = new ResilientProviderGateway(failingTimes(2), retry(3), breaker(10));

        assertEquals(APPROVED, gateway.execute(REQUEST));
        assertEquals(3, calls.get());
    }

    @Test
    void givesUpAfterMaxAttempts() {
        ResilientProviderGateway gateway = new ResilientProviderGateway(failingTimes(100), retry(3), breaker(10));

        assertThrows(ProviderUnavailableException.class, () -> gateway.execute(REQUEST));
        assertEquals(3, calls.get());
    }

    @Test
    void definitiveRejectionIsNeverRetried() {
        ProviderGateway rejecting = request -> {
            calls.incrementAndGet();
            return new ProviderResult.Rejected("INSUFFICIENT_FUNDS", "no");
        };
        ResilientProviderGateway gateway = new ResilientProviderGateway(rejecting, retry(3), breaker(10));

        gateway.execute(REQUEST);

        assertEquals(1, calls.get());
    }

    @Test
    void openCircuitFailsFastWithoutCallingProvider() {
        ResilientProviderGateway gateway = new ResilientProviderGateway(failingTimes(100), retry(1), breaker(2));

        assertThrows(ProviderUnavailableException.class, () -> gateway.execute(REQUEST));
        assertThrows(ProviderUnavailableException.class, () -> gateway.execute(REQUEST));
        int callsBeforeOpen = calls.get();

        ProviderUnavailableException e = assertThrows(ProviderUnavailableException.class, () -> gateway.execute(REQUEST));

        assertEquals(callsBeforeOpen, calls.get());
        assertTrue(e.getMessage().contains("circuit"));
    }
}

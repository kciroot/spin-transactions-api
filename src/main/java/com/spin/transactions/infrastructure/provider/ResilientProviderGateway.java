package com.spin.transactions.infrastructure.provider;

import com.spin.transactions.domain.ProviderGateway;
import com.spin.transactions.domain.ProviderRequest;
import com.spin.transactions.domain.ProviderResult;
import com.spin.transactions.domain.ProviderUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.util.function.Supplier;

/**
 * Decorator: Retry( CircuitBreaker( delegate ) ). Every attempt goes through the breaker; when the breaker
 * is open the call fails fast and is NOT retried. Only ProviderUnavailableException counts as a failure,
 * a provider REJECTED answer is a successful, definitive response.
 */
public class ResilientProviderGateway implements ProviderGateway {

    private final ProviderGateway delegate;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    public ResilientProviderGateway(ProviderGateway delegate, Retry retry, CircuitBreaker circuitBreaker) {
        this.delegate = delegate;
        this.retry = retry;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public ProviderResult execute(ProviderRequest request) {
        Supplier<ProviderResult> call = () -> delegate.execute(request);
        Supplier<ProviderResult> guarded = CircuitBreaker.decorateSupplier(circuitBreaker, call);
        Supplier<ProviderResult> resilient = Retry.decorateSupplier(retry, guarded);
        try {
            return resilient.get();
        } catch (CallNotPermittedException e) {
            throw new ProviderUnavailableException("Provider circuit breaker is open", e);
        }
    }
}

package com.spin.transactions.infrastructure.provider;

import com.spin.transactions.domain.ProviderGateway;
import com.spin.transactions.domain.ProviderUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
class ProviderConfig {

    @Bean
    ProviderGateway providerGateway(ProviderProperties props) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(props.connectTimeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(props.readTimeout());

        RestClient restClient = RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(requestFactory)
                .build();

        CircuitBreaker circuitBreaker = CircuitBreaker.of("provider", CircuitBreakerConfig.custom()
                .failureRateThreshold(props.circuitBreaker().failureRateThreshold())
                .slidingWindowSize(props.circuitBreaker().slidingWindowSize())
                .minimumNumberOfCalls(props.circuitBreaker().minimumNumberOfCalls())
                .waitDurationInOpenState(props.circuitBreaker().openStateDuration())
                .permittedNumberOfCallsInHalfOpenState(3)
                .recordException(e -> e instanceof ProviderUnavailableException)
                .build());

        Retry retry = Retry.of("provider", RetryConfig.custom()
                .maxAttempts(props.retry().maxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(props.retry().initialBackoff(), 2.0, 0.5))
                .retryOnException(e -> e instanceof ProviderUnavailableException)
                .build());

        return new ResilientProviderGateway(new HttpProviderGateway(restClient), retry, circuitBreaker);
    }
}

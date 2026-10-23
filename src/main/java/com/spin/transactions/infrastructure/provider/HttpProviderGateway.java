package com.spin.transactions.infrastructure.provider;

import com.spin.transactions.domain.ProviderGateway;
import com.spin.transactions.domain.ProviderRequest;
import com.spin.transactions.domain.ProviderResult;
import com.spin.transactions.domain.ProviderUnavailableException;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Plain HTTP adapter for POST /provider/v1/execute. No resilience here (see ResilientProviderGateway).
 * Translation rules:
 *   2xx + valid body              -> Approved
 *   any status + body REJECTED    -> Rejected (definitive business answer, never retried)
 *   5xx without a REJECTED body   -> ProviderUnavailableException (retried / counted by the circuit breaker)
 *   4xx without a REJECTED body   -> Rejected(PROVIDER_ERROR): the provider refused our request, retrying won't help
 *   I/O error or timeout          -> ProviderUnavailableException
 */
public class HttpProviderGateway implements ProviderGateway {

    private static final String EXECUTE_PATH = "/provider/v1/execute";

    private final RestClient restClient;

    public HttpProviderGateway(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public ProviderResult execute(ProviderRequest request) {
        ProviderPayload payload = new ProviderPayload(request.accountId(), request.type().name(),
                request.amount(), request.currency());
        try {
            return restClient.post()
                    .uri(EXECUTE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", request.transactionId().toString())
                    .body(payload)
                    .exchange((req, res) -> {
                        HttpStatusCode status = res.getStatusCode();
                        ProviderBody body;
                        try {
                            body = res.bodyTo(ProviderBody.class);
                        } catch (RuntimeException e) {
                            body = null; // unparsable body: decide from the status code alone
                        }
                        return interpret(status, body);
                    });
        } catch (ResourceAccessException e) {
            throw new ProviderUnavailableException("Provider unreachable or timed out", e);
        }
    }

    private ProviderResult interpret(HttpStatusCode status, ProviderBody body) {
        if (body != null && "REJECTED".equals(body.status())) {
            return new ProviderResult.Rejected(body.code(), body.message());
        }
        if (status.is2xxSuccessful()) {
            if (body == null || body.transactionId() == null || body.balance() == null) {
                throw new ProviderUnavailableException("Malformed provider response (HTTP " + status.value() + ")");
            }
            return new ProviderResult.Approved(body.transactionId(), body.balance(), body.executedAt());
        }
        if (status.is5xxServerError()) {
            throw new ProviderUnavailableException("Provider responded HTTP " + status.value());
        }
        return new ProviderResult.Rejected("PROVIDER_ERROR", "Provider refused the request (HTTP " + status.value() + ")");
    }

    record ProviderPayload(String accountId, String type, BigDecimal amount, String currency) {
    }

    /** Superset of the success and failure bodies of the provider contract. */
    record ProviderBody(String transactionId, String status, BigDecimal balance, Instant executedAt, String code,
                        String message) {
    }
}

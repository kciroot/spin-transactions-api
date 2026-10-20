package com.spin.transactions.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.spin.transactions.domain.ProviderRequest;
import com.spin.transactions.domain.ProviderResult;
import com.spin.transactions.domain.ProviderUnavailableException;
import com.spin.transactions.domain.TransactionType;
import com.spin.transactions.infrastructure.provider.HttpProviderGateway;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpProviderGatewayTest {

    private static final String URL = "http://provider/provider/v1/execute";

    private final UUID transactionId = UUID.randomUUID();
    private final ProviderRequest request = new ProviderRequest(transactionId, "acc-123456", TransactionType.CREDIT,
            new BigDecimal("1500.00"), "MXN");

    private MockRestServiceServer server;
    private HttpProviderGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://provider");
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new HttpProviderGateway(builder.build());
    }

    @Test
    void approvedResponseIsMappedAndRequestCarriesIdempotencyKey() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", transactionId.toString()))
                .andExpect(jsonPath("$.accountId").value("acc-123456"))
                .andExpect(jsonPath("$.type").value("CREDIT"))
                .andExpect(jsonPath("$.currency").value("MXN"))
                .andRespond(withSuccess("""
                        {"transactionId":"txn-789","status":"APPROVED","balance":5500.00,
                         "executedAt":"2025-03-15T10:30:00Z"}""", MediaType.APPLICATION_JSON));

        ProviderResult result = gateway.execute(request);

        ProviderResult.Approved approved = (ProviderResult.Approved) result;
        assertEquals("txn-789", approved.transactionId());
        assertEquals(0, new BigDecimal("5500.00").compareTo(approved.balance()));
        server.verify();
    }

    @Test
    void rejectedBodyOn4xxIsMappedToRejected() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"status\":\"REJECTED\",\"code\":\"INSUFFICIENT_FUNDS\",\"message\":\"no balance\"}"));

        ProviderResult.Rejected rejected = (ProviderResult.Rejected) gateway.execute(request);

        assertEquals("INSUFFICIENT_FUNDS", rejected.code());
        assertEquals("no balance", rejected.message());
    }

    @Test
    void rejectedBodyOn5xxIsStillADefinitiveRejection() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"status\":\"REJECTED\",\"code\":\"ACCOUNT_BLOCKED\",\"message\":\"blocked\"}"));

        ProviderResult.Rejected rejected = (ProviderResult.Rejected) gateway.execute(request);

        assertEquals("ACCOUNT_BLOCKED", rejected.code());
    }

    @Test
    void serverErrorWithoutRejectedBodyIsUnavailable() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(ProviderUnavailableException.class, () -> gateway.execute(request));
    }

    @Test
    void clientErrorWithoutRejectedBodyIsProviderError() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        ProviderResult.Rejected rejected = (ProviderResult.Rejected) gateway.execute(request);

        assertEquals("PROVIDER_ERROR", rejected.code());
    }

    @Test
    void malformedSuccessBodyIsUnavailable() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThrows(ProviderUnavailableException.class, () -> gateway.execute(request));
    }

    @Test
    void networkErrorIsUnavailable() {
        server.expect(requestTo(URL)).andRespond(req -> {
            throw new IOException("connection reset");
        });

        assertThrows(ProviderUnavailableException.class, () -> gateway.execute(request));
    }
}

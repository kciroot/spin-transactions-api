package com.spin.transactions.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.spin.transactions.domain.ProviderGateway;
import com.spin.transactions.domain.ProviderResult;
import com.spin.transactions.domain.ProviderUnavailableException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Full stack (controller -> service -> JPA/Flyway on H2) with the provider replaced by a Mockito mock. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionControllerIT {

    private static final String API_KEY = "test-key";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ProviderGateway provider;

    @BeforeEach
    void resetProvider() {
        Mockito.reset(provider);
        when(provider.execute(any())).thenReturn(
                new ProviderResult.Approved("txn-789", new BigDecimal("5500.00"), Instant.now()));
    }

    private static MockHttpServletRequestBuilder authedGet(String url) {
        return get(url).header("X-API-Key", API_KEY);
    }

    private static String account() {
        return "acc-" + UUID.randomUUID();
    }

    private ResultActions postTransaction(String accountId, String type, String amount, String currency, String idempotencyKey)
            throws Exception {
        var request = post("/transactions").header("X-API-Key", API_KEY).contentType(MediaType.APPLICATION_JSON).content("""
                {"accountId":"%s","type":"%s","amount":%s,"currency":"%s","description":"Transferencia recibida"}
                """.formatted(accountId, type, amount, currency));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mvc.perform(request);
    }

    @Test
    void executedTransactionReturns201WithProviderData() throws Exception {
        postTransaction(account(), "CREDIT", "1500.00", "MXN", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("EXECUTED"))
                .andExpect(jsonPath("$.providerTransactionId").value("txn-789"))
                .andExpect(jsonPath("$.balanceAfter").value(5500.0))
                .andExpect(jsonPath("$.amount").value(1500.0))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void providerRejectionIsPersistedAndReturnedAsRejected() throws Exception {
        when(provider.execute(any())).thenReturn(new ProviderResult.Rejected("INSUFFICIENT_FUNDS", "no balance"));

        postTransaction(account(), "DEBIT", "500.00", "MXN", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void providerOutageReturns502WithFailedTransaction() throws Exception {
        when(provider.execute(any())).thenThrow(new ProviderUnavailableException("timeout"));

        postTransaction(account(), "CREDIT", "100.00", "MXN", null)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value("PROVIDER_UNAVAILABLE"));
    }

    @Test
    void businessRuleViolationReturns422AndNeverCallsProvider() throws Exception {
        postTransaction(account(), "DEBIT", "10000.01", "MXN", null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DEBIT_LIMIT_EXCEEDED"));
        postTransaction(account(), "CREDIT", "1.00", "MXN", null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("AMOUNT_TOO_LOW"));
        postTransaction(account(), "CREDIT", "100.00", "USD", null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_CURRENCY"));
        postTransaction(account(), "CREDIT", "100.00", "PESOS", null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_CURRENCY"));

        verifyNoInteractions(provider);
    }

    @Test
    void malformedRequestReturns400WithFieldErrors() throws Exception {
        mvc.perform(post("/transactions").header("X-API-Key", API_KEY).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"\",\"type\":\"CREDIT\",\"currency\":\"MXN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.accountId").exists())
                .andExpect(jsonPath("$.errors.amount").exists());
    }

    @Test
    void unknownTransactionTypeReturns400() throws Exception {
        postTransaction(account(), "TRANSFER", "100.00", "MXN", null).andExpect(status().isBadRequest());
    }

    @Test
    void idempotencyKeyReplaysWithoutCallingProviderTwice() throws Exception {
        String account = account();
        String key = UUID.randomUUID().toString();

        postTransaction(account, "CREDIT", "100.00", "MXN", key).andExpect(status().isCreated());
        postTransaction(account, "CREDIT", "100.00", "MXN", key).andExpect(status().isOk());
        postTransaction(account, "CREDIT", "999.00", "MXN", key).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));

        verify(provider, times(1)).execute(any());
    }

    @Test
    void listSupportsFiltersAndPagination() throws Exception {
        String account = account();
        for (int i = 0; i < 3; i++) {
            postTransaction(account, "CREDIT", "100.00", "MXN", null).andExpect(status().isCreated());
        }
        postTransaction(account, "DEBIT", "50.00", "MXN", null).andExpect(status().isCreated());

        mvc.perform(authedGet("/transactions").param("accountId", account).param("limit", "3").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2));

        mvc.perform(authedGet("/transactions").param("accountId", account).param("limit", "3").param("page", "2"))
                .andExpect(jsonPath("$.data.length()").value(1));

        mvc.perform(authedGet("/transactions").param("accountId", account).param("type", "DEBIT"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.data[0].type").value("DEBIT"));

        mvc.perform(authedGet("/transactions").param("accountId", account).param("status", "REJECTED"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void invalidPaginationReturns400() throws Exception {
        mvc.perform(authedGet("/transactions").param("limit", "1000")).andExpect(status().isBadRequest());
        mvc.perform(authedGet("/transactions").param("page", "0")).andExpect(status().isBadRequest());
        mvc.perform(authedGet("/transactions").param("status", "NOPE")).andExpect(status().isBadRequest());
    }

    @Test
    void getByIdReturnsTransactionOr404() throws Exception {
        mvc.perform(authedGet("/transactions/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));
    }

    @Test
    void requestsWithoutOrWithWrongApiKeyAreRejectedWith401() throws Exception {
        mvc.perform(get("/transactions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/transactions").header("X-API-Key", "wrong-key"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"a\",\"type\":\"CREDIT\",\"amount\":10,\"currency\":\"MXN\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(provider);
    }

    @Test
    void healthProbeIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}

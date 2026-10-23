package com.spin.transactions.infrastructure.provider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stand-in for the external provider, only active with provider.mock.enabled=true.
 * Holds balances in memory (every account starts at 4000.00, so CREDIT 1500.00 answers 5500.00 as in the
 * challenge example) and picks a scenario from the accountId suffix:
 *   -nofunds -> 422 REJECTED INSUFFICIENT_FUNDS     -blocked -> 403 REJECTED ACCOUNT_BLOCKED
 *   -down    -> 503 always                          -slow    -> answers after 5s (read timeout is 2s)
 *   -flaky   -> 503 on the first two calls, then OK (shows the retry policy working)
 */
@RestController
@RequestMapping("/provider/v1")
@ConditionalOnProperty(name = "provider.mock.enabled", havingValue = "true")
class MockProviderController {

    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("4000.00");

    @Value("${provider.mock.latency-ms:0}")
    private long latencyMs;

    private final Map<String, BigDecimal> balances = new HashMap<>();
    private final Map<String, AtomicInteger> flakyCalls = new ConcurrentHashMap<>();

    record MockRequest(String accountId, String type, BigDecimal amount, String currency) {
    }

    @PostMapping("/execute")
    ResponseEntity<Map<String, Object>> execute(@RequestBody MockRequest request) throws InterruptedException {
        String account = request.accountId();

        if (latencyMs > 0) {
            Thread.sleep(latencyMs); // simulate a real provider's response time
        }

        if (account.endsWith("-down")) {
            return ResponseEntity.status(503).body(Map.of("message", "Service unavailable"));
        }
        if (account.endsWith("-slow")) {
            Thread.sleep(5_000);
        }
        if (account.endsWith("-flaky")
                && flakyCalls.computeIfAbsent(account, k -> new AtomicInteger()).incrementAndGet() <= 2) {
            return ResponseEntity.status(503).body(Map.of("message", "Temporary failure"));
        }
        if (account.endsWith("-nofunds")) {
            return rejected(422, "INSUFFICIENT_FUNDS", "The account does not have enough balance to complete the transaction");
        }
        if (account.endsWith("-blocked")) {
            return rejected(403, "ACCOUNT_BLOCKED", "The account is blocked");
        }

        synchronized (balances) {
            BigDecimal current = balances.getOrDefault(account, INITIAL_BALANCE);
            BigDecimal next = "DEBIT".equals(request.type())
                    ? current.subtract(request.amount())
                    : current.add(request.amount());
            if (next.signum() < 0) {
                return rejected(422, "INSUFFICIENT_FUNDS", "The account does not have enough balance to complete the transaction");
            }
            balances.put(account, next);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("transactionId", "txn-" + Long.toHexString(System.nanoTime()));
            body.put("status", "APPROVED");
            body.put("balance", next);
            body.put("executedAt", Instant.now().toString());
            return ResponseEntity.ok(body);
        }
    }

    private ResponseEntity<Map<String, Object>> rejected(int httpStatus, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "REJECTED");
        body.put("code", code);
        body.put("message", message);
        return ResponseEntity.status(httpStatus).body(body);
    }
}

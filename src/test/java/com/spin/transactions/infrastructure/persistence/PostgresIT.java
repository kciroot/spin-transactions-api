package com.spin.transactions.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spin.transactions.application.ExecuteTransactionCommand;
import com.spin.transactions.application.ExecutionResult;
import com.spin.transactions.application.TransactionService;
import com.spin.transactions.domain.PageResult;
import com.spin.transactions.domain.ProviderGateway;
import com.spin.transactions.domain.ProviderResult;
import com.spin.transactions.domain.Transaction;
import com.spin.transactions.domain.TransactionQuery;
import com.spin.transactions.domain.TransactionRepository;
import com.spin.transactions.domain.TransactionStatus;
import com.spin.transactions.domain.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs against a REAL PostgreSQL (Testcontainers). Skipped automatically when Docker is not available.
 * What H2 cannot prove: the Flyway migration on PostgreSQL, the Hibernate schema validation against it,
 * and the behaviour of the UNIQUE constraint under real concurrent inserts.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class PostgresIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TransactionService service;

    @Autowired
    private TransactionRepository repository;

    @MockBean
    private ProviderGateway provider;

    private ExecuteTransactionCommand command(String accountId, TransactionType type, String amount, String key) {
        return new ExecuteTransactionCommand(accountId, type, new BigDecimal(amount), "MXN", "pg-it", key);
    }

    @Test
    void concurrentRequestsWithTheSameIdempotencyKeyExecuteExactlyOnce() throws Exception {
        when(provider.execute(any())).thenAnswer(invocation -> {
            Thread.sleep(50); // widen the race window
            return new ProviderResult.Approved("txn-1", new BigDecimal("10.00"), Instant.now());
        });
        String key = UUID.randomUUID().toString();
        String account = "acc-" + UUID.randomUUID();
        int threads = 12;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGun = new CountDownLatch(1);
        List<Future<ExecutionResult>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                startGun.await();
                return service.execute(command(account, TransactionType.CREDIT, "100.00", key));
            }));
        }
        startGun.countDown();

        Set<UUID> ids = new HashSet<>();
        int created = 0;
        for (Future<ExecutionResult> future : futures) {
            ExecutionResult result = future.get(15, TimeUnit.SECONDS);
            ids.add(result.transaction().id());
            if (!result.replayed()) {
                created++;
            }
        }
        pool.shutdown();

        assertEquals(1, ids.size(), "all callers must see the same transaction");
        assertEquals(1, created, "exactly one caller creates it, the rest are replays");
        verify(provider, times(1)).execute(any());
    }

    @Test
    void migrationMappingAndFiltersWorkOnPostgres() {
        when(provider.execute(any())).thenReturn(
                new ProviderResult.Approved("txn-2", new BigDecimal("1234.50"), Instant.now()));
        String account = "acc-" + UUID.randomUUID();

        Transaction credit = service.execute(command(account, TransactionType.CREDIT, "1500.00", null)).transaction();
        service.execute(command(account, TransactionType.DEBIT, "20.00", null));
        service.execute(command(account, TransactionType.DEBIT, "30.00", null));

        Transaction reloaded = repository.findById(credit.id()).orElseThrow();
        assertEquals(TransactionStatus.EXECUTED, reloaded.status());
        assertEquals(0, new BigDecimal("1500.00").compareTo(reloaded.amount()));
        assertEquals(0, new BigDecimal("1234.50").compareTo(reloaded.balanceAfter()));

        PageResult<Transaction> debits = repository.search(
                new TransactionQuery(account, null, TransactionType.DEBIT, 1, 1));
        assertEquals(2, debits.totalElements());
        assertEquals(2, debits.totalPages());
        assertEquals(1, debits.items().size());

        PageResult<Transaction> newestFirst = repository.search(new TransactionQuery(account, null, null, 1, 10));
        assertEquals(3, newestFirst.items().size());
        assertTrue(!newestFirst.items().get(0).createdAt().isBefore(newestFirst.items().get(2).createdAt()));
    }
}

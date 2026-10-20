package com.spin.transactions.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.spin.transactions.domain.BusinessRuleViolationException;
import com.spin.transactions.domain.DuplicateIdempotencyKeyException;
import com.spin.transactions.domain.IdempotencyKeyConflictException;
import com.spin.transactions.domain.PageResult;
import com.spin.transactions.domain.ProviderGateway;
import com.spin.transactions.domain.ProviderRequest;
import com.spin.transactions.domain.ProviderResult;
import com.spin.transactions.domain.ProviderUnavailableException;
import com.spin.transactions.domain.Transaction;
import com.spin.transactions.domain.TransactionNotFoundException;
import com.spin.transactions.domain.TransactionQuery;
import com.spin.transactions.domain.TransactionRepository;
import com.spin.transactions.domain.TransactionRules;
import com.spin.transactions.domain.TransactionStatus;
import com.spin.transactions.domain.TransactionType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class TransactionServiceTest {

    private static final Instant NOW = Instant.parse("2025-03-15T10:30:00Z");

    private final InMemoryRepository repository = new InMemoryRepository();
    private final StubProvider provider = new StubProvider();
    private final TransactionService service = new TransactionService(repository, provider, new TransactionRules(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    private ExecuteTransactionCommand command(TransactionType type, String amount, String currency, String key) {
        return new ExecuteTransactionCommand("acc-123456", type, new BigDecimal(amount), currency, "test", key);
    }

    @Test
    void approvedProviderResponseIsPersistedAsExecutedWithBalance() {
        provider.respondWith(r -> new ProviderResult.Approved("txn-789", new BigDecimal("5500.00"), NOW));

        ExecutionResult result = service.execute(command(TransactionType.CREDIT, "1500.00", "MXN", null));

        Transaction tx = result.transaction();
        assertEquals(TransactionStatus.EXECUTED, tx.status());
        assertEquals("txn-789", tx.providerTransactionId());
        assertEquals(new BigDecimal("5500.00"), tx.balanceAfter());
        assertEquals(NOW, tx.createdAt());
        assertEquals(1, repository.stored.size());
        assertEquals(TransactionStatus.EXECUTED, repository.stored.get(tx.id()).status());
    }

    @Test
    void rejectedProviderResponseIsPersistedAsRejectedWithCode() {
        provider.respondWith(r -> new ProviderResult.Rejected("INSUFFICIENT_FUNDS", "not enough balance"));

        Transaction tx = service.execute(command(TransactionType.DEBIT, "500.00", "MXN", null)).transaction();

        assertEquals(TransactionStatus.REJECTED, tx.status());
        assertEquals("INSUFFICIENT_FUNDS", tx.errorCode());
        assertNull(tx.balanceAfter());
    }

    @Test
    void unavailableProviderIsPersistedAsFailed() {
        provider.respondWith(r -> {
            throw new ProviderUnavailableException("timeout");
        });

        Transaction tx = service.execute(command(TransactionType.CREDIT, "100.00", "MXN", null)).transaction();

        assertEquals(TransactionStatus.FAILED, tx.status());
        assertEquals("PROVIDER_UNAVAILABLE", tx.errorCode());
    }

    @Test
    void businessRuleViolationNeverReachesProviderNorDatabase() {
        assertThrows(BusinessRuleViolationException.class,
                () -> service.execute(command(TransactionType.DEBIT, "10000.01", "MXN", null)));
        assertThrows(BusinessRuleViolationException.class,
                () -> service.execute(command(TransactionType.CREDIT, "100.00", "USD", null)));

        assertEquals(0, provider.calls.size());
        assertTrue(repository.stored.isEmpty());
    }

    @Test
    void currencyIsNormalizedToUpperCase() {
        Transaction tx = service.execute(command(TransactionType.CREDIT, "100.00", " mxn ", null)).transaction();
        assertEquals("MXN", tx.currency());
    }

    @Test
    void sameIdempotencyKeyReturnsStoredTransactionAndCallsProviderOnce() {
        ExecutionResult first = service.execute(command(TransactionType.CREDIT, "100.00", "MXN", "key-1"));
        ExecutionResult second = service.execute(command(TransactionType.CREDIT, "100.00", "MXN", "key-1"));

        assertEquals(false, first.replayed());
        assertTrue(second.replayed());
        assertEquals(first.transaction().id(), second.transaction().id());
        assertEquals(1, provider.calls.size());
    }

    @Test
    void sameIdempotencyKeyWithDifferentPayloadIsAConflict() {
        service.execute(command(TransactionType.CREDIT, "100.00", "MXN", "key-1"));

        assertThrows(IdempotencyKeyConflictException.class,
                () -> service.execute(command(TransactionType.CREDIT, "200.00", "MXN", "key-1")));
    }

    @Test
    void losingAnIdempotencyRaceReturnsTheWinner() {
        Transaction winner = Transaction.pending(UUID.randomUUID(), "acc-123456", TransactionType.CREDIT,
                new BigDecimal("100.00"), "MXN", "test", "key-race", NOW);
        repository.simulateRaceWith(winner);

        ExecutionResult result = service.execute(command(TransactionType.CREDIT, "100.00", "MXN", "key-race"));

        assertTrue(result.replayed());
        assertEquals(winner.id(), result.transaction().id());
        assertEquals(0, provider.calls.size());
    }

    @Test
    void transactionIdIsForwardedToProviderForIdempotency() {
        Transaction tx = service.execute(command(TransactionType.CREDIT, "100.00", "MXN", null)).transaction();

        assertEquals(tx.id(), provider.calls.get(0).transactionId());
        assertNotNull(provider.calls.get(0).amount());
    }

    @Test
    void getByIdThrowsWhenMissing() {
        assertThrows(TransactionNotFoundException.class, () -> service.getById(UUID.randomUUID()));
    }

    // ---------- fakes ----------

    private static class StubProvider implements ProviderGateway {
        final List<ProviderRequest> calls = new ArrayList<>();
        private Function<ProviderRequest, ProviderResult> behaviour =
                r -> new ProviderResult.Approved("txn-default", new BigDecimal("1000.00"), NOW);

        void respondWith(Function<ProviderRequest, ProviderResult> behaviour) {
            this.behaviour = behaviour;
        }

        @Override
        public ProviderResult execute(ProviderRequest request) {
            calls.add(request);
            return behaviour.apply(request);
        }
    }

    private static class InMemoryRepository implements TransactionRepository {
        final Map<UUID, Transaction> stored = new LinkedHashMap<>();
        private Transaction raceWinner;

        /** First create() fails as if another request inserted the same key a moment earlier. */
        void simulateRaceWith(Transaction winner) {
            this.raceWinner = winner;
        }

        @Override
        public Transaction create(Transaction transaction) {
            if (raceWinner != null) {
                stored.put(raceWinner.id(), raceWinner);
                raceWinner = null;
                throw new DuplicateIdempotencyKeyException(transaction.idempotencyKey(), null);
            }
            stored.put(transaction.id(), transaction);
            return transaction;
        }

        @Override
        public Transaction update(Transaction transaction) {
            stored.put(transaction.id(), transaction);
            return transaction;
        }

        @Override
        public Optional<Transaction> findById(UUID id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public Optional<Transaction> findByIdempotencyKey(String key) {
            return stored.values().stream().filter(t -> key.equals(t.idempotencyKey())).findFirst();
        }

        @Override
        public PageResult<Transaction> search(TransactionQuery query) {
            return new PageResult<>(List.copyOf(stored.values()), query.page(), query.limit(), stored.size());
        }
    }
}

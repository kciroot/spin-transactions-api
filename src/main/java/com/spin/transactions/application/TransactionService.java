package com.spin.transactions.application;

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
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Use cases. Deliberately NOT @Transactional: no DB connection is held while waiting for the provider.
 * Flow: idempotency check -> business rules -> persist PENDING -> call provider -> persist final state.
 */
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository repository;
    private final ProviderGateway provider;
    private final TransactionRules rules;
    private final Clock clock;

    public TransactionService(TransactionRepository repository, ProviderGateway provider, TransactionRules rules,
                              Clock clock) {
        this.repository = repository;
        this.provider = provider;
        this.rules = rules;
        this.clock = clock;
    }

    public ExecutionResult execute(ExecuteTransactionCommand command) {
        String currency = command.currency().trim().toUpperCase(Locale.ROOT);

        Optional<Transaction> existing = findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            return replay(existing.get(), command, currency);
        }

        rules.validate(command.type(), command.amount(), currency);

        Transaction pending = Transaction.pending(UUID.randomUUID(), command.accountId(), command.type(),
                command.amount().setScale(2, RoundingMode.UNNECESSARY), currency, command.description(),
                command.idempotencyKey(), now());
        try {
            repository.create(pending);
        } catch (DuplicateIdempotencyKeyException e) {
            // Lost a race against a concurrent request with the same key: return the winner.
            Transaction winner = repository.findByIdempotencyKey(command.idempotencyKey()).orElseThrow(() -> e);
            return replay(winner, command, currency);
        }

        Transaction finished = callProvider(pending);
        return new ExecutionResult(repository.update(finished), false);
    }

    public Transaction getById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new TransactionNotFoundException(id));
    }

    public PageResult<Transaction> search(TransactionQuery query) {
        return repository.search(query);
    }

    private Transaction callProvider(Transaction pending) {
        ProviderRequest request = new ProviderRequest(pending.id(), pending.accountId(), pending.type(),
                pending.amount(), pending.currency());
        try {
            ProviderResult result = provider.execute(request);
            if (result instanceof ProviderResult.Approved approved) {
                return pending.executed(approved.transactionId(), approved.balance(), now());
            }
            ProviderResult.Rejected rejected = (ProviderResult.Rejected) result;
            return pending.rejected(rejected.code(), rejected.message(), now());
        } catch (ProviderUnavailableException e) {
            log.warn("Provider unavailable for transaction {}: {}", pending.id(), e.getMessage());
            return pending.failed("PROVIDER_UNAVAILABLE", e.getMessage(), now());
        } catch (RuntimeException e) {
            // Safety net: never leave the row PENDING because of an unexpected bug/adapter error. The outcome at the
            // provider is unknown, so it is recorded as FAILED (same meaning as an outage) without leaking details.
            log.error("Unexpected error calling provider for transaction {}", pending.id(), e);
            return pending.failed("PROVIDER_ERROR", "Unexpected error while calling the provider", now());
        }
    }

    private Optional<Transaction> findByIdempotencyKey(String key) {
        return key == null ? Optional.empty() : repository.findByIdempotencyKey(key);
    }

    private ExecutionResult replay(Transaction existing, ExecuteTransactionCommand command, String currency) {
        if (!existing.matches(command.accountId(), command.type(), command.amount(), currency)) {
            throw new IdempotencyKeyConflictException(command.idempotencyKey());
        }
        return new ExecutionResult(existing, true);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}

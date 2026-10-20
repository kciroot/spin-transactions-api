package com.spin.transactions.domain;

import java.util.Optional;
import java.util.UUID;

/** Output port: persistence. */
public interface TransactionRepository {

    /** Inserts a new transaction. @throws DuplicateIdempotencyKeyException if the idempotency key already exists. */
    Transaction create(Transaction transaction);

    Transaction update(Transaction transaction);

    Optional<Transaction> findById(UUID id);

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    PageResult<Transaction> search(TransactionQuery query);
}

package com.spin.transactions.application;

import com.spin.transactions.domain.Transaction;

/** replayed = true when an existing transaction was returned because of the Idempotency-Key. */
public record ExecutionResult(Transaction transaction, boolean replayed) {
}

package com.spin.transactions.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Immutable transaction. State changes return a new instance. */
public record Transaction(
        UUID id,
        String accountId,
        TransactionType type,
        BigDecimal amount,
        String currency,
        String description,
        TransactionStatus status,
        String providerTransactionId,
        BigDecimal balanceAfter,
        String errorCode,
        String errorMessage,
        String idempotencyKey,
        Instant createdAt,
        Instant updatedAt) {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 512;

    public static Transaction pending(UUID id, String accountId, TransactionType type, BigDecimal amount,
                                      String currency, String description, String idempotencyKey, Instant now) {
        return new Transaction(id, accountId, type, amount, currency, description, TransactionStatus.PENDING,
                null, null, null, null, idempotencyKey, now, now);
    }

    public Transaction executed(String providerTransactionId, BigDecimal balanceAfter, Instant now) {
        return new Transaction(id, accountId, type, amount, currency, description, TransactionStatus.EXECUTED,
                providerTransactionId, balanceAfter, null, null, idempotencyKey, createdAt, now);
    }

    public Transaction rejected(String code, String message, Instant now) {
        return withError(TransactionStatus.REJECTED, code, message, now);
    }

    public Transaction failed(String code, String message, Instant now) {
        return withError(TransactionStatus.FAILED, code, message, now);
    }

    /** True when this transaction comes from the same business request (idempotency check). */
    public boolean matches(String accountId, TransactionType type, BigDecimal amount, String currency) {
        return this.accountId.equals(accountId)
                && this.type == type
                && this.amount.compareTo(amount) == 0
                && this.currency.equals(currency);
    }

    private Transaction withError(TransactionStatus newStatus, String code, String message, Instant now) {
        return new Transaction(id, accountId, type, amount, currency, description, newStatus,
                null, null, code, truncate(message), idempotencyKey, createdAt, now);
    }

    private static String truncate(String message) {
        if (message == null || message.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}

package com.spin.transactions.domain;

/** Optional filters + 1-based pagination. */
public record TransactionQuery(String accountId, TransactionStatus status, TransactionType type, int page, int limit) {
}

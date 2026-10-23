package com.spin.transactions.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** transactionId is sent to the provider as Idempotency-Key so retries cannot double-execute. */
public record ProviderRequest(UUID transactionId, String accountId, TransactionType type, BigDecimal amount,
                              String currency) {
}

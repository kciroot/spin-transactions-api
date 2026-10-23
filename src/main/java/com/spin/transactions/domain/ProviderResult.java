package com.spin.transactions.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** Definitive answer from the provider. Anything non-definitive is a ProviderUnavailableException. */
public sealed interface ProviderResult {

    record Approved(String transactionId, BigDecimal balance, Instant executedAt) implements ProviderResult {
    }

    record Rejected(String code, String message) implements ProviderResult {
    }
}

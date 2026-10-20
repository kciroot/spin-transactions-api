package com.spin.transactions.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.spin.transactions.domain.Transaction;
import com.spin.transactions.domain.TransactionStatus;
import com.spin.transactions.domain.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionResponse(
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
        Instant createdAt) {

    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(t.id(), t.accountId(), t.type(), t.amount(), t.currency(), t.description(),
                t.status(), t.providerTransactionId(), t.balanceAfter(), t.errorCode(), t.errorMessage(),
                t.createdAt());
    }
}

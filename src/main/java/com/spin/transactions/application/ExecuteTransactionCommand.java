package com.spin.transactions.application;

import com.spin.transactions.domain.TransactionType;
import java.math.BigDecimal;

public record ExecuteTransactionCommand(String accountId, TransactionType type, BigDecimal amount, String currency,
                                        String description, String idempotencyKey) {
}

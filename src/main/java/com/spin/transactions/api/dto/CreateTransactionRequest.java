package com.spin.transactions.api.dto;

import com.spin.transactions.domain.TransactionType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Only syntactic validation lives here; business rules live in the domain (TransactionRules). */
public record CreateTransactionRequest(
        @NotBlank @Size(max = 64) String accountId,
        @NotNull TransactionType type,
        @NotNull @Digits(integer = 15, fraction = 2, message = "must have at most 2 decimal places") BigDecimal amount,
        @NotBlank @Size(max = 3) String currency,
        @Size(max = 255) String description) {
}

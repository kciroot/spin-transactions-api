package com.spin.transactions.domain;

import java.math.BigDecimal;

/** Business rules evaluated BEFORE calling the provider. First violation wins. */
public class TransactionRules {

    public static final BigDecimal MIN_AMOUNT_EXCLUSIVE = new BigDecimal("1.00");
    public static final BigDecimal DEBIT_MAX_AMOUNT = new BigDecimal("10000.00");
    public static final String SUPPORTED_CURRENCY = "MXN";

    public void validate(TransactionType type, BigDecimal amount, String currency) {
        if (!SUPPORTED_CURRENCY.equals(currency)) {
            throw new BusinessRuleViolationException("UNSUPPORTED_CURRENCY",
                    "Only " + SUPPORTED_CURRENCY + " is supported, got '" + currency + "'");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new BusinessRuleViolationException("INVALID_AMOUNT_PRECISION",
                    "Amount cannot have more than 2 decimal places");
        }
        if (amount.compareTo(MIN_AMOUNT_EXCLUSIVE) <= 0) {
            throw new BusinessRuleViolationException("AMOUNT_TOO_LOW",
                    "Amount must be greater than " + MIN_AMOUNT_EXCLUSIVE);
        }
        if (type == TransactionType.DEBIT && amount.compareTo(DEBIT_MAX_AMOUNT) > 0) {
            throw new BusinessRuleViolationException("DEBIT_LIMIT_EXCEEDED",
                    "DEBIT amount cannot exceed " + DEBIT_MAX_AMOUNT + " per transaction");
        }
    }
}

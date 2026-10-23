package com.spin.transactions.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TransactionRulesTest {

    private final TransactionRules rules = new TransactionRules();

    private String codeOf(TransactionType type, String amount, String currency) {
        return assertThrows(BusinessRuleViolationException.class,
                () -> rules.validate(type, new BigDecimal(amount), currency)).code();
    }

    @Test
    void rejectsAmountEqualToMinimum() {
        assertEquals("AMOUNT_TOO_LOW", codeOf(TransactionType.CREDIT, "1.00", "MXN"));
    }

    @Test
    void rejectsAmountBelowMinimumAndNegative() {
        assertEquals("AMOUNT_TOO_LOW", codeOf(TransactionType.CREDIT, "0.50", "MXN"));
        assertEquals("AMOUNT_TOO_LOW", codeOf(TransactionType.DEBIT, "-5.00", "MXN"));
    }

    @Test
    void acceptsAmountJustAboveMinimum() {
        assertDoesNotThrow(() -> rules.validate(TransactionType.CREDIT, new BigDecimal("1.01"), "MXN"));
    }

    @Test
    void debitAboveLimitIsRejected() {
        assertEquals("DEBIT_LIMIT_EXCEEDED", codeOf(TransactionType.DEBIT, "10000.01", "MXN"));
    }

    @Test
    void debitAtLimitIsAccepted() {
        assertDoesNotThrow(() -> rules.validate(TransactionType.DEBIT, new BigDecimal("10000.00"), "MXN"));
    }

    @Test
    void creditHasNoUpperLimit() {
        assertDoesNotThrow(() -> rules.validate(TransactionType.CREDIT, new BigDecimal("99999999.99"), "MXN"));
    }

    @Test
    void onlyMxnIsSupported() {
        assertEquals("UNSUPPORTED_CURRENCY", codeOf(TransactionType.CREDIT, "100.00", "USD"));
    }

    @Test
    void rejectsMoreThanTwoDecimals() {
        assertEquals("INVALID_AMOUNT_PRECISION", codeOf(TransactionType.CREDIT, "10.005", "MXN"));
    }
}

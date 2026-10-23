package com.spin.transactions.domain;

public class BusinessRuleViolationException extends RuntimeException {
    private final String code;

    public BusinessRuleViolationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}

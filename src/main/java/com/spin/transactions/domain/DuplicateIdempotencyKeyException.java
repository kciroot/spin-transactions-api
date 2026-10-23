package com.spin.transactions.domain;

public class DuplicateIdempotencyKeyException extends RuntimeException {
    public DuplicateIdempotencyKeyException(String key, Throwable cause) {
        super("Idempotency key already used: " + key, cause);
    }
}

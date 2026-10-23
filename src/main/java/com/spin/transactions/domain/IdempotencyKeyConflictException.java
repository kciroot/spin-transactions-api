package com.spin.transactions.domain;

/** Same Idempotency-Key reused with a different payload. */
public class IdempotencyKeyConflictException extends RuntimeException {
    public IdempotencyKeyConflictException(String key) {
        super("Idempotency key '" + key + "' was already used with a different request");
    }
}

package com.spin.transactions.domain;

/**
 * PENDING  - persisted, provider has not answered yet (or the process died mid-flight).
 * EXECUTED - provider approved.
 * REJECTED - provider answered with a business rejection (e.g. INSUFFICIENT_FUNDS).
 * FAILED   - provider unreachable / timed out / circuit open. Outcome at the provider is unknown.
 */
public enum TransactionStatus {
    PENDING,
    EXECUTED,
    REJECTED,
    FAILED
}

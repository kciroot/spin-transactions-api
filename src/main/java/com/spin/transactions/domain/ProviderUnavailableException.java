package com.spin.transactions.domain;

/** Provider could not give a definitive answer: network error, timeout, 5xx or circuit open. */
public class ProviderUnavailableException extends RuntimeException {
    public ProviderUnavailableException(String message) {
        super(message);
    }

    public ProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

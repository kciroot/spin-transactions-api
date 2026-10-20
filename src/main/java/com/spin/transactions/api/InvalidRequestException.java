package com.spin.transactions.api;

class InvalidRequestException extends RuntimeException {
    InvalidRequestException(String message) {
        super(message);
    }
}

package com.highnote.processor.parser;

/** An input line can't be parsed into a {@code Transaction}. */
public final class InvalidTransactionException extends RuntimeException {

    public InvalidTransactionException(String message) {
        super(message);
    }
}

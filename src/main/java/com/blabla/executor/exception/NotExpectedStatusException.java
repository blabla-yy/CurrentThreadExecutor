package com.blabla.executor.exception;

public class NotExpectedStatusException extends RuntimeException {
    public NotExpectedStatusException(String message) {
        super(message);
    }
}

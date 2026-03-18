package com.omninote_ai.server.outbox.exception;

public class EnqueueOutboxEventException extends RuntimeException {
    public EnqueueOutboxEventException(String message) {
        super(message);
    }

    public EnqueueOutboxEventException(String message, Throwable cause) {
        super(message, cause);
    }
    
}

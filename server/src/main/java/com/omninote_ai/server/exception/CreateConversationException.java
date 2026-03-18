package com.omninote_ai.server.exception;

public class CreateConversationException extends RuntimeException {
    public CreateConversationException(String message) {
        super(message);
    }

    public CreateConversationException(String message, Throwable cause) {
        super(message, cause);
    }
    
}

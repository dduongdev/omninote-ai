package com.omninote_ai.server.exception;

public class SendMessage extends RuntimeException {
    public SendMessage(String message) {
        super(message);
    }

    public SendMessage(String message, Throwable cause) {
        super(message, cause);
    }
    
}

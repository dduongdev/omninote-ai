package com.omninote_ai.server.exception;

public class SelectedFileException extends RuntimeException {
    public SelectedFileException(String message) {
        super(message);
    }

    public SelectedFileException(String message, Throwable cause) {
        super(message, cause);
    }
    
}

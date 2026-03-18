package com.omninote_ai.server.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.omninote_ai.server.exception.CreateConversationException;
import com.omninote_ai.server.exception.SelectedFileException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(CreateConversationException.class)
    public ResponseEntity<ProblemDetail> handleCreateConversationException(CreateConversationException e) {
        ProblemDetail errorResponse = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(SelectedFileException.class)
    public ResponseEntity<ProblemDetail> handleSelectedFileException(SelectedFileException e) {
        ProblemDetail errorResponse = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
}

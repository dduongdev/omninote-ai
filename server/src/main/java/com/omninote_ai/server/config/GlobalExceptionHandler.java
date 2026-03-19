package com.omninote_ai.server.config;

import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.omninote_ai.server.exception.CreateConversationException;
import com.omninote_ai.server.exception.SelectedFileException;
import com.omninote_ai.server.exception.UploadFileException;
import jakarta.persistence.EntityNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    // Giữ nguyên các handler đặc thù
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

    // Dung hợp: Thêm Title và Timestamp từ develop cho EntityNotFound
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleEntityNotFound(EntityNotFoundException e) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problemDetail.setTitle("Resource Not Found");
        problemDetail.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problemDetail);
    }

    // Dung hợp: Thêm Title cho AccessDenied
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException e) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
        problemDetail.setTitle("Access Denied");
        problemDetail.setProperty("timestamp", Instant.now()); // Thêm cho đồng bộ
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problemDetail);
    }

    // Dung hợp: Ưu tiên mã lỗi CONFLICT (409) từ feat nhưng lấy Title từ develop
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> handleIllegalState(IllegalStateException e) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problemDetail.setTitle("Invalid Resource State");
        problemDetail.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problemDetail);
    }

    // Giữ lại UploadFileException từ nhánh feat
    @ExceptionHandler(UploadFileException.class)
    public ResponseEntity<ProblemDetail> handleUploadFile(UploadFileException e) {
        ProblemDetail error = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        error.setTitle("Upload Failed");
        error.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
}
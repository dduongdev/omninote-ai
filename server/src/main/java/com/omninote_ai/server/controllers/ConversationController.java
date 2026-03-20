package com.omninote_ai.server.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import com.omninote_ai.server.dto.ConversationCreateRequest;
import com.omninote_ai.server.dto.ConversationCreateResponse;
import com.omninote_ai.server.dto.ConversationDeleteResponse;
import com.omninote_ai.server.dto.ConversationHistoryResponse;
import com.omninote_ai.server.dto.ConversationSummary;
import com.omninote_ai.server.dto.DocumentContentResponse;
import com.omninote_ai.server.dto.DocumentDeleteRequest;
import com.omninote_ai.server.dto.DocumentSummary;
import com.omninote_ai.server.dto.DocumentUploadRequest;
import com.omninote_ai.server.dto.DocumentUploadResponse;
import com.omninote_ai.server.repositories.ConversationRepository;
import com.omninote_ai.server.repositories.UserRepository;
import com.omninote_ai.server.services.ConversationService;
import com.omninote_ai.server.services.DocumentService;
import com.omninote_ai.server.utility.JwtUtil;
import com.omninote_ai.server.entity.User;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final DocumentService documentService;

    @GetMapping("/api/v1/conversations")
    public ResponseEntity<List<ConversationSummary>> getConversations() {
        List<ConversationSummary> conversations = conversationService.getConversations();
        return ResponseEntity.ok(conversations);
    }

    @GetMapping("/api/v1/conversations/{id}/history")
    public ResponseEntity<ConversationHistoryResponse> getConversationHistory(@PathVariable("id") Long id) {
        ConversationHistoryResponse response = conversationService.getConversationHistory(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/v1/conversations/create")
    public ResponseEntity<?> createConversation(@Valid ConversationCreateRequest request) {
        ConversationCreateResponse response = conversationService.create(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/v1/conversations/{id}/exists")
    public ResponseEntity<java.util.Map<String, Boolean>> existsConversation(
            @PathVariable("id") Long id,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(java.util.Map.of("exists", false));
        }

        String token = authorization.substring(7);
        String userName = jwtUtil.extractUserName(token);
        java.util.Optional<User> optionalUser = userRepository.findByUserName(userName);
        if (optionalUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(java.util.Map.of("exists", false));
        }

        boolean exists = conversationRepository.existsByIdAndUserId(id, optionalUser.get().getId());
        return ResponseEntity.ok(java.util.Map.of("exists", exists));
    }

    @PostMapping("/api/v1/conversations/{conversationId}/documents/upload")
    public ResponseEntity<DocumentUploadResponse> uploadDocuments(
            @PathVariable("conversationId") Long conversationId,
            @Valid DocumentUploadRequest request) {
        DocumentUploadResponse response = documentService.uploadDocuments(conversationId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/v1/conversations/{conversationId}/documents/{documentId}/content")
    public ResponseEntity<DocumentContentResponse> getDocumentContent(
            @PathVariable("conversationId") Long conversationId,
            @PathVariable("documentId") Long documentId) {
        DocumentContentResponse response = documentService.getDocumentContent(conversationId, documentId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/v1/conversations/{conversationId}/documents/delete")
    public ResponseEntity<?> deleteDocument(
            @PathVariable("conversationId") Long conversationId, DocumentDeleteRequest request) {
        DocumentSummary response = documentService.deleteDocument(conversationId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/api/v1/conversations/{id}")
    public ResponseEntity<ConversationDeleteResponse> deleteConversation(@PathVariable("id") Long id) {
        ConversationDeleteResponse response = conversationService.deleteConversation(id);
        if ("DELETED".equals(response.getStatus())) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.accepted().body(response);
    }
}

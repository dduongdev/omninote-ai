package com.omninote_ai.server.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.omninote_ai.server.dto.ConversationCreateRequest;
import com.omninote_ai.server.dto.ConversationCreateResponse;
import com.omninote_ai.server.services.ConversationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping("/api/v1/conversations/create")
    public ResponseEntity<?> createConversation(@Valid ConversationCreateRequest request) {
        ConversationCreateResponse response = conversationService.create(request);
        return ResponseEntity.ok(response);
    }
}

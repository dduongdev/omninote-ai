package com.omninote_ai.server.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;

import com.omninote_ai.server.dto.ConversationCreateRequest;

import jakarta.validation.Valid;

@Controller
public class ConversationController {

    @PostMapping("/api/v1/conversations/create")
    public ResponseEntity<?> createConversation(@Valid ConversationCreateRequest request) {
        return ResponseEntity.ok("Conversation created successfully");        
    }
}

package com.omninote_ai.server.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.omninote_ai.server.dto.MessageCreateRequest;
import com.omninote_ai.server.dto.MessageResponse;
import com.omninote_ai.server.services.ChatService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/sendmessages")
    public ResponseEntity<MessageResponse> sendMessage(@Valid @RequestBody MessageCreateRequest request) {
        MessageResponse response = chatService.sendMessage(request);
        return ResponseEntity.ok(response);
    }
}

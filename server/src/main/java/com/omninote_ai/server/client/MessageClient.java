package com.omninote_ai.server.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.omninote_ai.server.dto.MessageCreateRequest;
import com.omninote_ai.server.dto.MessageResponse;

@FeignClient(
    name = "ai-service", 
    path = "/api/v1/ai",
    fallback = MessageClientFallback.class
)
public interface MessageClient {

    @PostMapping("/chat")
    MessageResponse generateAIResponse(@RequestBody MessageCreateRequest request);
}
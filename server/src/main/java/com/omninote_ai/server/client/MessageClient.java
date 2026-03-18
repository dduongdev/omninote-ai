package com.omninote_ai.server.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.omninote_ai.server.dto.MessageCreateRequest;
import com.omninote_ai.server.dto.MessageResponse;

@FeignClient(name = "ai-service",
   // configuration = FeignConfig.class,
    fallback=MessageClientFallback.class,
    url="http://localhost:8533"
)
public interface MessageClient {
    @PostMapping("/api/v1/ai/chat")
    MessageResponse generateAIResponse(@RequestBody MessageCreateRequest request);
}

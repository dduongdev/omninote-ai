package com.omninote_ai.server.client;

import org.springframework.stereotype.Component;

import com.omninote_ai.server.dto.MessageCreateRequest;
import com.omninote_ai.server.dto.MessageResponse;
import java.util.Collections;

@Component
public class MessageClientFallback implements MessageClient {

    @Override
    public MessageResponse generateAIResponse(MessageCreateRequest request) {
        return MessageResponse.builder()
                .contentAnswer("AI Service hiện không khả dụng hoặc đang khởi tạo. Vui lòng thử lại sau.")
                .citationsResponses(Collections.emptyList())
                .build();
    }
}


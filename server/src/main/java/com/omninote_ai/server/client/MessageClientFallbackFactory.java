package com.omninote_ai.server.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import java.util.Collections;
import com.omninote_ai.server.dto.MessageCreateRequest;
import com.omninote_ai.server.dto.MessageResponse;

@Component
public class MessageClientFallbackFactory implements FallbackFactory<MessageClient> {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageClientFallbackFactory.class);

    @Override
    public MessageClient create(Throwable cause) {
        return new MessageClient() {
            @Override
            public MessageResponse generateAIResponse(MessageCreateRequest request) {
                logger.error("Error occurred while calling ai-service generateAIResponse: ", cause);
                return MessageResponse.builder()
                        .contentAnswer("AI Service hiện không khả dụng hoặc bị lỗi (" + cause.getMessage() + "). Vui lòng thử lại sau.")
                        .citationsResponses(Collections.emptyList())
                        .build();
            }
        };
    }
}

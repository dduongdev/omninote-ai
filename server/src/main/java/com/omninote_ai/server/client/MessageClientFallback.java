package com.omninote_ai.server.client;

import org.springframework.stereotype.Component;

import com.omninote_ai.server.dto.CitationResponse;
import com.omninote_ai.server.dto.MessageCreateRequest;
import com.omninote_ai.server.dto.MessageResponse;
import java.util.Collections;
import java.util.List;

@Component
public class MessageClientFallback implements MessageClient {

    @Override
    public MessageResponse generateAIResponse(MessageCreateRequest request) {

        List<CitationResponse> mockCitations = Collections.emptyList();
        
        // If there are documents in the request, create a mock citation for the first one
        if (request.getDocumentIds() != null && !request.getDocumentIds().isEmpty()) {
            mockCitations = List.of(
                CitationResponse.builder()
                    .documentId(request.getDocumentIds().get(0))
                    .startIndex(0L)
                    .endIndex(10L)
                    .fileName("Mock Document")
                    .build()
            );
        }

        return MessageResponse.builder()
                .contentAnswer("Mock AI response: " + request.getContentQuery())
                .citationsResponses(mockCitations)
                .build();
    }
}
package com.omninote_ai.server.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationHistoryResponse {
    private Long conversationId;
    private String title;
    private String status;
    private List<DocumentSummary> documents;
    private List<MessageResponse> messages;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

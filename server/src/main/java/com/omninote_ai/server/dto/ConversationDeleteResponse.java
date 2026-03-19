package com.omninote_ai.server.dto;

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
public class ConversationDeleteResponse {
    private Long conversationId;
    private String status;
    private String message;
    private int totalDocuments;
    private int asyncPending;
    private int deletedImmediately;
}

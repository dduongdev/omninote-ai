package com.omninote_ai.server.dto;

import java.time.LocalDateTime;

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
public class ConversationSummary {
    private Long id;
    private String title;
    private String status;
    private int documentCount;
    private int messageCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

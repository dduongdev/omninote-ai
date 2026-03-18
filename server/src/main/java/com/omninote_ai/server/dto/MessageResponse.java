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
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MessageResponse {
    private Long id;
    private Long conversationId;
    private String contentQuery;
    private String contentAnswer;
    private List<DocumentSummary> selectedDocuments;
    private List<CitationResponse> citationsResponses;
    private LocalDateTime createdAt;
}

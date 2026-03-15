package com.omninote_ai.server.dto;

import java.time.LocalDateTime;

import com.omninote_ai.server.entity.DocumentStatus;

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
public class DocumentSummary {
    private Long id;
    private String fileName;
    private DocumentStatus status;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

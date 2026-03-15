package com.omninote_ai.server.mapper;

import com.omninote_ai.server.dto.DocumentSummary;
import com.omninote_ai.server.entity.Document;

public class DocumentMapper {
    public static DocumentSummary toSummary(Document document) {
        return DocumentSummary.builder()
                .id(document.getId())
                .fileName(document.getFileName())
                .status(document.getStatus())
                .content(document.getContent())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }
}

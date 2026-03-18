package com.omninote_ai.server.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentUploadResponse {
    @Builder.Default
    private List<DocumentSummary> uploadedDocuments = new ArrayList<>();
}

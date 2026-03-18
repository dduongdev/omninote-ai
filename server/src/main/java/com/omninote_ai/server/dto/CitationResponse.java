package com.omninote_ai.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CitationResponse {
    private Long id;
    private Long documentId;
    private String fileName; // Thêm vào để FE hiển thị tên nguồn nhanh
    private Long startIndex;
    private Long endIndex;
}
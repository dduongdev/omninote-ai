package com.omninote_ai.server.services;

import com.omninote_ai.server.dto.DocumentDeleteRequest;
import com.omninote_ai.server.dto.DocumentSummary;
import com.omninote_ai.server.dto.DocumentUploadRequest;
import com.omninote_ai.server.dto.DocumentUploadResponse;

public interface DocumentService {
    DocumentUploadResponse uploadDocuments(Long conversationId, DocumentUploadRequest request);
    void deleteDocuments(Long conversationId, DocumentDeleteRequest request);
    void handleMilvusDeleteSuccess(Long docId);
    void handleMilvusDeleteFailed(Long docId);
    void handleIngestSuccess(Long docId, String extractedObjectName);
    void handleIngestFailed(Long docId);
}

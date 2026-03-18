package com.omninote_ai.server.services;

import com.omninote_ai.server.dto.DocumentUploadRequest;
import com.omninote_ai.server.dto.DocumentUploadResponse;

public interface DocumentService {
    DocumentUploadResponse uploadDocuments(Long conversationId, DocumentUploadRequest request);
}

package com.omninote_ai.server.dto;

import com.omninote_ai.server.entity.DocumentStatus;

public record DocumentStatusUpdate(
    Long documentId,
    Long conversationId,
    DocumentStatus status
) {}

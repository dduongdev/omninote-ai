package com.omninote_ai.server.services;

import com.omninote_ai.server.dto.DocumentStatusUpdate;
import com.omninote_ai.server.entity.Document;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentSyncService {

    private final SimpMessagingTemplate messagingTemplate;

    public void syncDocumentStatus(Document document) {
        if (document == null || document.getConversation() == null) {
            return;
        }
        
        Long conversationId = document.getConversation().getId();
        DocumentStatusUpdate updatePayload = new DocumentStatusUpdate(
                document.getId(),
                conversationId,
                document.getStatus()
        );

        String topic = String.format("/topic/conversations/%s/documents", conversationId);
        log.info("Broadcasting document status: {} to topic: {}", document.getStatus(), topic);
        messagingTemplate.convertAndSend(topic, updatePayload);
    }
}

package com.omninote_ai.server.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omninote_ai.server.entity.Document;
import com.omninote_ai.server.event.DocumentDeletedEvent;
import com.omninote_ai.server.event.DocumentDeletingEvent;
import com.omninote_ai.server.event.DocumentUploadedEvent;
import com.omninote_ai.server.outbox.entity.OutboxEvent;
import com.omninote_ai.server.outbox.exception.EnqueueOutboxEventException;
import com.omninote_ai.server.outbox.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OutboxEventService {

    private final ObjectMapper objectMapper;
    private final OutboxEventRepository outboxEventRepository;

    public OutboxEvent createDocumentUploadedEvent(Document uploadedDocument) {
        if (uploadedDocument == null) {
            throw new EnqueueOutboxEventException("Uploaded document cannot be null");
        }

        if (uploadedDocument.getId() == null || uploadedDocument.getConversation() == null) {
            throw new EnqueueOutboxEventException("Document must be uploaded (have an ID) and be associated with a conversation");
        }

        OutboxEvent event = new OutboxEvent();
        event.setAggregateId(uploadedDocument.getId());
        event.setAggregateType(Document.class.getSimpleName());
        event.setEventType("document.uploaded");
        DocumentUploadedEvent payload = DocumentUploadedEvent.builder()
            .documentId(uploadedDocument.getId())
            .conversationId(uploadedDocument.getConversation().getId())
            .objectName(uploadedDocument.getObjectName())
            .build();
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            event.setPayload(payloadJson);
        } catch (Exception e) {
            throw new EnqueueOutboxEventException("Failed to serialize document for outbox event", e);
        }
        return event;
    }

    public void enqueueUploadedDocuments(List<Document> uploadedDocuments) {
        List<OutboxEvent> events = uploadedDocuments.stream()
            .map(this::createDocumentUploadedEvent)
            .toList();
            
        outboxEventRepository.saveAll(events);
    }

    public OutboxEvent createDocumentDeletedEvent(Document deletedDocument) {
        if (deletedDocument == null) {
            throw new EnqueueOutboxEventException("Deleted document cannot be null");
        }

        if (deletedDocument.getId() == null || deletedDocument.getConversation() == null) {
            throw new EnqueueOutboxEventException("Document must have an ID and be associated with a conversation");
        }

        OutboxEvent event = new OutboxEvent();
        event.setAggregateId(deletedDocument.getId());
        event.setAggregateType(Document.class.getSimpleName());
        event.setEventType("document.deleted");

        DocumentDeletedEvent payload = DocumentDeletedEvent.builder()
            .documentId(deletedDocument.getId())
            .conversationId(deletedDocument.getConversation().getId())
            .build();
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            event.setPayload(payloadJson);
        } catch (Exception e) {
            throw new EnqueueOutboxEventException("Failed to serialize document for outbox event", e);
        }
        return event;
    }

    public void enqueueDeletedDocument(Document deletedDocument) {
        OutboxEvent event = createDocumentDeletedEvent(deletedDocument);
        outboxEventRepository.save(event);
    }

    public OutboxEvent createDocumentDeletingEvent(Document document) {
        if (document == null) {
            throw new EnqueueOutboxEventException("Document cannot be null");
        }

        if (document.getId() == null || document.getConversation() == null) {
            throw new EnqueueOutboxEventException("Document must have an ID and be associated with a conversation");
        }

        OutboxEvent event = new OutboxEvent();
        event.setAggregateId(document.getId());
        event.setAggregateType(Document.class.getSimpleName());
        event.setEventType("document.deleting");

        DocumentDeletingEvent payload = DocumentDeletingEvent.builder()
            .documentId(document.getId())
            .conversationId(document.getConversation().getId())
            .build();
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            event.setPayload(payloadJson);
        } catch (Exception e) {
            throw new EnqueueOutboxEventException("Failed to serialize document for outbox event", e);
        }
        return event;
    }

    public void enqueueDeletingDocument(Document document) {
        OutboxEvent event = createDocumentDeletingEvent(document);
        outboxEventRepository.save(event);
    }

    public void enqueueMilvusRevertSoftDelete(Document document) {
        OutboxEvent event = new OutboxEvent();
        event.setAggregateId(document.getId());
        event.setAggregateType(Document.class.getSimpleName());
        event.setEventType("MILVUS_REVERT_SOFT_DELETE");

        com.omninote_ai.server.event.DocumentDeletedEvent payload = com.omninote_ai.server.event.DocumentDeletedEvent.builder()
            .documentId(document.getId())
            .conversationId(document.getConversation().getId())
            .build();
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            event.setPayload(payloadJson);
        } catch (Exception e) {
            throw new EnqueueOutboxEventException("Failed to serialize document for outbox event", e);
        }
        outboxEventRepository.save(event);
    }

    public void enqueueFinalPurgeCommand(Document document) {
        OutboxEvent event = new OutboxEvent();
        event.setAggregateId(document.getId());
        event.setAggregateType(Document.class.getSimpleName());
        event.setEventType("FINAL_PURGE_COMMAND");

        com.omninote_ai.server.event.DocumentDeletedEvent payload = com.omninote_ai.server.event.DocumentDeletedEvent.builder()
            .documentId(document.getId())
            .conversationId(document.getConversation().getId())
            .build();
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            event.setPayload(payloadJson);
        } catch (Exception e) {
            throw new EnqueueOutboxEventException("Failed to serialize document for outbox event", e);
        }
        outboxEventRepository.save(event);
    }
}

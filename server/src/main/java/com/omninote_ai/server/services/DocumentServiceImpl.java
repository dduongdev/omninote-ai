package com.omninote_ai.server.services;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.omninote_ai.server.dto.DocumentSummary;
import com.omninote_ai.server.dto.DocumentDeleteRequest;
import com.omninote_ai.server.dto.DocumentUploadRequest;
import com.omninote_ai.server.dto.DocumentUploadResponse;
import com.omninote_ai.server.entity.Conversation;
import com.omninote_ai.server.entity.Document;
import com.omninote_ai.server.entity.DocumentStatus;
import com.omninote_ai.server.exception.UploadFileException;
import com.omninote_ai.server.mapper.DocumentMapper;
import com.omninote_ai.server.repositories.ConversationRepository;
import com.omninote_ai.server.repositories.DocumentRepository;
import com.omninote_ai.server.utility.JwtUtil;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {
    private final ConversationService conversationService;
    private final JwtUtil jwtUtil;
    private final ConversationRepository conversationRepository;
    private final DocumentRepository documentRepository;
    private final OutboxEventService outboxEventService;
    private final MinioService minioService;
    private final DocumentSyncService documentSyncService;

    @Override
    @Transactional
    public DocumentUploadResponse uploadDocuments(Long conversationId, DocumentUploadRequest request) {
        try {
            Long userId = jwtUtil.getCurrentUserId();
            Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new EntityNotFoundException("Conversation not found"));
            if (!conversation.getUser().getId().equals(userId)) {
                throw new AccessDeniedException("User does not have permission to upload documents to this conversation");
            }

            if (conversation.getDocuments().size() + request.getFiles().size() > 5) {
                throw new UploadFileException("Cannot upload more than 5 documents to a conversation");
            }

            List<Document> uploadedDocuments = conversationService.uploadAndAttachDocuments(conversation, request.getFiles());
            return new DocumentUploadResponse(uploadedDocuments.stream()
                .map(DocumentMapper::toSummary)
                .toList());
        } catch (Exception e) {
            throw new UploadFileException("Failed to upload documents", e);
        }
    }

    @Override
    @Transactional
    public DocumentSummary deleteDocument(Long conversationId, DocumentDeleteRequest request) {
        Long userId = jwtUtil.getCurrentUserId();
        Conversation conversation = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new EntityNotFoundException("Conversation not found"));
            
        if (!conversation.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("User does not have permission to modify documents in this conversation");
        }

        Document document = documentRepository.findById(request.getDocumentId())
            .orElseThrow(() -> new EntityNotFoundException("Document not found"));
            
        if (!document.getConversation().getId().equals(conversationId)) {
            throw new AccessDeniedException("Document does not belong to the specified conversation");
        }

        if (document.getStatus() != DocumentStatus.READY) {
            throw new IllegalStateException("Document is not in READY state");
        }

        document.setStatus(DocumentStatus.DELETING);
        documentRepository.save(document);
        documentSyncService.syncDocumentStatus(document);

        outboxEventService.enqueueDeletingDocument(document);

        return DocumentMapper.toSummary(document);
    }

    @Override
    @Transactional
    public void handleMilvusSoftDeleteFailed(Long docId) {
        Document document = documentRepository.findById(docId)
            .orElseThrow(() -> new EntityNotFoundException("Document not found"));
        document.setStatus(DocumentStatus.READY);
        documentRepository.save(document);
        documentSyncService.syncDocumentStatus(document);
    }

    @Override
    @Transactional
    public void handleMilvusSoftDeleteSuccess(Long docId) {
        Document document = documentRepository.findById(docId)
            .orElseThrow(() -> new EntityNotFoundException("Document not found"));
        
        try {
            documentRepository.delete(document);
            minioService.deleteFile(document.getObjectName());
            if (document.getExtractedObjectName() != null) {
                minioService.deleteFile(document.getExtractedObjectName());
            }
            document.setStatus(DocumentStatus.DELETED);
            documentSyncService.syncDocumentStatus(document);
            
            outboxEventService.enqueueFinalPurgeCommand(document);
        } catch (Exception e) {
            document.setStatus(DocumentStatus.READY);
            documentRepository.save(document);
            documentSyncService.syncDocumentStatus(document);
            outboxEventService.enqueueMilvusRevertSoftDelete(document);
        }
    }
}

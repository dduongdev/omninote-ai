package com.omninote_ai.server.services;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.omninote_ai.server.dto.DocumentContentResponse;
import com.omninote_ai.server.dto.DocumentDeleteRequest;
import com.omninote_ai.server.dto.DocumentSummary;
import com.omninote_ai.server.dto.DocumentUploadRequest;
import com.omninote_ai.server.dto.DocumentUploadResponse;
import com.omninote_ai.server.entity.Conversation;
import com.omninote_ai.server.entity.ConversationStatus;
import com.omninote_ai.server.entity.Document;
import com.omninote_ai.server.entity.DocumentStatus;
import com.omninote_ai.server.exception.UploadFileException;
import com.omninote_ai.server.mapper.DocumentMapper;
import com.omninote_ai.server.repositories.ConversationRepository;
import com.omninote_ai.server.repositories.DocumentRepository;
import com.omninote_ai.server.repositories.MessageRepository;
import com.omninote_ai.server.utility.JwtUtil;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentServiceImpl implements DocumentService {
    private final ConversationService conversationService;
    private final JwtUtil jwtUtil;
    private final ConversationRepository conversationRepository;
    private final DocumentRepository documentRepository;
    private final MessageRepository messageRepository;
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

            if (conversation.getStatus() == ConversationStatus.DELETING) {
                throw new IllegalStateException("Cannot upload to a conversation that is being deleted");
            }

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

        if (conversation.getStatus() == ConversationStatus.DELETING) {
            throw new IllegalStateException("Cannot modify a conversation that is being deleted");
        }

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
    public DocumentContentResponse getDocumentContent(Long conversationId, Long documentId) {
        Long userId = jwtUtil.getCurrentUserId();

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Document not found"));

        if (!document.getConversation().getId().equals(conversationId)) {
            throw new AccessDeniedException("Document does not belong to the specified conversation");
        }

        if (!document.getConversation().getUser().getId().equals(userId)) {
            throw new AccessDeniedException("User does not own this conversation");
        }

        String objectName = document.getExtractedObjectName() != null
                ? document.getExtractedObjectName()
                : document.getObjectName();

        String content = minioService.getFileContent(objectName);

        return DocumentContentResponse.builder()
                .documentId(document.getId())
                .fileName(document.getFileName())
                .content(content)
                .build();
    }

    private static final int MAX_DELETE_RETRIES = 3;

    @Override
    @Transactional
    public void handleMilvusDeleteFailed(Long docId) {
        Document document = documentRepository.findById(docId)
            .orElseThrow(() -> new EntityNotFoundException("Document not found"));

        int retryCount = document.getDeleteRetryCount() + 1;
        document.setDeleteRetryCount(retryCount);

        if (retryCount >= MAX_DELETE_RETRIES) {
            document.setStatus(DocumentStatus.DELETE_FAILED);
            documentRepository.save(document);
            log.error("Milvus delete failed for doc {} after {} retries. Marked DELETE_FAILED.",
                    docId, retryCount);

            Conversation conversation = document.getConversation();
            if (conversation.getStatus() == ConversationStatus.DELETING) {
                tryFinalizeConversationDelete(conversation);
            }
        } else {
            documentRepository.save(document);
            outboxEventService.enqueueDeletingDocument(document);
            log.warn("Milvus delete failed for doc {}, retry {}/{}",
                    docId, retryCount, MAX_DELETE_RETRIES);
        }
    }

    @Override
    @Transactional
    public void handleMilvusDeleteSuccess(Long docId) {
        Document document = documentRepository.findById(docId)
            .orElseThrow(() -> new EntityNotFoundException("Document not found"));
        Conversation conversation = document.getConversation();
        boolean isConversationDeleting = conversation.getStatus() == ConversationStatus.DELETING;

        try {
            minioService.deleteFile(document.getObjectName());
            if (document.getExtractedObjectName() != null) {
                minioService.deleteFile(document.getExtractedObjectName());
            }
            
            // Delete constraints from messages table prior to deleting the actual document
            messageRepository.deleteCitationsByDocumentId(docId);
            messageRepository.deleteMessageDocumentsByDocumentId(docId);
            
            documentRepository.delete(document);

            if (isConversationDeleting) {
                tryFinalizeConversationDelete(conversation);
            }
        } catch (Exception e) {
            int retryCount = document.getDeleteRetryCount() + 1;
            document.setDeleteRetryCount(retryCount);

            if (retryCount >= MAX_DELETE_RETRIES) {
                document.setStatus(DocumentStatus.DELETE_FAILED);
                documentRepository.save(document);
                log.error("Cleanup failed for doc {} after {} retries. Marked DELETE_FAILED.",
                        docId, retryCount, e);

                if (isConversationDeleting) {
                    tryFinalizeConversationDelete(conversation);
                }
            } else {
                documentRepository.save(document);
                outboxEventService.enqueueDeletingDocument(document);
                log.warn("Cleanup failed for doc {} after Milvus delete, retry {}/{}",
                        docId, retryCount, MAX_DELETE_RETRIES, e);
            }
        }
    }

    @Override
    @Transactional
    public void handleIngestSuccess(Long docId, String extractedObjectName) {
        Document document = documentRepository.findById(docId)
            .orElseThrow(() -> new EntityNotFoundException("Document not found"));

        if (document.getStatus() == DocumentStatus.READY) {
            log.info("Document {} is already READY. Skipping to ensure idempotency.", docId);
            return;
        }

        Conversation conversation = document.getConversation();

        if (conversation.getStatus() == ConversationStatus.DELETING) {
            // Ingest finished but conversation is being deleted → start delete flow
            document.setStatus(DocumentStatus.DELETING);
            document.setExtractedObjectName(extractedObjectName);
            documentRepository.save(document);
            outboxEventService.enqueueDeletingDocument(document);
            log.info("Document {} ingest succeeded, but conversation {} is DELETING. Redirecting to delete flow.",
                    docId, conversation.getId());
        } else {
            document.setStatus(DocumentStatus.READY);
            document.setExtractedObjectName(extractedObjectName);
            documentRepository.save(document);
            log.info("Document {} processing succeeded. Status updated to READY.", docId);
        }
    }

    @Override
    @Transactional
    public void handleIngestFailed(Long docId) {
        Document document = documentRepository.findById(docId)
            .orElseThrow(() -> new EntityNotFoundException("Document not found"));
        Conversation conversation = document.getConversation();

        if (conversation.getStatus() == ConversationStatus.DELETING) {
            // Ingest failed and conversation is being deleted
            // Python already cleaned up Milvus chunks + extracted file
            // We only need to delete the original file from MinIO + remove from DB
            try {
                if (document.getObjectName() != null) {
                    minioService.deleteFile(document.getObjectName());
                }
            } catch (Exception e) {
                log.error("Failed to delete original MinIO file for doc {} during conversation delete",
                        docId, e);
            }
            
            // Cleanup message citations/references linking to document
            messageRepository.deleteCitationsByDocumentId(docId);
            messageRepository.deleteMessageDocumentsByDocumentId(docId);
            
            documentRepository.delete(document);
            tryFinalizeConversationDelete(conversation);
            log.info("Document {} ingest failed during conversation delete. Cleaned up.", docId);
        } else {
            document.setStatus(DocumentStatus.FAILED);
            documentRepository.save(document);
            log.error("Document {} processing failed.", docId);
        }
    }

    private void tryFinalizeConversationDelete(Conversation conversation) {
        documentRepository.flush();
        List<Document> remaining = documentRepository.findAllByConversationId(conversation.getId());

        if (remaining.isEmpty()) {
            conversationRepository.delete(conversation);
            outboxEventService.enqueueDropPartitionCommand(conversation.getId());
            log.info("All documents cleaned up. Conversation {} deleted.", conversation.getId());
            return;
        }

        boolean allFailed = remaining.stream()
                .allMatch(d -> d.getStatus() == DocumentStatus.DELETE_FAILED);

        if (allFailed) {
            conversation.setStatus(ConversationStatus.DELETE_FAILED);
            conversationRepository.save(conversation);
            log.error("Conversation {} marked DELETE_FAILED. {} document(s) could not be deleted.",
                    conversation.getId(), remaining.size());
        } else {
            log.info("Conversation {} still has {} documents pending deletion.",
                    conversation.getId(), remaining.size());
        }
    }
}

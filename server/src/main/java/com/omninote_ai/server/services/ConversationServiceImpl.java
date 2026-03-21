package com.omninote_ai.server.services;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.omninote_ai.server.dto.ConversationCreateRequest;
import com.omninote_ai.server.dto.ConversationCreateResponse;
import com.omninote_ai.server.dto.ConversationDeleteResponse;
import com.omninote_ai.server.dto.ConversationHistoryResponse;
import com.omninote_ai.server.dto.ConversationSummary;
import com.omninote_ai.server.dto.MessageResponse;
import com.omninote_ai.server.entity.Conversation;
import com.omninote_ai.server.entity.ConversationStatus;
import com.omninote_ai.server.entity.Document;
import com.omninote_ai.server.entity.DocumentStatus;
import com.omninote_ai.server.exception.CreateConversationException;
import com.omninote_ai.server.exception.UploadFileException;
import com.omninote_ai.server.entity.Message;
import com.omninote_ai.server.mapper.ConversationMapper;
import com.omninote_ai.server.mapper.DocumentMapper;
import com.omninote_ai.server.mapper.MessageMapper;
import com.omninote_ai.server.outbox.exception.EnqueueOutboxEventException;
import com.omninote_ai.server.repositories.ConversationRepository;
import com.omninote_ai.server.repositories.DocumentRepository;
import com.omninote_ai.server.repositories.MessageRepository;
import com.omninote_ai.server.repositories.UserRepository;
import com.omninote_ai.server.utility.JwtUtil;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationServiceImpl implements ConversationService {

    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final MinioService minioService;
    private final OutboxEventService outboxEventService;
    private final JwtUtil jwtUtil;
    private final DocumentRepository documentRepository;
    private final MessageRepository messageRepository;
    private final DocumentSyncService documentSyncService;

    @Override
    @Transactional
    public ConversationCreateResponse create(ConversationCreateRequest request) {
        try {
            Long currentUserId = jwtUtil.getCurrentUserId();
            var user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

            Conversation conversation = ConversationMapper.toEntity(request);
            conversation.setUser(user); 
            
            uploadAndAttachDocuments(conversation, request.getFiles());
            return ConversationMapper.toCreateResponse(conversation);
        } catch (Exception e) {
            log.error("Error creating conversation", e);
            throw new CreateConversationException("Failed to create conversation", e);
        }
    }

    public List<Document> uploadAndAttachDocuments(Conversation conversation, List<MultipartFile> files) {
        List<Document> uploadedDocuments = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                String objectName = minioService.uploadFile(file);

                Document document = new Document();
                String fileName = Paths.get(file.getOriginalFilename()).getFileName().toString();
                document.setFileName(fileName);
                document.setObjectName(objectName);
                document.setConversation(conversation);
                uploadedDocuments.add(document);
            }
            conversation.getDocuments().addAll(uploadedDocuments);
            documentRepository.saveAll(uploadedDocuments);
            conversation = conversationRepository.save(conversation);

            for (Document doc : uploadedDocuments) {
                documentSyncService.syncDocumentStatus(doc);
            }

            outboxEventService.enqueueUploadedDocuments(uploadedDocuments);
        } catch (UploadFileException | EnqueueOutboxEventException e) {
            for (Document doc : uploadedDocuments) {
                try {
                    minioService.deleteFile(doc.getObjectName());
                } catch (Exception ex) {
                    log.error("Failed to delete file from MinIO: {}", doc.getObjectName(), ex);
                }
            }
            throw e;
        }

        return uploadedDocuments;
    }

    @Override
    @Transactional
    public ConversationDeleteResponse deleteConversation(Long conversationId) {
        Long currentUserId = jwtUtil.getCurrentUserId();
        Conversation conversation = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new EntityNotFoundException("Conversation not found"));

        if (!conversation.getUser().getId().equals(currentUserId)) {
            throw new AccessDeniedException("User does not own this conversation");
        }

        if (conversation.getStatus() == ConversationStatus.DELETING) {
            throw new IllegalStateException("Conversation is already being deleted");
        }

        conversation.setStatus(ConversationStatus.DELETING);
        conversationRepository.save(conversation);

        messageRepository.deleteCitationsByConversationId(conversationId);
        messageRepository.deleteMessageDocumentsByConversationId(conversationId);
        messageRepository.deleteAllByConversationId(conversationId);

        List<Document> documents = documentRepository.findAllByConversationId(conversationId);
        int asyncDocsCount = 0;
        int deletedImmediately = 0;

        for (Document document : documents) {
            switch (document.getStatus()) {
                case READY:
                    document.setStatus(DocumentStatus.DELETING);
                    documentRepository.save(document);
                    outboxEventService.enqueueDeletingDocument(document);
                    asyncDocsCount++;
                    break;

                case PROCESSING:
                    document.setStatus(DocumentStatus.DELETING);
                    documentRepository.save(document);
                    asyncDocsCount++;
                    break;

                case FAILED:
                    cleanupFailedDocument(document);
                    deletedImmediately++;
                    break;

                case DELETING:
                case DELETE_FAILED:
                    asyncDocsCount++;
                    break;

                default:
                    log.warn("Unknown document status {} for doc {}", document.getStatus(), document.getId());
                    asyncDocsCount++;
                    break;
            }
        }

        if (asyncDocsCount == 0) {
            conversationRepository.delete(conversation);
            outboxEventService.enqueueDropPartitionCommand(conversationId);
            log.info("Conversation {} deleted immediately (no async docs)", conversationId);

            return ConversationDeleteResponse.builder()
                    .conversationId(conversationId)
                    .status("DELETED")
                    .message("Conversation deleted successfully")
                    .totalDocuments(documents.size())
                    .asyncPending(0)
                    .deletedImmediately(deletedImmediately)
                    .build();
        }

        log.info("Conversation {} marked as DELETING, {} docs pending async cleanup",
                conversationId, asyncDocsCount);

        return ConversationDeleteResponse.builder()
                .conversationId(conversationId)
                .status("DELETING")
                .message("Conversation deletion in progress. " + asyncDocsCount
                        + " document(s) being cleaned up asynchronously.")
                .totalDocuments(documents.size())
                .asyncPending(asyncDocsCount)
                .deletedImmediately(deletedImmediately)
                .build();
    }

    @Override
    public List<ConversationSummary> getConversations() {
        Long currentUserId = jwtUtil.getCurrentUserId();

        List<Conversation> conversations = conversationRepository
                .findByUserIdAndStatusNotInOrderByUpdatedAtDesc(
                        currentUserId,
                        List.of(ConversationStatus.DELETING, ConversationStatus.DELETE_FAILED));

        return conversations.stream().map(conv -> ConversationSummary.builder()
                .id(conv.getId())
                .title(conv.getTitle())
                .status(conv.getStatus().name())
                .documentCount(conv.getDocuments().size())
                .messageCount((int) messageRepository.countByConversationId(conv.getId()))
                .createdAt(conv.getCreatedAt())
                .updatedAt(conv.getUpdatedAt())
                .build())
                .toList();
    }

    @Override
    public ConversationHistoryResponse getConversationHistory(Long conversationId) {
        Long currentUserId = jwtUtil.getCurrentUserId();

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new EntityNotFoundException("Conversation not found"));

        if (!conversation.getUser().getId().equals(currentUserId)) {
            throw new AccessDeniedException("User does not own this conversation");
        }

        if (conversation.getStatus() == ConversationStatus.DELETING
                || conversation.getStatus() == ConversationStatus.DELETE_FAILED) {
            throw new IllegalStateException("Conversation is no longer accessible");
        }

        List<Message> messages = messageRepository
                .findAllByConversationIdOrderByCreatedAtAsc(conversationId);

        List<MessageResponse> messageResponses = messages.stream()
                .map(MessageMapper::toResponse)
                .toList();

        return ConversationHistoryResponse.builder()
                .conversationId(conversation.getId())
                .title(conversation.getTitle())
                .status(conversation.getStatus().name())
                .documents(conversation.getDocuments().stream()
                        .map(DocumentMapper::toSummary)
                        .toList())
                .messages(messageResponses)
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional
    public ConversationSummary updateConversationTitle(Long conversationId, String newTitle) {
        Long currentUserId = jwtUtil.getCurrentUserId();

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new EntityNotFoundException("Conversation not found"));

        if (!conversation.getUser().getId().equals(currentUserId)) {
            throw new AccessDeniedException("User does not own this conversation");
        }

        conversation.setTitle(newTitle);
        conversation = conversationRepository.save(conversation);

        return ConversationSummary.builder()
                .id(conversation.getId())
                .title(conversation.getTitle())
                .status(conversation.getStatus().name())
                .documentCount(conversation.getDocuments().size())
                .messageCount((int) messageRepository.countByConversationId(conversation.getId()))
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }

    private void cleanupFailedDocument(Document document) {
        try {
            if (document.getObjectName() != null) {
                minioService.deleteFile(document.getObjectName());
            }
        } catch (Exception e) {
            log.error("Failed to delete original MinIO file for doc {}: {}",
                    document.getId(), e.getMessage());
        }
        try {
            if (document.getExtractedObjectName() != null) {
                minioService.deleteFile(document.getExtractedObjectName());
            }
        } catch (Exception e) {
            log.error("Failed to delete extracted MinIO file for doc {}: {}",
                    document.getId(), e.getMessage());
        }
        documentRepository.delete(document);
    }
}

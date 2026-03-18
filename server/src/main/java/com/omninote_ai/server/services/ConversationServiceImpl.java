package com.omninote_ai.server.services;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.omninote_ai.server.dto.ConversationCreateRequest;
import com.omninote_ai.server.dto.ConversationCreateResponse;
import com.omninote_ai.server.entity.Conversation;
import com.omninote_ai.server.entity.Document;
import com.omninote_ai.server.exception.CreateConversationException;
import com.omninote_ai.server.exception.UploadFileException;
import com.omninote_ai.server.mapper.ConversationMapper;
import com.omninote_ai.server.outbox.exception.EnqueueOutboxEventException;
import com.omninote_ai.server.repositories.ConversationRepository;
import com.omninote_ai.server.repositories.DocumentRepository;
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
}

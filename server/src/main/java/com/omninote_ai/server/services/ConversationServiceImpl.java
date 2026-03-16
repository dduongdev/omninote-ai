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

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationServiceImpl implements ConversationService {

    private final ConversationRepository conversationRepository;
    private final MinioService minioService;
    private final OutboxEventService outboxEventService;

    @Override
    @Transactional
    public ConversationCreateResponse create(ConversationCreateRequest request) {
        Conversation conversation = ConversationMapper.toEntity(request);
        conversation = uploadAndAttachDocuments(conversation, request.getFiles());
        return ConversationMapper.toCreateResponse(conversation);
    }

    private Conversation uploadAndAttachDocuments(Conversation conversation, List<MultipartFile> files) {
        List<Document> uploadedDocuments = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                String objectName = minioService.uploadFile(file);

                Document document = new Document();
                String fileName = Paths.get(file.getOriginalFilename()).getFileName().toString();
                document.setFileName(fileName);
                document.setObjectName(objectName);
                uploadedDocuments.add(document);
            }
            conversation.getDocuments().addAll(uploadedDocuments);
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
            log.error(e.getMessage());
            throw new CreateConversationException("Failed to create conversation", e);
        }

        return conversation;
    }
}

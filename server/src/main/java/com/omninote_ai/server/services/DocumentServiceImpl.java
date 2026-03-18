package com.omninote_ai.server.services;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.omninote_ai.server.dto.DocumentSummary;
import com.omninote_ai.server.dto.DocumentUploadRequest;
import com.omninote_ai.server.dto.DocumentUploadResponse;
import com.omninote_ai.server.entity.Conversation;
import com.omninote_ai.server.entity.Document;
import com.omninote_ai.server.entity.User;
import com.omninote_ai.server.exception.UploadFileException;
import com.omninote_ai.server.mapper.DocumentMapper;
import com.omninote_ai.server.repositories.ConversationRepository;
import com.omninote_ai.server.repositories.UserRepository;
import com.omninote_ai.server.utility.JwtUtil;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {
    private final ConversationService conversationService;
    private final JwtUtil jwtUtil;
    private final ConversationRepository conversationRepository;

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
}

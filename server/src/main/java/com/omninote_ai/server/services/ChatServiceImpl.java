package com.omninote_ai.server.services;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import com.omninote_ai.server.client.MessageClient;
import com.omninote_ai.server.dto.MessageCreateRequest;
import com.omninote_ai.server.dto.MessageResponse;
import com.omninote_ai.server.entity.Conversation;
import com.omninote_ai.server.entity.Document;
import com.omninote_ai.server.entity.Message;
import com.omninote_ai.server.entity.MessageCitation;
import com.omninote_ai.server.entity.User;
import com.omninote_ai.server.exception.SelectedFileException;
import com.omninote_ai.server.mapper.MessageMapper;
import com.omninote_ai.server.repositories.ConversationRepository;
import com.omninote_ai.server.repositories.DocumentRepository;
import com.omninote_ai.server.repositories.MessageRepository;
import com.omninote_ai.server.repositories.UserRepository;
import com.omninote_ai.server.utility.JwtUtil;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final MessageRepository messageRepository;
    private final DocumentRepository documentRepository;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final MessageClient client;
    private final JwtUtil jwtUtil;

    @Override
    @Transactional
    public MessageResponse sendMessage(MessageCreateRequest request) {
        Long currentUserId = jwtUtil.getCurrentUserId();

        // 1. Fetch and Validate Conversation Ownership
        if (!conversationRepository.existsByIdAndUserId(request.getConversationId(), currentUserId)) {
             throw new RuntimeException("Cuộc hội thoại không tồn tại hoặc không thuộc về người dùng này");
        }

        Conversation conv = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy hội thoại"));

        // 2. Validate selected documents
        List<Document> selectedDocs = validateAndGetDocuments(request, conv);

        // 3. Call AI service to generate answer and citations
        MessageResponse aiResponse = client.generateAIResponse(request);

        // 4. Build and Persist Message Entity
        Message message = buildMessageEntity(request, aiResponse, conv, selectedDocs);
        message = messageRepository.save(message);

        // 5. Map back to Response DTO
        return MessageMapper.toResponse(message);
    }

    private List<Document> validateAndGetDocuments(MessageCreateRequest request, Conversation conv) {
        if (request.getDocumentIds() == null || request.getDocumentIds().isEmpty()) {
            throw new SelectedFileException("Vui lòng chọn ít nhất một tài liệu.");
        }

        List<Document> selectedDocs = documentRepository.findAllById(request.getDocumentIds());
        if (selectedDocs.size() != request.getDocumentIds().size()) {
            throw new SelectedFileException("Một số tài liệu được chọn không tồn tại.");
        }

        boolean allBelongToConv = selectedDocs.stream()
                .allMatch(d -> d.getConversation() != null && d.getConversation().getId().equals(conv.getId()));
        
        if (!allBelongToConv) {
            throw new SelectedFileException("Một số tài liệu không thuộc về cuộc hội thoại này.");
        }
        
        return selectedDocs;
    }

    private Message buildMessageEntity(MessageCreateRequest request, MessageResponse aiResponse, Conversation conv, List<Document> selectedDocs) {
        Message message = Message.builder()
                .conversation(conv)
                .contentQuery(request.getContentQuery())
                .contentAnswer(aiResponse.getContentAnswer())
                .selectedDocuments(selectedDocs)
                .build();

        if (aiResponse.getCitationsResponses() != null) {
            List<MessageCitation> citations = aiResponse.getCitationsResponses().stream()
                    .map(dto -> {
                        Document doc = selectedDocs.stream()
                                .filter(d -> d.getId().equals(dto.getDocumentId()))
                                .findFirst()
                                .orElse(null);
                        
                        if (doc == null) return null; // Skip if AI cites a doc that wasn't provided or doesn't exist

                        return MessageCitation.builder()
                                .message(message)
                                .document(doc)
                                .startIndex(dto.getStartIndex())
                                .endIndex(dto.getEndIndex())
                                .build();
                    })
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
            
            message.setCitations(citations);
        }
        return message;
    }

    public boolean currentUserHasConversation(Jwt jwt, Long conversationId) {
        String userName = jwt.getSubject();
        User user = userRepository.findByUserName(userName)
            .orElseThrow(() -> new UsernameNotFoundException(userName));
        return conversationRepository.existsByIdAndUserId(conversationId, user.getId());
    }


}

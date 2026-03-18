package com.omninote_ai.server.mapper;

import com.omninote_ai.server.dto.ConversationCreateRequest;
import com.omninote_ai.server.dto.ConversationCreateResponse;
import com.omninote_ai.server.entity.Conversation;
import com.omninote_ai.server.entity.Document;

public class ConversationMapper {
    public static Conversation toEntity(ConversationCreateRequest request) {
        Conversation conversation = new Conversation();
        if (request.getTitle() != null && !request.getTitle().trim().isEmpty()) {
            conversation.setTitle(request.getTitle());
        }

        return conversation;
    }

    public static ConversationCreateResponse toCreateResponse(Conversation conversation) {
        ConversationCreateResponse response = new ConversationCreateResponse();
        response.setId(conversation.getId());
        response.setTitle(conversation.getTitle());
        response.setCreatedAt(conversation.getCreatedAt());
        response.setUpdatedAt(conversation.getUpdatedAt());

        for (Document document : conversation.getDocuments()) {
            response.getDocuments().add(DocumentMapper.toSummary(document));
        }

        return response;
    }
}

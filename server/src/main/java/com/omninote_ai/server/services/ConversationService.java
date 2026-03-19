package com.omninote_ai.server.services;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.omninote_ai.server.dto.ConversationCreateRequest;
import com.omninote_ai.server.dto.ConversationCreateResponse;
import com.omninote_ai.server.dto.ConversationDeleteResponse;
import com.omninote_ai.server.entity.Conversation;
import com.omninote_ai.server.entity.Document;

public interface ConversationService {
    ConversationCreateResponse create(ConversationCreateRequest request);
    List<Document> uploadAndAttachDocuments(Conversation conversation, List<MultipartFile> files);
    ConversationDeleteResponse deleteConversation(Long conversationId);
}

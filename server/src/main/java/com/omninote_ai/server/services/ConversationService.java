package com.omninote_ai.server.services;

import com.omninote_ai.server.dto.ConversationCreateRequest;
import com.omninote_ai.server.dto.ConversationCreateResponse;

public interface ConversationService {
    ConversationCreateResponse create(ConversationCreateRequest request);
}

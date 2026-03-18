package com.omninote_ai.server.services;

import com.omninote_ai.server.dto.MessageCreateRequest;
import com.omninote_ai.server.dto.MessageResponse;

public interface ChatService {
    MessageResponse sendMessage(MessageCreateRequest request);
}

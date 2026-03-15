package com.omninote_ai.server.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.omninote_ai.server.entity.Conversation;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    
}

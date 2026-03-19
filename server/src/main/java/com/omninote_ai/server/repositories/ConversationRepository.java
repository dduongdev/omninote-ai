package com.omninote_ai.server.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.omninote_ai.server.entity.Conversation;
import com.omninote_ai.server.entity.ConversationStatus;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    boolean existsByIdAndUserId(Long id, Long userId);

    List<Conversation> findByUserIdAndStatusOrderByUpdatedAtDesc(Long userId, ConversationStatus status);

    List<Conversation> findByUserIdAndStatusNotInOrderByUpdatedAtDesc(Long userId, List<ConversationStatus> excludedStatuses);
}

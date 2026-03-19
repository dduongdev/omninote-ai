package com.omninote_ai.server.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.omninote_ai.server.entity.Document;
import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findAllByConversationId(Long conversationId);
    List<Document> findAllByIdInAndConversationId(List<Long> ids, Long conversationId);
    long countByConversationId(Long conversationId);
}

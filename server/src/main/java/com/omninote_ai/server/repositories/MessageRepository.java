package com.omninote_ai.server.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.omninote_ai.server.entity.Message;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findAllByConversationId(Long conversationId);

    List<Message> findAllByConversationIdOrderByCreatedAtAsc(Long conversationId);

    long countByConversationId(Long conversationId);

    @Modifying
    @Query(value = "DELETE FROM message_citations WHERE message_id IN (SELECT id FROM messages WHERE conversation_id = :convId)", nativeQuery = true)
    void deleteCitationsByConversationId(@Param("convId") Long conversationId);

    @Modifying
    @Query(value = "DELETE FROM message_documents WHERE message_id IN (SELECT id FROM messages WHERE conversation_id = :convId)", nativeQuery = true)
    void deleteMessageDocumentsByConversationId(@Param("convId") Long conversationId);

    @Modifying
    @Query("DELETE FROM Message m WHERE m.conversation.id = :convId")
    void deleteAllByConversationId(@Param("convId") Long conversationId);
}

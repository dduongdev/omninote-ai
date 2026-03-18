package com.omninote_ai.server.outbox.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.omninote_ai.server.outbox.entity.OutboxEvent;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    List<OutboxEvent> findByIsPublishedFalse();
}

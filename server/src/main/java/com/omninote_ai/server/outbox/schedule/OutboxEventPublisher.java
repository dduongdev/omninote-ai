package com.omninote_ai.server.outbox.schedule;

import java.util.List;

import com.omninote_ai.server.outbox.entity.OutboxEvent;
import com.omninote_ai.server.outbox.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxEventRepository;

    public void publish() {
        List<OutboxEvent> events = outboxEventRepository.findByIsPublishedFalse();
        for (OutboxEvent event : events) {
            try {
                    
            } catch (Exception e) {
                continue;
            }
        }
    }
}

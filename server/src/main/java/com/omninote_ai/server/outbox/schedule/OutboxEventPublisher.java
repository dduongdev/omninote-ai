package com.omninote_ai.server.outbox.schedule;

import java.util.List;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import com.omninote_ai.server.outbox.entity.OutboxEvent;
import com.omninote_ai.server.outbox.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelay = 5000)
    public void publish() {
        List<OutboxEvent> events = outboxEventRepository.findByIsPublishedFalse();
        for (OutboxEvent event : events) {
            try {
                rabbitTemplate.convertAndSend(event.getEventType(), event.getPayload());
                event.setPublished(true);
                outboxEventRepository.save(event);
            } catch (Exception e) {
                continue;
            }
        }
    }
}

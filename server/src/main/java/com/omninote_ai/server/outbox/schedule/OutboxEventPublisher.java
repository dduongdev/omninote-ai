package com.omninote_ai.server.outbox.schedule;

import java.util.List;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.omninote_ai.server.config.RabbitMqConfig;
import com.omninote_ai.server.outbox.entity.OutboxEvent;
import com.omninote_ai.server.outbox.repository.OutboxEventRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publish() {
        List<OutboxEvent> events = outboxEventRepository.findByIsPublishedFalse();
        for (OutboxEvent event : events) {
            try {
                rabbitTemplate.convertAndSend(RabbitMqConfig.DOCUMENT_TOPIC_EXCHANGE, event.getEventType(), event.getPayload());
                event.setPublished(true);
                outboxEventRepository.save(event);
            } catch (Exception e) {
                log.error("Failed to publish outbox event: {}", event.getId(), e);
                continue;
            }
        }
    }
}

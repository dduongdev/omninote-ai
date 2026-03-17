package com.omninote_ai.server.listener;

import java.io.IOException;
import java.util.Map;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import com.rabbitmq.client.Channel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omninote_ai.server.config.RabbitMqConfig;
import com.omninote_ai.server.entity.Document;
import com.omninote_ai.server.entity.DocumentStatus;
import com.omninote_ai.server.repositories.DocumentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentEventListener {

    private final DocumentRepository documentRepository;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMqConfig.DOCUMENT_INGEST_QUEUE, ackMode = "MANUAL")
    public void onDocumentIngestEvent(String payload, 
                                      @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey,
                                      @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag, 
                                      Channel channel) throws IOException {
        log.info("Received ingest event with routing key: {}, payload: {}", routingKey, payload);
        
        try {
            Map<String, Object> data = objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {});
            
            Object docIdObj = data.get("doc_id");
            if (docIdObj == null) {
                log.error("doc_id is null in payload");
                channel.basicAck(deliveryTag, false);
                return;
            }
            Long docId = Long.valueOf(docIdObj.toString());

            Document document = documentRepository.findById(docId).orElse(null);
            if (document == null) {
                log.error("Document with id {} not found in database", docId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            if (document.getStatus() == DocumentStatus.READY) {
                log.info("Document {} is already READY. Skipping to ensure idempotency.", docId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            if ("document.ingest.succeed".equals(routingKey)) {
                String extractedObjectName = (String) data.getOrDefault("extracted_object_name", document.getExtractedObjectName());
                
                document.setStatus(DocumentStatus.READY);
                document.setExtractedObjectName(extractedObjectName);
                documentRepository.save(document);
                
                log.info("Document {} processing succeeded. Status updated to READY", docId);
                
            } else if ("document.ingest.failed".equals(routingKey)) {
                document.setStatus(DocumentStatus.FAILED);
                documentRepository.save(document);
                
                log.error("Document {} processing failed. Error: {}", docId, data.get("error"));
            } else {
                log.warn("Unknown routing key: {}", routingKey);
            }
            
            channel.basicAck(deliveryTag, false);
            
        } catch (JsonProcessingException e) {
            log.error("Failed to parse event payload json. Acking to remove poison message.", e);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Error processing document ingest event in listener. Nacking event...", e);
            channel.basicNack(deliveryTag, false, true);
        }
    }
}

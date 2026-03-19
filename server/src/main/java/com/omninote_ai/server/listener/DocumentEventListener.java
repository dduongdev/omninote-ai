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
import com.omninote_ai.server.services.DocumentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentEventListener {

    private final ObjectMapper objectMapper;
    private final DocumentService documentService;

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

            if ("document.ingest.succeed".equals(routingKey)) {
                String extractedObjectName = (String) data.getOrDefault("extracted_object_name", null);
                documentService.handleIngestSuccess(docId, extractedObjectName);
            } else if ("document.ingest.failed".equals(routingKey)) {
                documentService.handleIngestFailed(docId);
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

    @RabbitListener(queues = RabbitMqConfig.DOCUMENT_SOFT_DELETED_SUCCESS_QUEUE, ackMode = "MANUAL")
    public void onMilvusDeletedSuccess(String payload,
                                       @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey,
                                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag, 
                                       Channel channel) throws IOException {
        log.info("Received Milvus delete success event: {}", payload);
        try {
            com.omninote_ai.server.event.MilvusSoftDeletedSuccessEvent event = 
                objectMapper.readValue(payload, com.omninote_ai.server.event.MilvusSoftDeletedSuccessEvent.class);
            
            if (event.getDocId() != null) {
                documentService.handleMilvusDeleteSuccess(event.getDocId());
            } else {
                log.error("doc_id is null in MilvusDeletedSuccessEvent");
            }
            channel.basicAck(deliveryTag, false);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse success event payload. Acking poison message.", e);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Error processing success event. Nacking...", e);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    @RabbitListener(queues = RabbitMqConfig.DOCUMENT_SOFT_DELETED_FAILED_QUEUE, ackMode = "MANUAL")
    public void onMilvusDeletedFailed(String payload,
                                      @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey,
                                      @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag, 
                                      Channel channel) throws IOException {
        log.info("Received Milvus delete failed event: {}", payload);
        try {
            com.omninote_ai.server.event.MilvusSoftDeletedFailedEvent event = 
                objectMapper.readValue(payload, com.omninote_ai.server.event.MilvusSoftDeletedFailedEvent.class);
            
            if (event.getDocId() != null) {
                documentService.handleMilvusDeleteFailed(event.getDocId());
            } else {
                log.error("doc_id is null in MilvusDeletedFailedEvent");
            }
            channel.basicAck(deliveryTag, false);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse failed event payload. Acking poison message.", e);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Error processing failed event. Nacking...", e);
            channel.basicNack(deliveryTag, false, true);
        }
    }
}

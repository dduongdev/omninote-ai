package com.omninote_ai.server.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class RabbitMqConfig {
    
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new org.springframework.amqp.support.converter.SimpleMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }

    //#region RabbitMQ Queue and Exchange Configuration (if needed)

    public static final String DOCUMENT_TOPIC_EXCHANGE = "document.topic.exchange";
    public static final String DOCUMENT_UPLOAD_QUEUE = "document.upload.queue";
    public static final String DOCUMENT_INGEST_QUEUE = "document.ingest.queue";
    public static final String DOCUMENT_SOFT_DELETED_SUCCESS_QUEUE = "document.soft.deleted.success.queue";
    public static final String DOCUMENT_SOFT_DELETED_FAILED_QUEUE = "document.soft.deleted.failed.queue";
    public static final String DOCUMENT_UPLOAD_ROUTING_KEY = "document.uploaded";
    public static final String DOCUMENT_INGEST_ROUTING_KEY = "document.ingest.#";
    public static final String MILVUS_SOFT_DELETED_SUCCESS_ROUTING_KEY = "MILVUS_SOFT_DELETED_SUCCESS";
    public static final String MILVUS_SOFT_DELETED_FAILED_ROUTING_KEY = "MILVUS_SOFT_DELETED_FAILED";

    @Bean
    public TopicExchange documentTopicExchange() {
        return new TopicExchange(DOCUMENT_TOPIC_EXCHANGE);
    }

    @Bean
    public Queue documentUploadQueue() {
        return QueueBuilder.durable(DOCUMENT_UPLOAD_QUEUE).build();
    }

    @Bean
    public Queue documentIngestQueue() {
        return QueueBuilder.durable(DOCUMENT_INGEST_QUEUE).build();
    }

    @Bean
    public Queue documentSoftDeletedSuccessQueue() {
        return QueueBuilder.durable(DOCUMENT_SOFT_DELETED_SUCCESS_QUEUE).build();
    }

    @Bean
    public Queue documentSoftDeletedFailedQueue() {
        return QueueBuilder.durable(DOCUMENT_SOFT_DELETED_FAILED_QUEUE).build();
    }

    @Bean
    public Binding bindingDocumentUploadQueue(Queue documentUploadQueue, TopicExchange documentTopicExchange) {
        return BindingBuilder.bind(documentUploadQueue).to(documentTopicExchange).with(DOCUMENT_UPLOAD_ROUTING_KEY);
    }

    @Bean
    public Binding bindingDocumentIngestQueue(Queue documentIngestQueue, TopicExchange documentTopicExchange) {
        return BindingBuilder.bind(documentIngestQueue).to(documentTopicExchange).with(DOCUMENT_INGEST_ROUTING_KEY);
    }

    @Bean
    public Binding bindingDocumentSoftDeletedSuccessQueue(Queue documentSoftDeletedSuccessQueue, TopicExchange documentTopicExchange) {
        return BindingBuilder.bind(documentSoftDeletedSuccessQueue).to(documentTopicExchange).with(MILVUS_SOFT_DELETED_SUCCESS_ROUTING_KEY);
    }

    @Bean
    public Binding bindingDocumentSoftDeletedFailedQueue(Queue documentSoftDeletedFailedQueue, TopicExchange documentTopicExchange) {
        return BindingBuilder.bind(documentSoftDeletedFailedQueue).to(documentTopicExchange).with(MILVUS_SOFT_DELETED_FAILED_ROUTING_KEY);
    }

    //#endregion
}
    
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
    public static final String DOCUMENT_UPLOAD_ROUTING_KEY = "document.uploaded";
    public static final String DOCUMENT_INGEST_ROUTING_KEY = "document.ingest.#";

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
    public Binding bindingDocumentUploadQueue(Queue documentUploadQueue, TopicExchange documentTopicExchange) {
        return BindingBuilder.bind(documentUploadQueue).to(documentTopicExchange).with(DOCUMENT_UPLOAD_ROUTING_KEY);
    }

    @Bean
    public Binding bindingDocumentIngestQueue(Queue documentIngestQueue, TopicExchange documentTopicExchange) {
        return BindingBuilder.bind(documentIngestQueue).to(documentTopicExchange).with(DOCUMENT_INGEST_ROUTING_KEY);
    }

    //#endregion
}
    
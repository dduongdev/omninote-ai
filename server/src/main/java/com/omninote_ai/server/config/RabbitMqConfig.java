package com.omninote_ai.server.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class RabbitMqConfig {
    @SuppressWarnings("removal")
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }

    //#region RabbitMQ Queue and Exchange Configuration (if needed)

    public static final String DOCUMENT_TOPIC_EXCHANGE = "document.topic.exchange";
    public static final String DOCUMENT_QUEUE = "document.queue";
    public static final String DOCUMENT_ROUTING_KEY = "document.#";

    @Bean
    public TopicExchange documentTopicExchange() {
        return new TopicExchange(DOCUMENT_TOPIC_EXCHANGE);
    }

    @Bean
    public Queue documentQueue() {
        return QueueBuilder.durable(DOCUMENT_QUEUE).build();
    }

    @Bean
    public Binding bindingDocumentQueue(Queue documentQueue, TopicExchange documentTopicExchange) {
        return BindingBuilder.bind(documentQueue).to(documentTopicExchange).with(DOCUMENT_ROUTING_KEY);
    }

    //#endregion
}

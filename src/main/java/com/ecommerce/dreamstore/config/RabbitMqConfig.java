package com.ecommerce.dreamstore.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String ORDER_EVENTS_EXCHANGE = "order.events.exchange";
    public static final String INVOICE_EMAIL_QUEUE = "order.invoice.email.queue";
    public static final String ROUTING_KEY_PAYMENT_COMPLETED = "order.payment.completed";

    public static final String DLX_EXCHANGE = "order.events.dlx";
    public static final String DLQ_QUEUE = "order.invoice.email.dlq";
    public static final String DLQ_ROUTING_KEY = "order.invoice.email.dlq";

    @Bean
    public TopicExchange orderEventsExchange() {
        return new TopicExchange(ORDER_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue invoiceEmailQueue() {
        return QueueBuilder.durable(INVOICE_EMAIL_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ_QUEUE).build();
    }

    @Bean
    public Binding invoiceEmailBinding(Queue invoiceEmailQueue, TopicExchange orderEventsExchange) {
        return BindingBuilder.bind(invoiceEmailQueue)
                .to(orderEventsExchange)
                .with(ROUTING_KEY_PAYMENT_COMPLETED);
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(DLQ_ROUTING_KEY);
    }

    @Bean
    @SuppressWarnings({"deprecation", "removal"})
    public MessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jacksonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jacksonMessageConverter);
        return template;
    }
}

package com.msg.delivery.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ Configuration
 * 
 * Defines durable queues, exchanges, and bindings for the MQ Delivery System.
 * 
 * Queues:
 * - Main Message Queue: Primary queue for message delivery
 * - Receipt Queue: Queue for delivery receipts to upstream systems
 * - Dead Letter Queue: Queue for messages that failed after max retries
 * 
 * All queues are configured as durable to survive broker restarts.
 */
@Configuration
public class RabbitMQConfig {

    @Value("${mq.queue.main}")
    private String mainQueueName;

    @Value("${mq.queue.receipt}")
    private String receiptQueueName;

    @Value("${mq.queue.dlq}")
    private String dlqName;

    // Exchange names
    private static final String MAIN_EXCHANGE = "mq.delivery.exchange.main";
    private static final String RECEIPT_EXCHANGE = "mq.delivery.exchange.receipt";
    private static final String DLQ_EXCHANGE = "mq.delivery.exchange.dlq";

    // Routing keys
    private static final String MAIN_ROUTING_KEY = "mq.delivery.routing.main";
    private static final String RECEIPT_ROUTING_KEY = "mq.delivery.routing.receipt";
    private static final String DLQ_ROUTING_KEY = "mq.delivery.routing.dlq";

    /**
     * Main Message Queue
     * 
     * Durable queue for primary message delivery.
     * Configuration:
     * - Durable: true (survives broker restart)
     * - Auto-delete: false
     * - Exclusive: false
     * - TTL: 24 hours (86400000 ms)
     * - Max length: 1,000,000 messages
     */
    @Bean
    public Queue mainQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", 86400000L); // 24 hours
        args.put("x-max-length", 1000000L);   // Max 1M messages
        
        return QueueBuilder.durable(mainQueueName)
                .withArguments(args)
                .build();
    }

    /**
     * Receipt Queue
     * 
     * Durable queue for delivery receipts to upstream systems.
     * Configuration:
     * - Durable: true (survives broker restart)
     * - Auto-delete: false
     * - Exclusive: false
     * - TTL: 7 days (604800000 ms)
     */
    @Bean
    public Queue receiptQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", 604800000L); // 7 days
        
        return QueueBuilder.durable(receiptQueueName)
                .withArguments(args)
                .build();
    }

    /**
     * Dead Letter Queue
     * 
     * Durable queue for messages that failed after max retry attempts.
     * Configuration:
     * - Durable: true (survives broker restart)
     * - Auto-delete: false
     * - Exclusive: false
     * - TTL: 30 days (2592000000 ms)
     */
    @Bean
    public Queue deadLetterQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", 2592000000L); // 30 days
        
        return QueueBuilder.durable(dlqName)
                .withArguments(args)
                .build();
    }

    /**
     * Main Exchange
     * 
     * Direct exchange for main message queue routing.
     */
    @Bean
    public DirectExchange mainExchange() {
        return new DirectExchange(MAIN_EXCHANGE, true, false);
    }

    /**
     * Receipt Exchange
     * 
     * Direct exchange for receipt queue routing.
     */
    @Bean
    public DirectExchange receiptExchange() {
        return new DirectExchange(RECEIPT_EXCHANGE, true, false);
    }

    /**
     * Dead Letter Exchange
     * 
     * Direct exchange for dead letter queue routing.
     */
    @Bean
    public DirectExchange dlqExchange() {
        return new DirectExchange(DLQ_EXCHANGE, true, false);
    }

    /**
     * Main Queue Binding
     * 
     * Binds main queue to main exchange with routing key.
     */
    @Bean
    public Binding mainQueueBinding(Queue mainQueue, DirectExchange mainExchange) {
        return BindingBuilder.bind(mainQueue)
                .to(mainExchange)
                .with(MAIN_ROUTING_KEY);
    }

    /**
     * Receipt Queue Binding
     * 
     * Binds receipt queue to receipt exchange with routing key.
     */
    @Bean
    public Binding receiptQueueBinding(Queue receiptQueue, DirectExchange receiptExchange) {
        return BindingBuilder.bind(receiptQueue)
                .to(receiptExchange)
                .with(RECEIPT_ROUTING_KEY);
    }

    /**
     * Dead Letter Queue Binding
     * 
     * Binds dead letter queue to DLQ exchange with routing key.
     */
    @Bean
    public Binding dlqBinding(Queue deadLetterQueue, DirectExchange dlqExchange) {
        return BindingBuilder.bind(deadLetterQueue)
                .to(dlqExchange)
                .with(DLQ_ROUTING_KEY);
    }
}

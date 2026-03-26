package com.msg.delivery.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RabbitMQConfig
 * 
 * Validates queue configuration, exchange setup, and bindings.
 */
class RabbitMQConfigTest {

    private RabbitMQConfig config;

    @BeforeEach
    void setUp() {
        config = new RabbitMQConfig();
        ReflectionTestUtils.setField(config, "mainQueueName", "mq.delivery.main");
        ReflectionTestUtils.setField(config, "receiptQueueName", "mq.delivery.receipt");
        ReflectionTestUtils.setField(config, "dlqName", "mq.delivery.dlq");
    }

    @Test
    void testMainQueue_ShouldBeDurableWithCorrectProperties() {
        Queue mainQueue = config.mainQueue();

        assertNotNull(mainQueue);
        assertEquals("mq.delivery.main", mainQueue.getName());
        assertTrue(mainQueue.isDurable(), "Main queue should be durable");
        assertFalse(mainQueue.isAutoDelete(), "Main queue should not auto-delete");
        assertFalse(mainQueue.isExclusive(), "Main queue should not be exclusive");
        
        // Verify TTL and max length
        assertEquals(86400000L, mainQueue.getArguments().get("x-message-ttl"), 
                "Main queue TTL should be 24 hours");
        assertEquals(1000000L, mainQueue.getArguments().get("x-max-length"), 
                "Main queue max length should be 1M messages");
    }

    @Test
    void testReceiptQueue_ShouldBeDurableWithCorrectProperties() {
        Queue receiptQueue = config.receiptQueue();

        assertNotNull(receiptQueue);
        assertEquals("mq.delivery.receipt", receiptQueue.getName());
        assertTrue(receiptQueue.isDurable(), "Receipt queue should be durable");
        assertFalse(receiptQueue.isAutoDelete(), "Receipt queue should not auto-delete");
        assertFalse(receiptQueue.isExclusive(), "Receipt queue should not be exclusive");
        
        // Verify TTL
        assertEquals(604800000L, receiptQueue.getArguments().get("x-message-ttl"), 
                "Receipt queue TTL should be 7 days");
    }

    @Test
    void testDeadLetterQueue_ShouldBeDurableWithCorrectProperties() {
        Queue dlq = config.deadLetterQueue();

        assertNotNull(dlq);
        assertEquals("mq.delivery.dlq", dlq.getName());
        assertTrue(dlq.isDurable(), "DLQ should be durable");
        assertFalse(dlq.isAutoDelete(), "DLQ should not auto-delete");
        assertFalse(dlq.isExclusive(), "DLQ should not be exclusive");
        
        // Verify TTL
        assertEquals(2592000000L, dlq.getArguments().get("x-message-ttl"), 
                "DLQ TTL should be 30 days");
    }

    @Test
    void testMainExchange_ShouldBeDurableDirectExchange() {
        DirectExchange exchange = config.mainExchange();

        assertNotNull(exchange);
        assertEquals("mq.delivery.exchange.main", exchange.getName());
        assertTrue(exchange.isDurable(), "Main exchange should be durable");
        assertFalse(exchange.isAutoDelete(), "Main exchange should not auto-delete");
    }

    @Test
    void testReceiptExchange_ShouldBeDurableDirectExchange() {
        DirectExchange exchange = config.receiptExchange();

        assertNotNull(exchange);
        assertEquals("mq.delivery.exchange.receipt", exchange.getName());
        assertTrue(exchange.isDurable(), "Receipt exchange should be durable");
        assertFalse(exchange.isAutoDelete(), "Receipt exchange should not auto-delete");
    }

    @Test
    void testDlqExchange_ShouldBeDurableDirectExchange() {
        DirectExchange exchange = config.dlqExchange();

        assertNotNull(exchange);
        assertEquals("mq.delivery.exchange.dlq", exchange.getName());
        assertTrue(exchange.isDurable(), "DLQ exchange should be durable");
        assertFalse(exchange.isAutoDelete(), "DLQ exchange should not auto-delete");
    }

    @Test
    void testMainQueueBinding_ShouldBindQueueToExchangeWithRoutingKey() {
        Queue queue = config.mainQueue();
        DirectExchange exchange = config.mainExchange();
        Binding binding = config.mainQueueBinding(queue, exchange);

        assertNotNull(binding);
        assertEquals("mq.delivery.main", binding.getDestination());
        assertEquals("mq.delivery.exchange.main", binding.getExchange());
        assertEquals("mq.delivery.routing.main", binding.getRoutingKey());
    }

    @Test
    void testReceiptQueueBinding_ShouldBindQueueToExchangeWithRoutingKey() {
        Queue queue = config.receiptQueue();
        DirectExchange exchange = config.receiptExchange();
        Binding binding = config.receiptQueueBinding(queue, exchange);

        assertNotNull(binding);
        assertEquals("mq.delivery.receipt", binding.getDestination());
        assertEquals("mq.delivery.exchange.receipt", binding.getExchange());
        assertEquals("mq.delivery.routing.receipt", binding.getRoutingKey());
    }

    @Test
    void testDlqBinding_ShouldBindQueueToExchangeWithRoutingKey() {
        Queue queue = config.deadLetterQueue();
        DirectExchange exchange = config.dlqExchange();
        Binding binding = config.dlqBinding(queue, exchange);

        assertNotNull(binding);
        assertEquals("mq.delivery.dlq", binding.getDestination());
        assertEquals("mq.delivery.exchange.dlq", binding.getExchange());
        assertEquals("mq.delivery.routing.dlq", binding.getRoutingKey());
    }

    @Test
    void testAllQueues_ShouldHaveDifferentNames() {
        Queue mainQueue = config.mainQueue();
        Queue receiptQueue = config.receiptQueue();
        Queue dlq = config.deadLetterQueue();

        assertNotEquals(mainQueue.getName(), receiptQueue.getName());
        assertNotEquals(mainQueue.getName(), dlq.getName());
        assertNotEquals(receiptQueue.getName(), dlq.getName());
    }

    @Test
    void testAllExchanges_ShouldHaveDifferentNames() {
        DirectExchange mainExchange = config.mainExchange();
        DirectExchange receiptExchange = config.receiptExchange();
        DirectExchange dlqExchange = config.dlqExchange();

        assertNotEquals(mainExchange.getName(), receiptExchange.getName());
        assertNotEquals(mainExchange.getName(), dlqExchange.getName());
        assertNotEquals(receiptExchange.getName(), dlqExchange.getName());
    }
}

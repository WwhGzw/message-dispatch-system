package com.msg.delivery.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msg.delivery.dto.DeliveryResult;
import com.msg.delivery.dto.DeliveryStatus;
import com.msg.delivery.dto.Message;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for HttpDeliveryClient
 * 
 * Tests the component in a Spring context to verify proper bean wiring
 * and configuration.
 * 
 * @author MQ Delivery System
 */
@SpringBootTest
@TestPropertySource(properties = {
        "mq.queue.main=test.main.queue",
        "mq.queue.receipt=test.receipt.queue",
        "mq.queue.dlq=test.dlq.queue"
})
class HttpDeliveryClientIntegrationTest {
    
    @Autowired
    private HttpDeliveryClient httpDeliveryClient;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Test
    void contextLoads() {
        assertThat(httpDeliveryClient).isNotNull();
        assertThat(objectMapper).isNotNull();
    }
    
    @Test
    void testDeliverToInvalidUrl() {
        // Given: Message with invalid/unreachable URL
        Message message = Message.builder()
                .messageId("test-msg-001")
                .destinationUrl("http://invalid-host-that-does-not-exist-12345.com/api")
                .payload("{\"test\":\"data\"}")
                .retryCount(0)
                .maxRetries(5)
                .build();
        
        // When: Attempt delivery
        DeliveryResult result = httpDeliveryClient.deliver(message);
        
        // Then: Should return connection error or timeout
        assertThat(result).isNotNull();
        assertThat(result.getMessageId()).isEqualTo("test-msg-001");
        assertThat(result.getStatus()).isIn(DeliveryStatus.CONNECTION_ERROR, DeliveryStatus.TIMEOUT);
        assertThat(result.getErrorMessage()).isNotNull();
        assertThat(result.getLatencyMs()).isGreaterThanOrEqualTo(0);
    }
}

package com.msg.delivery.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msg.delivery.dto.DeliveryResult;
import com.msg.delivery.dto.DeliveryStatus;
import com.msg.delivery.dto.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Unit tests for HttpDeliveryClient
 * 
 * Tests HTTP delivery functionality including:
 * - Successful delivery with 2xx responses
 * - HTTP error handling (4xx, 5xx)
 * - Timeout handling
 * - Connection error handling
 * - Receipt extraction
 * - Header inclusion
 * - Latency measurement
 * 
 * @author MQ Delivery System
 */
@ExtendWith(MockitoExtension.class)
class HttpDeliveryClientTest {
    
    private HttpDeliveryClient httpDeliveryClient;
    private MockRestServiceServer mockServer;
    private ObjectMapper objectMapper;
    private RestTemplate restTemplate;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        restTemplate = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
        
        mockServer = MockRestServiceServer.createServer(restTemplate);
        
        // Create HttpDeliveryClient with a custom RestTemplateBuilder that returns our mocked RestTemplate
        RestTemplateBuilder builder = new RestTemplateBuilder() {
            @Override
            public RestTemplate build() {
                return restTemplate;
            }
        };
        
        httpDeliveryClient = new HttpDeliveryClient(builder, objectMapper);
    }
    
    @Test
    void testDeliverSuccess_WithReceipt() {
        // Given
        Message message = Message.builder()
                .messageId("msg-001")
                .destinationUrl("http://downstream.example.com/api/messages")
                .payload("{\"data\":\"test payload\"}")
                .retryCount(0)
                .maxRetries(5)
                .build();
        
        String responseBody = "{\"success\":true,\"receipt\":{\"receiptId\":\"rcpt-001\",\"timestamp\":\"2024-01-01T10:00:00\"}}";
        
        mockServer.expect(requestTo("http://downstream.example.com/api/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Message-Id", "msg-001"))
                .andExpect(header("X-Timestamp", org.hamcrest.Matchers.notNullValue()))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().string("{\"data\":\"test payload\"}"))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
        
        // When
        DeliveryResult result = httpDeliveryClient.deliver(message);
        
        // Then
        mockServer.verify();
        assertThat(result).isNotNull();
        assertThat(result.getMessageId()).isEqualTo("msg-001");
        assertThat(result.getHttpStatus()).isEqualTo(200);
        assertThat(result.getStatus()).isEqualTo(DeliveryStatus.SUCCESS);
        assertThat(result.getResponseBody()).isEqualTo(responseBody);
        assertThat(result.getLatencyMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.getReceipt()).isNotNull();
        assertThat(result.getReceipt().getMessageId()).isEqualTo("msg-001");
        assertThat(result.getReceipt().getReceiptData()).contains("receiptId");
    }
    
    @Test
    void testDeliverSuccess_WithoutReceipt() {
        // Given
        Message message = Message.builder()
                .messageId("msg-002")
                .destinationUrl("http://downstream.example.com/api/messages")
                .payload("{\"data\":\"test payload\"}")
                .build();
        
        String responseBody = "{\"success\":true}";
        
        mockServer.expect(requestTo("http://downstream.example.com/api/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
        
        // When
        DeliveryResult result = httpDeliveryClient.deliver(message);
        
        // Then
        mockServer.verify();
        assertThat(result.getStatus()).isEqualTo(DeliveryStatus.SUCCESS);
        assertThat(result.getHttpStatus()).isEqualTo(200);
        assertThat(result.getReceipt()).isNull();
    }
    
    @Test
    void testDeliverSuccess_201Created() {
        // Given
        Message message = Message.builder()
                .messageId("msg-003")
                .destinationUrl("http://downstream.example.com/api/messages")
                .payload("{\"data\":\"test payload\"}")
                .build();
        
        mockServer.expect(requestTo("http://downstream.example.com/api/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"success\":true}"));
        
        // When
        DeliveryResult result = httpDeliveryClient.deliver(message);
        
        // Then
        mockServer.verify();
        assertThat(result.getStatus()).isEqualTo(DeliveryStatus.SUCCESS);
        assertThat(result.getHttpStatus()).isEqualTo(201);
    }
    
    @Test
    void testDeliverHttpError_400BadRequest() {
        // Given
        Message message = Message.builder()
                .messageId("msg-004")
                .destinationUrl("http://downstream.example.com/api/messages")
                .payload("{\"data\":\"invalid payload\"}")
                .build();
        
        mockServer.expect(requestTo("http://downstream.example.com/api/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withBadRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"Invalid payload\"}"));
        
        // When
        DeliveryResult result = httpDeliveryClient.deliver(message);
        
        // Then
        mockServer.verify();
        assertThat(result.getStatus()).isEqualTo(DeliveryStatus.HTTP_ERROR);
        assertThat(result.getHttpStatus()).isEqualTo(400);
        assertThat(result.getErrorMessage()).isNotNull();
        assertThat(result.getResponseBody()).contains("Invalid payload");
    }
    
    @Test
    void testDeliverHttpError_404NotFound() {
        // Given
        Message message = Message.builder()
                .messageId("msg-005")
                .destinationUrl("http://downstream.example.com/api/messages")
                .payload("{\"data\":\"test payload\"}")
                .build();
        
        mockServer.expect(requestTo("http://downstream.example.com/api/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        
        // When
        DeliveryResult result = httpDeliveryClient.deliver(message);
        
        // Then
        mockServer.verify();
        assertThat(result.getStatus()).isEqualTo(DeliveryStatus.HTTP_ERROR);
        assertThat(result.getHttpStatus()).isEqualTo(404);
    }
    
    @Test
    void testDeliverHttpError_500InternalServerError() {
        // Given
        Message message = Message.builder()
                .messageId("msg-006")
                .destinationUrl("http://downstream.example.com/api/messages")
                .payload("{\"data\":\"test payload\"}")
                .build();
        
        mockServer.expect(requestTo("http://downstream.example.com/api/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"Internal server error\"}"));
        
        // When
        DeliveryResult result = httpDeliveryClient.deliver(message);
        
        // Then
        mockServer.verify();
        assertThat(result.getStatus()).isEqualTo(DeliveryStatus.HTTP_ERROR);
        assertThat(result.getHttpStatus()).isEqualTo(500);
        assertThat(result.getResponseBody()).contains("Internal server error");
    }
    
    @Test
    void testDeliverHttpError_503ServiceUnavailable() {
        // Given
        Message message = Message.builder()
                .messageId("msg-007")
                .destinationUrl("http://downstream.example.com/api/messages")
                .payload("{\"data\":\"test payload\"}")
                .build();
        
        mockServer.expect(requestTo("http://downstream.example.com/api/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        
        // When
        DeliveryResult result = httpDeliveryClient.deliver(message);
        
        // Then
        mockServer.verify();
        assertThat(result.getStatus()).isEqualTo(DeliveryStatus.HTTP_ERROR);
        assertThat(result.getHttpStatus()).isEqualTo(503);
    }
    
    @Test
    void testDeliverConnectionError() {
        // Given
        Message message = Message.builder()
                .messageId("msg-008")
                .destinationUrl("http://downstream.example.com/api/messages")
                .payload("{\"data\":\"test payload\"}")
                .build();
        
        mockServer.expect(requestTo("http://downstream.example.com/api/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withException(new java.net.ConnectException("Connection refused")));
        
        // When
        DeliveryResult result = httpDeliveryClient.deliver(message);
        
        // Then
        assertThat(result.getStatus()).isIn(DeliveryStatus.CONNECTION_ERROR, DeliveryStatus.TIMEOUT);
        assertThat(result.getHttpStatus()).isNull();
        assertThat(result.getErrorMessage()).isNotNull();
    }
    
    @Test
    void testDeliverTimeout() {
        // Given
        Message message = Message.builder()
                .messageId("msg-009")
                .destinationUrl("http://downstream.example.com/api/messages")
                .payload("{\"data\":\"test payload\"}")
                .build();
        
        mockServer.expect(requestTo("http://downstream.example.com/api/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withException(new java.net.SocketTimeoutException("Read timed out")));
        
        // When
        DeliveryResult result = httpDeliveryClient.deliver(message);
        
        // Then
        assertThat(result.getStatus()).isIn(DeliveryStatus.TIMEOUT, DeliveryStatus.CONNECTION_ERROR);
        assertThat(result.getHttpStatus()).isNull();
        assertThat(result.getErrorMessage()).contains("timed out");
    }
    
    @Test
    void testDeliverWithInvalidJsonReceipt() {
        // Given
        Message message = Message.builder()
                .messageId("msg-010")
                .destinationUrl("http://downstream.example.com/api/messages")
                .payload("{\"data\":\"test payload\"}")
                .build();
        
        String responseBody = "invalid json {";
        
        mockServer.expect(requestTo("http://downstream.example.com/api/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
        
        // When
        DeliveryResult result = httpDeliveryClient.deliver(message);
        
        // Then
        mockServer.verify();
        assertThat(result.getStatus()).isEqualTo(DeliveryStatus.SUCCESS);
        assertThat(result.getReceipt()).isNull(); // Receipt extraction should fail gracefully
    }
    
    @Test
    void testDeliverWithEmptyResponse() {
        // Given
        Message message = Message.builder()
                .messageId("msg-011")
                .destinationUrl("http://downstream.example.com/api/messages")
                .payload("{\"data\":\"test payload\"}")
                .build();
        
        mockServer.expect(requestTo("http://downstream.example.com/api/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());
        
        // When
        DeliveryResult result = httpDeliveryClient.deliver(message);
        
        // Then
        mockServer.verify();
        assertThat(result.getStatus()).isEqualTo(DeliveryStatus.SUCCESS);
        assertThat(result.getReceipt()).isNull();
    }
    
    @Test
    void testDeliverLatencyMeasurement() {
        // Given
        Message message = Message.builder()
                .messageId("msg-012")
                .destinationUrl("http://downstream.example.com/api/messages")
                .payload("{\"data\":\"test payload\"}")
                .build();
        
        mockServer.expect(requestTo("http://downstream.example.com/api/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));
        
        // When
        DeliveryResult result = httpDeliveryClient.deliver(message);
        
        // Then
        mockServer.verify();
        assertThat(result.getLatencyMs()).isNotNull();
        assertThat(result.getLatencyMs()).isGreaterThanOrEqualTo(0);
    }
    
    @Test
    void testDeliverHeadersIncluded() {
        // Given
        Message message = Message.builder()
                .messageId("msg-013")
                .destinationUrl("http://downstream.example.com/api/messages")
                .payload("{\"data\":\"test payload\"}")
                .build();
        
        mockServer.expect(requestTo("http://downstream.example.com/api/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Message-Id", "msg-013"))
                .andExpect(header("X-Timestamp", org.hamcrest.Matchers.notNullValue()))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess());
        
        // When
        httpDeliveryClient.deliver(message);
        
        // Then
        mockServer.verify();
    }
}

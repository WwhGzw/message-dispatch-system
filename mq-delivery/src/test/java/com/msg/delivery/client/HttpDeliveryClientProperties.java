package com.msg.delivery.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msg.delivery.dto.DeliveryResult;
import com.msg.delivery.dto.Message;
import net.jqwik.api.*;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Property-Based Tests for HttpDeliveryClient
 * 
 * Uses jqwik for property-based testing to verify correctness properties
 * across a wide range of randomly generated inputs.
 * 
 * @author MQ Delivery System
 */
class HttpDeliveryClientProperties {
    
    /**
     * **Validates: Requirements 3.5**
     * 
     * Property 5: HTTP Request Headers
     * 
     * For any message delivered to a downstream channel, the HTTP POST request 
     * should include X-Message-Id header containing the message identifier and 
     * X-Timestamp header containing the current timestamp.
     * 
     * This property test generates random messages with varying:
     * - Message IDs (alphanumeric strings)
     * - Destination URLs (valid HTTP/HTTPS URLs)
     * - Payloads (JSON strings)
     * 
     * For each generated message, it verifies that:
     * 1. X-Message-Id header is present and matches the message ID
     * 2. X-Timestamp header is present and contains a valid timestamp
     * 3. Content-Type header is set to application/json
     */
    @Property(tries = 100)
    @Label("Property 5: HTTP Request Headers - All requests must include X-Message-Id and X-Timestamp headers")
    void httpRequestHeadersProperty(
            @ForAll("validMessages") Message message
    ) {
        // Given: HttpDeliveryClient with mocked RestTemplate
        ObjectMapper objectMapper = new ObjectMapper();
        RestTemplate restTemplate = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
        
        MockRestServiceServer mockServer = MockRestServiceServer.createServer(restTemplate);
        
        RestTemplateBuilder builder = new RestTemplateBuilder() {
            @Override
            public RestTemplate build() {
                return restTemplate;
            }
        };
        
        HttpDeliveryClient client = new HttpDeliveryClient(builder, objectMapper);
        
        // Expect: HTTP request with required headers
        mockServer.expect(requestTo(message.getDestinationUrl()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Message-Id", message.getMessageId()))
                .andExpect(header("X-Timestamp", org.hamcrest.Matchers.notNullValue()))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));
        
        // When: Deliver message
        DeliveryResult result = client.deliver(message);
        
        // Then: Verify request was made with correct headers
        mockServer.verify();
        
        // Additional verification: Result should be successful
        assertThat(result).isNotNull();
        assertThat(result.getMessageId()).isEqualTo(message.getMessageId());
    }
    
    /**
     * Arbitrary generator for valid messages
     * 
     * Generates messages with:
     * - Random alphanumeric message IDs (8-32 characters)
     * - Valid HTTP/HTTPS destination URLs
     * - Random JSON payloads
     * - Retry count 0-5
     * - Max retries 5
     */
    @Provide
    Arbitrary<Message> validMessages() {
        Arbitrary<String> messageIds = Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(8)
                .ofMaxLength(32)
                .map(s -> "msg-" + s);
        
        Arbitrary<String> destinationUrls = Arbitraries.of(
                "http://downstream.example.com/api/messages",
                "https://downstream.example.com/api/messages",
                "http://api.downstream.com/webhook",
                "https://api.downstream.com/webhook",
                "http://localhost:8080/api/messages",
                "https://service.example.org/receive"
        );
        
        Arbitrary<String> payloads = Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(10)
                .ofMaxLength(100)
                .map(s -> "{\"data\":\"" + s + "\"}");
        
        Arbitrary<Integer> retryCounts = Arbitraries.integers().between(0, 5);
        
        return Combinators.combine(messageIds, destinationUrls, payloads, retryCounts)
                .as((msgId, url, payload, retryCount) -> 
                        Message.builder()
                                .messageId(msgId)
                                .destinationUrl(url)
                                .payload(payload)
                                .retryCount(retryCount)
                                .maxRetries(5)
                                .build()
                );
    }
    
    /**
     * Property: Latency Measurement
     * 
     * For any message delivery (successful or failed), the delivery result
     * should include a non-negative latency measurement in milliseconds.
     */
    @Property(tries = 50)
    @Label("Latency measurement should always be non-negative")
    void latencyMeasurementProperty(
            @ForAll("validMessages") Message message
    ) {
        // Given: HttpDeliveryClient with mocked RestTemplate
        ObjectMapper objectMapper = new ObjectMapper();
        RestTemplate restTemplate = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
        
        MockRestServiceServer mockServer = MockRestServiceServer.createServer(restTemplate);
        
        RestTemplateBuilder builder = new RestTemplateBuilder() {
            @Override
            public RestTemplate build() {
                return restTemplate;
            }
        };
        
        HttpDeliveryClient client = new HttpDeliveryClient(builder, objectMapper);
        
        // Expect: HTTP request
        mockServer.expect(requestTo(message.getDestinationUrl()))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));
        
        // When: Deliver message
        DeliveryResult result = client.deliver(message);
        
        // Then: Latency should be non-negative
        assertThat(result.getLatencyMs()).isNotNull();
        assertThat(result.getLatencyMs()).isGreaterThanOrEqualTo(0L);
    }
    
    /**
     * Property: Message ID Consistency
     * 
     * For any message delivery, the delivery result should contain the same
     * message ID as the input message.
     */
    @Property(tries = 50)
    @Label("Delivery result should preserve message ID")
    void messageIdConsistencyProperty(
            @ForAll("validMessages") Message message
    ) {
        // Given: HttpDeliveryClient with mocked RestTemplate
        ObjectMapper objectMapper = new ObjectMapper();
        RestTemplate restTemplate = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
        
        MockRestServiceServer mockServer = MockRestServiceServer.createServer(restTemplate);
        
        RestTemplateBuilder builder = new RestTemplateBuilder() {
            @Override
            public RestTemplate build() {
                return restTemplate;
            }
        };
        
        HttpDeliveryClient client = new HttpDeliveryClient(builder, objectMapper);
        
        // Expect: HTTP request
        mockServer.expect(requestTo(message.getDestinationUrl()))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));
        
        // When: Deliver message
        DeliveryResult result = client.deliver(message);
        
        // Then: Message ID should be preserved
        assertThat(result.getMessageId()).isEqualTo(message.getMessageId());
    }
}

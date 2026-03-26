package com.msg.delivery.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msg.delivery.dto.DeliveryResult;
import com.msg.delivery.dto.DeliveryStatus;
import com.msg.delivery.dto.Message;
import com.msg.delivery.dto.Receipt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * HTTP Delivery Client
 * 
 * Responsible for delivering messages to downstream channels via HTTP POST.
 * Configures RestTemplate with connection timeout 5s and read timeout 30s.
 * Adds X-Message-Id and X-Timestamp headers to all requests.
 * Returns DeliveryResult with HTTP status, response body, and latency.
 * Handles timeouts and connection errors.
 * 
 * Requirements: 3.2, 3.5, 3.6
 * 
 * @author MQ Delivery System
 */
@Slf4j
@Component
public class HttpDeliveryClient {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    /**
     * Constructor with RestTemplate configuration
     * 
     * @param restTemplateBuilder RestTemplate builder for configuration
     * @param objectMapper Object mapper for JSON processing
     */
    public HttpDeliveryClient(RestTemplateBuilder restTemplateBuilder, ObjectMapper objectMapper) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = objectMapper;
    }
    
    /**
     * Deliver message to downstream channel
     * 
     * HTTP Configuration:
     *   - Connection timeout: 5 seconds
     *   - Read timeout: 30 seconds
     *   - Method: POST
     *   - Headers: X-Message-Id, X-Timestamp, Content-Type: application/json
     * 
     * @param message Message to deliver
     * @return DeliveryResult containing HTTP status, response body, and receipt
     * 
     * Preconditions:
     *   - message.destinationUrl is valid HTTP/HTTPS URL
     * 
     * Postconditions:
     *   - HTTP request sent to destination URL
     *   - Returns result with HTTP status code
     *   - If status 200-299, extracts receipt from response body
     *   - If timeout or connection error, returns TIMEOUT or CONNECTION_ERROR status
     */
    public DeliveryResult deliver(Message message) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Prepare headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Message-Id", message.getMessageId());
            headers.set("X-Timestamp", String.valueOf(System.currentTimeMillis()));
            
            // Prepare request entity
            HttpEntity<String> requestEntity = new HttpEntity<>(message.getPayload(), headers);
            
            // Send HTTP POST request
            ResponseEntity<String> response = restTemplate.exchange(
                    message.getDestinationUrl(),
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );
            
            long latency = System.currentTimeMillis() - startTime;
            
            // Extract receipt if present
            Optional<Receipt> receipt = extractReceipt(response.getBody(), message.getMessageId());
            
            // Determine delivery status based on HTTP status code
            DeliveryStatus status = determineStatus(response.getStatusCode());
            
            return DeliveryResult.builder()
                    .messageId(message.getMessageId())
                    .httpStatus(response.getStatusCode().value())
                    .responseBody(response.getBody())
                    .status(status)
                    .latencyMs(latency)
                    .receipt(receipt.orElse(null))
                    .build();
                    
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            // HTTP 4xx or 5xx errors
            long latency = System.currentTimeMillis() - startTime;
            
            log.warn("HTTP error delivering message {}: {} {}", 
                    message.getMessageId(), e.getStatusCode(), e.getMessage());
            
            return DeliveryResult.builder()
                    .messageId(message.getMessageId())
                    .httpStatus(e.getStatusCode().value())
                    .responseBody(e.getResponseBodyAsString())
                    .status(DeliveryStatus.HTTP_ERROR)
                    .errorMessage(e.getMessage())
                    .latencyMs(latency)
                    .build();
                    
        } catch (ResourceAccessException e) {
            // Timeout or connection errors
            long latency = System.currentTimeMillis() - startTime;
            
            log.warn("Connection/timeout error delivering message {}: {}", 
                    message.getMessageId(), e.getMessage());
            
            // Determine if it's a timeout or connection error
            DeliveryStatus status = e.getMessage() != null && 
                    (e.getMessage().contains("timeout") || e.getMessage().contains("timed out"))
                    ? DeliveryStatus.TIMEOUT
                    : DeliveryStatus.CONNECTION_ERROR;
            
            return DeliveryResult.builder()
                    .messageId(message.getMessageId())
                    .status(status)
                    .errorMessage(e.getMessage())
                    .latencyMs(latency)
                    .build();
                    
        } catch (Exception e) {
            // Unexpected errors
            long latency = System.currentTimeMillis() - startTime;
            
            log.error("Unexpected error delivering message {}: {}", 
                    message.getMessageId(), e.getMessage(), e);
            
            return DeliveryResult.builder()
                    .messageId(message.getMessageId())
                    .status(DeliveryStatus.CONNECTION_ERROR)
                    .errorMessage(e.getMessage())
                    .latencyMs(latency)
                    .build();
        }
    }
    
    /**
     * Determine delivery status based on HTTP status code
     * 
     * @param statusCode HTTP status code
     * @return DeliveryStatus
     */
    private DeliveryStatus determineStatus(HttpStatus statusCode) {
        if (statusCode.is2xxSuccessful()) {
            return DeliveryStatus.SUCCESS;
        } else {
            return DeliveryStatus.HTTP_ERROR;
        }
    }
    
    /**
     * Extract receipt from HTTP response body
     * 
     * Expected Response Format:
     *   {
     *     "success": true,
     *     "receipt": {
     *       "receiptId": "...",
     *       "timestamp": "...",
     *       "data": { ... }
     *     }
     *   }
     * 
     * @param responseBody HTTP response body (JSON)
     * @param messageId Original message identifier
     * @return Optional<Receipt> containing receipt if present
     * 
     * Preconditions:
     *   - responseBody may be null
     * 
     * Postconditions:
     *   - Returns Optional.of(receipt) if receipt present and valid JSON
     *   - Returns Optional.empty() if receipt not present or invalid JSON
     *   - Does not throw exception on parse error
     */
    private Optional<Receipt> extractReceipt(String responseBody, String messageId) {
        if (responseBody == null || responseBody.isEmpty()) {
            return Optional.empty();
        }
        
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.has("receipt")) {
                JsonNode receiptNode = root.get("receipt");
                Receipt receipt = Receipt.builder()
                        .messageId(messageId)
                        .receiptData(receiptNode.toString())
                        .receiptTime(LocalDateTime.now())
                        .build();
                return Optional.of(receipt);
            }
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to extract receipt from response for message {}: {}", 
                    messageId, e.getMessage());
            return Optional.empty();
        }
    }
}

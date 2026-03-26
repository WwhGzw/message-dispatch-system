# Implementation Plan: MQ Delivery System

## Overview

This implementation plan breaks down the MQ Delivery System into discrete coding tasks. The system is a high-availability message queue wrapper built on RabbitMQ that provides reliable message delivery with receipt tracking, automatic retry with exponential backoff, and comprehensive monitoring capabilities.

The implementation follows a layered approach: data layer first, then core message processing, retry logic, management interfaces, and finally integration and monitoring.

## Tasks

- [x] 1. Set up project structure and dependencies
  - Create Maven module structure for mq-delivery service
  - Add dependencies: Spring Boot, RabbitMQ, MySQL (MyBatis-Plus), Redis, Dubbo
  - Configure application.yml with RabbitMQ, MySQL, and Redis connection properties
  - Set up logging configuration
  - _Requirements: All (foundation)_

- [x] 2. Implement data layer and entities
  - [x] 2.1 Create MessageEntity and database table
    - Create MessageEntity class with all fields (messageId, destinationUrl, payload, status, retryCount, maxRetries, failureReason, timestamps)
    - Create SQL migration script for t_mq_message table with indexes
    - Create MessageMapper interface for MyBatis-Plus
    - _Requirements: 2.5, 3.4, 5.6, 6.2_
  
  - [x] 2.2 Create DeliveryAttemptEntity and database table
    - Create DeliveryAttemptEntity class with all fields (messageId, attemptNumber, httpStatus, responseBody, deliveryResult, errorMessage, attemptTime, latencyMs)
    - Create SQL migration script for t_mq_delivery_attempt table with indexes
    - Create DeliveryAttemptMapper interface
    - _Requirements: 12.2, 12.5_
  
  - [x] 2.3 Create ReceiptEntity and database table
    - Create ReceiptEntity class with all fields (messageId, receiptData, createTime, consumed, consumeTime)
    - Create SQL migration script for t_mq_receipt table with indexes
    - Create ReceiptMapper interface
    - _Requirements: 4.2, 4.3_
  
  - [ ]* 2.4 Write unit tests for entity mappings
    - Test CRUD operations for all entities
    - Test index usage for common queries
    - _Requirements: 2.5, 12.2, 4.2_

- [x] 3. Implement RabbitMQ configuration and publisher
  - [x] 3.1 Create RabbitMQ queue configuration
    - Define main queue, receipt queue, and dead letter queue as durable queues
    - Configure queue properties (TTL, max length) as per design
    - Create RabbitMQConfig class with queue, exchange, and binding beans
    - _Requirements: 2.1, 2.2, 4.4, 6.4_
  
  - [x] 3.2 Implement RabbitMQPublisher component
    - Implement publishToMainQueue with persistent delivery mode and publisher confirms
    - Implement publishToReceiptQueue for receipt forwarding
    - Implement publishToDeadLetterQueue with failure metadata
    - Handle QueuePublishException for publish failures
    - _Requirements: 2.1, 2.3, 2.4, 4.2, 6.1_
  
  - [ ]* 3.3 Write property test for message persistence
    - **Property 4: Message Persistence to Durable Queue**
    - **Validates: Requirements 2.1, 2.3**
    - Generate random messages, publish to queue, verify persistent delivery mode and durable queue configuration
  
  - [ ]* 3.4 Write unit tests for RabbitMQPublisher
    - Test successful publish to main queue
    - Test publish failure handling
    - Test receipt queue publishing
    - Test DLQ publishing with metadata
    - _Requirements: 2.1, 2.3, 2.4, 4.2, 6.1_

- [~] 4. Implement Redis distributed lock manager
  - [-] 4.1 Create DistributedLockManager component
    - Implement tryAcquire using Redis SET NX EX command with Lua script
    - Implement release using Lua script to verify ownership before deletion
    - Configure lock key format: "lock:message:{messageId}"
    - Set lock TTL to 60 seconds
    - _Requirements: 11.1, 11.2, 11.4_
  
  - [ ]* 4.2 Write property test for distributed lock lifecycle
    - **Property 14: Distributed Lock Lifecycle**
    - **Validates: Requirements 11.1, 11.2, 11.4**
    - Generate random message IDs, acquire locks, verify TTL set, release locks, verify cleanup
  
  - [ ]* 4.3 Write unit tests for lock operations
    - Test successful lock acquisition
    - Test failed acquisition (already locked)
    - Test lock release by owner
    - Test lock release by non-owner (should not delete)
    - Test lock expiration after TTL
    - _Requirements: 11.1, 11.2, 11.3, 11.4_

- [~] 5. Implement message validation
  - [~] 5.1 Create MessageValidator component
    - Validate payload not null
    - Validate payload size <= 1MB (1048576 bytes)
    - Validate messageId not null and not empty
    - Validate destinationUrl matches HTTP/HTTPS URL pattern
    - Validate destinationUrl length <= 2048 characters
    - Throw ValidationException with specific error codes
    - _Requirements: 1.4, 14.1, 14.2, 14.3, 14.4, 14.5_
  
  - [ ]* 5.2 Write property test for invalid message rejection
    - **Property 2: Invalid Message Rejection**
    - **Validates: Requirements 1.3, 1.4, 14.1, 14.2, 14.3, 14.4, 14.5**
    - Generate random invalid messages (null payload, oversized, invalid URL, missing fields), verify all rejected with error codes
  
  - [ ]* 5.3 Write unit tests for validation rules
    - Test valid message acceptance
    - Test null payload rejection
    - Test oversized payload rejection
    - Test invalid URL rejection
    - Test missing required fields rejection
    - _Requirements: 14.1, 14.2, 14.3, 14.4, 14.5_

- [~] 6. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [~] 7. Implement Dubbo RPC interface
  - [~] 7.1 Create MessageRpcService interface and implementation
    - Define MessageSubmitRequest and MessageSubmitResponse DTOs
    - Implement submitMessage method with @DubboService annotation
    - Call MessageValidator to validate request
    - Persist message to database with status PENDING
    - Publish message to RabbitMQ main queue
    - Return unique message identifier within 100ms
    - Handle ValidationException and SystemException
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 2.1, 2.5_
  
  - [ ]* 7.2 Write property test for message submission round trip
    - **Property 1: Message Submission Round Trip**
    - **Validates: Requirements 1.2, 1.5, 2.5**
    - Generate random valid messages, submit via RPC, verify persisted with PENDING status and unique ID returned within 100ms
  
  - [ ]* 7.3 Write unit tests for RPC service
    - Test successful message submission
    - Test validation failure handling
    - Test database failure handling
    - Test RabbitMQ failure handling
    - Test response time within 100ms
    - _Requirements: 1.2, 1.3, 1.4, 1.5, 2.4_

- [~] 8. Implement HTTP delivery client
  - [~] 8.1 Create HttpDeliveryClient component
    - Configure RestTemplate with connection timeout 5s and read timeout 30s
    - Implement deliver method to send HTTP POST to destination URL
    - Add X-Message-Id and X-Timestamp headers to all requests
    - Return DeliveryResult with HTTP status, response body, and latency
    - Handle timeouts and connection errors
    - _Requirements: 3.2, 3.5, 3.6_
  
  - [ ]* 8.2 Write property test for HTTP request headers
    - **Property 5: HTTP Request Headers**
    - **Validates: Requirements 3.5**
    - Generate random messages, deliver to mock downstream, verify all requests contain X-Message-Id and X-Timestamp headers
  
  - [ ]* 8.3 Write unit tests for HTTP delivery
    - Test successful delivery (200 response)
    - Test timeout handling
    - Test connection error handling
    - Test header inclusion
    - Test latency measurement
    - _Requirements: 3.2, 3.5, 3.6_

- [~] 9. Implement receipt processing
  - [~] 9.1 Create ReceiptProcessor component
    - Implement extractReceipt method to parse JSON response body
    - Implement processReceipt method to publish receipt to receipt queue
    - Associate receipt with original message identifier
    - Update message status to DELIVERED in database
    - Record delivery timestamp
    - _Requirements: 4.1, 4.2, 4.3, 3.4_
  
  - [ ]* 9.2 Write property test for receipt processing round trip
    - **Property 6: Receipt Processing Round Trip**
    - **Validates: Requirements 4.1, 4.2, 4.3**
    - Generate random receipts in HTTP responses, verify extracted, published to receipt queue, and associated with correct message IDs
  
  - [ ]* 9.3 Write unit tests for receipt extraction
    - Test valid receipt extraction
    - Test missing receipt handling
    - Test invalid JSON handling
    - Test null response body handling
    - _Requirements: 4.1_

- [~] 10. Implement exponential backoff retry scheduler
  - [~] 10.1 Create RetryScheduler component
    - Implement calculateBackoffDelay using formula: min(1 * 2^(n-1), 300)
    - Implement scheduleRetry method to handle retry logic
    - For attempts < 5: calculate delay and republish to main queue after delay
    - For attempts >= 5: publish to DLQ and update status to DEAD_LETTER
    - Record retry history in database
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 6.1, 6.2, 6.3_
  
  - [ ]* 10.2 Write property test for exponential backoff calculation
    - **Property 9: Exponential Backoff Calculation**
    - **Validates: Requirements 5.2, 5.3**
    - Generate random attempt numbers (1-10), verify backoff delay follows formula min(1 * 2^(n-1), 300)
  
  - [ ]* 10.3 Write property test for retry scheduling
    - **Property 7: Retry Scheduling for Failures**
    - **Validates: Requirements 5.1, 5.3, 5.5, 5.6**
    - Generate random messages, simulate 5xx responses or timeouts, verify retry scheduled with exponential backoff and message not acknowledged until final outcome
  
  - [ ]* 10.4 Write unit tests for retry logic
    - Test backoff calculation for attempts 1-5
    - Test max delay capping at 300 seconds
    - Test retry scheduling for transient failures
    - Test DLQ move after max retries
    - _Requirements: 5.1, 5.2, 5.3, 5.4_

- [~] 11. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [~] 12. Implement message delivery consumer
  - [~] 12.1 Create MessageDeliveryConsumer component
    - Implement handleMessage method with @RabbitListener and manual acknowledgment mode
    - Try to acquire distributed lock for message ID
    - If lock not acquired: nack with requeue=true
    - If lock acquired: call HttpDeliveryClient to deliver message
    - Handle success (200-299): process receipt, update status to DELIVERED, ack message
    - Handle transient failure (500-599, timeout): schedule retry, do not ack
    - Handle permanent failure (400-499): move to DLQ, update status to FAILED, ack message
    - Always release lock after processing
    - Record delivery attempt in database
    - _Requirements: 3.1, 3.3, 3.4, 5.5, 5.6, 6.5, 11.1, 11.3, 11.4, 12.2_
  
  - [ ]* 12.2 Write property test for successful delivery completion
    - **Property 3: Successful Delivery Completion**
    - **Validates: Requirements 3.3, 3.4**
    - Generate random messages, simulate 2xx responses, verify message acknowledged, status updated to DELIVERED, and timestamp recorded
  
  - [ ]* 12.3 Write property test for dead letter queue after max retries
    - **Property 8: Dead Letter Queue After Max Retries**
    - **Validates: Requirements 5.4, 6.1, 6.2, 6.3, 6.5**
    - Generate random messages, simulate 5 consecutive failures, verify message moved to DLQ with status DEAD_LETTER, failure reason, and retry history
  
  - [ ]* 12.4 Write property test for lock contention handling
    - **Property 15: Lock Contention Handling**
    - **Validates: Requirements 11.3**
    - Generate random messages, simulate lock already held, verify message negatively acknowledged with requeue=true
  
  - [ ]* 12.5 Write property test for delivery attempt persistence
    - **Property 17: Delivery Attempt Persistence**
    - **Validates: Requirements 12.2, 12.5**
    - Generate random delivery attempts (success and failures), verify all persisted with complete details
  
  - [ ]* 12.6 Write integration tests for consumer
    - Test complete success flow with mock downstream
    - Test retry flow with transient failures
    - Test DLQ flow with max retries exceeded
    - Test lock contention with multiple consumers
    - _Requirements: 3.1, 3.3, 5.1, 6.1, 11.3_

- [~] 13. Implement message query service
  - [~] 13.1 Create MessageQueryService
    - Implement queryByMessageId to retrieve message with delivery history
    - Implement queryByStatus with pagination support
    - Implement queryByTimeRange with pagination support
    - Ensure query response time <= 2 seconds for up to 1000 matches
    - Support pagination with max 100 results per page
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6_
  
  - [ ]* 13.2 Write property test for query response time
    - **Property 10: Query Response Time**
    - **Validates: Requirements 7.4, 7.5**
    - Generate random query requests matching up to 1000 messages, verify results returned within 2 seconds with all required fields
  
  - [ ]* 13.3 Write unit tests for query operations
    - Test query by message ID (found and not found)
    - Test query by status with pagination
    - Test query by time range with pagination
    - Test pagination with > 100 results
    - _Requirements: 7.1, 7.2, 7.3, 7.6_

- [~] 14. Implement message resend service
  - [~] 14.1 Create MessageResendService
    - Implement resendMessage for single message resend
    - Acquire distributed lock before resend
    - Verify message status is FAILED or DEAD_LETTER
    - Update status to PENDING and reset retry count to 0
    - Republish message to main queue
    - Release lock after completion
    - Throw MessageLockedException if lock cannot be acquired
    - Implement batchResend for up to 100 messages
    - Process each message independently in batch
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6_
  
  - [ ]* 14.2 Write property test for message resend state reset
    - **Property 11: Message Resend State Reset**
    - **Validates: Requirements 8.2, 8.3, 8.4**
    - Generate random FAILED/DEAD_LETTER messages, resend them, verify status reset to PENDING, retry count reset to 0, and republished to queue
  
  - [ ]* 14.3 Write property test for concurrent resend prevention
    - **Property 12: Concurrent Resend Prevention**
    - **Validates: Requirements 8.5**
    - Generate random locked messages, attempt resend, verify fails with MessageLockedException without modifying state
  
  - [ ]* 14.4 Write property test for batch resend independence
    - **Property 13: Batch Resend Independence**
    - **Validates: Requirements 8.6**
    - Generate random batches of message IDs (some valid, some invalid, some locked), verify each processed independently and result counts accurate
  
  - [ ]* 14.5 Write unit tests for resend operations
    - Test successful single message resend
    - Test resend with lock acquisition failure
    - Test resend with invalid status
    - Test batch resend with mixed results
    - _Requirements: 8.1, 8.2, 8.5, 8.6_

- [~] 15. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [~] 16. Implement statistics service
  - [~] 16.1 Create StatisticsService
    - Implement calculateThroughput: count messages in time window / window duration in seconds
    - Implement calculateAverageLatency: average of (deliveryTime - createTime) for delivered messages
    - Implement calculateFailureRate: (failed attempts / total attempts) * 100
    - Implement getCurrentQueueDepths: query RabbitMQ for current message counts
    - Support time windows: 1, 5, and 15 minutes
    - _Requirements: 9.1, 9.2, 9.3, 9.4_
  
  - [ ]* 16.2 Write property test for statistics calculation accuracy
    - **Property 18: Statistics Calculation Accuracy**
    - **Validates: Requirements 9.1, 9.2, 9.3**
    - Generate random message sets with varying timestamps and statuses, verify throughput, latency, and failure rate calculations match expected formulas
  
  - [ ]* 16.3 Write property test for queue depth accuracy
    - **Property 19: Queue Depth Accuracy**
    - **Validates: Requirements 9.4**
    - Publish random number of messages to queues, verify reported depths match actual RabbitMQ counts
  
  - [ ]* 16.4 Write unit tests for statistics calculations
    - Test throughput calculation with various message counts
    - Test average latency calculation
    - Test failure rate calculation
    - Test edge case: zero messages in window
    - Test queue depth retrieval
    - _Requirements: 9.1, 9.2, 9.3, 9.4_

- [~] 17. Implement configuration manager
  - [~] 17.1 Create ConfigurationManager service
    - Implement updatePrefetchCount with validation (1-1000)
    - Implement updateConcurrency with validation (1-100)
    - Implement updateMaxRetries with validation (1-10)
    - Implement updateBackoffParameters with validation (initial 1-60, max 60-600, initial < max)
    - Store configuration in Redis for persistence
    - Apply configuration changes within 30 seconds without restart
    - Throw InvalidConfigException for invalid values
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6_
  
  - [ ]* 17.2 Write property test for configuration validation
    - **Property 20: Configuration Validation**
    - **Validates: Requirements 10.6**
    - Generate random configuration values (valid and invalid), verify invalid values rejected with validation errors
  
  - [ ]* 17.3 Write property test for configuration hot reload
    - **Property 21: Configuration Hot Reload**
    - **Validates: Requirements 10.5**
    - Generate random valid configuration updates, verify applied within 30 seconds and subsequent operations use new values
  
  - [ ]* 17.4 Write unit tests for configuration operations
    - Test valid configuration updates
    - Test invalid prefetch count rejection
    - Test invalid concurrency rejection
    - Test invalid max retries rejection
    - Test invalid backoff parameters rejection
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.6_

- [~] 18. Implement health check service
  - [~] 18.1 Create HealthCheckService
    - Implement performHealthCheck method
    - Verify RabbitMQ connectivity (try to get channel)
    - Verify MySQL connectivity (execute simple query)
    - Verify Redis connectivity (execute PING command)
    - Return HTTP 200 with HEALTHY status if all components pass
    - Return HTTP 503 with UNHEALTHY status and failing component details if any fails
    - Ensure response time <= 1 second
    - _Requirements: 13.1, 13.2, 13.3, 13.4, 13.5, 13.6_
  
  - [ ]* 18.2 Write property test for health check component verification
    - **Property 22: Health Check Component Verification**
    - **Validates: Requirements 13.1, 13.2, 13.3, 13.4, 13.5**
    - Test with various component states, verify correct HTTP status and details returned
  
  - [ ]* 18.3 Write property test for health check response time
    - **Property 23: Health Check Response Time**
    - **Validates: Requirements 13.6**
    - Execute health checks, verify all respond within 1 second regardless of component health
  
  - [ ]* 18.4 Write unit tests for health check
    - Test all components healthy
    - Test RabbitMQ failure
    - Test MySQL failure
    - Test Redis failure
    - Test response time within 1 second
    - _Requirements: 13.1, 13.2, 13.3, 13.4, 13.5, 13.6_

- [~] 19. Implement graceful shutdown handler
  - [~] 19.1 Create GracefulShutdownHandler component
    - Implement handleShutdown method with @PreDestroy annotation
    - Stop accepting new messages via RPC interface
    - Wait up to 60 seconds for in-flight messages to complete
    - For incomplete messages: nack with requeue=true and release locks
    - Close connections to RabbitMQ, MySQL, and Redis
    - Log shutdown summary (completed count, incomplete count)
    - _Requirements: 15.1, 15.2, 15.3, 15.4, 15.5, 15.6_
  
  - [ ]* 19.2 Write integration test for graceful shutdown
    - Submit multiple messages
    - Start processing
    - Send shutdown signal
    - Verify in-flight messages complete or negatively acknowledged
    - Verify connections closed
    - Verify shutdown summary logged
    - _Requirements: 15.1, 15.2, 15.3, 15.4, 15.5, 15.6_

- [~] 20. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [~] 21. Implement management backend REST API
  - [~] 21.1 Create MessageController for query operations
    - Implement GET /api/messages/{messageId} endpoint
    - Implement GET /api/messages endpoint with status and time range query parameters
    - Implement pagination support with page and size parameters
    - Return message details with delivery history
    - _Requirements: 7.1, 7.2, 7.3, 7.6_
  
  - [~] 21.2 Create MessageController for resend operations
    - Implement POST /api/messages/{messageId}/resend endpoint
    - Implement POST /api/messages/batch-resend endpoint with message ID list
    - Return resend result with success/failure status
    - Handle MessageLockedException with HTTP 409
    - _Requirements: 8.1, 8.5, 8.6_
  
  - [~] 21.3 Create StatisticsController for monitoring
    - Implement GET /api/statistics/throughput endpoint with time window parameter
    - Implement GET /api/statistics/latency endpoint with time window parameter
    - Implement GET /api/statistics/failure-rate endpoint with time window parameter
    - Implement GET /api/statistics/queue-depths endpoint
    - _Requirements: 9.1, 9.2, 9.3, 9.4_
  
  - [~] 21.4 Create ConfigurationController for configuration management
    - Implement PUT /api/config/prefetch-count endpoint
    - Implement PUT /api/config/concurrency endpoint
    - Implement PUT /api/config/max-retries endpoint
    - Implement PUT /api/config/backoff-parameters endpoint
    - Handle InvalidConfigException with HTTP 400
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.6_
  
  - [~] 21.5 Create HealthController for health check
    - Implement GET /health endpoint
    - Return HTTP 200 for healthy, HTTP 503 for unhealthy
    - Include component health details in response
    - _Requirements: 13.1, 13.5_
  
  - [ ]* 21.6 Write integration tests for REST API
    - Test all query endpoints
    - Test resend endpoints
    - Test statistics endpoints
    - Test configuration endpoints
    - Test health check endpoint
    - _Requirements: 7.1, 8.1, 9.1, 10.1, 13.1_

- [~] 22. Implement validation failure logging
  - [~] 22.1 Add logging to MessageValidator
    - Log all validation failures with message ID, validation error, and timestamp
    - Use appropriate log level (WARN or ERROR)
    - Include structured logging for auditing
    - _Requirements: 14.6_
  
  - [ ]* 22.2 Write property test for validation failure logging
    - **Property 24: Validation Failure Logging**
    - **Validates: Requirements 14.6**
    - Generate random invalid messages, verify validation failures logged with message details

- [~] 23. Implement message identifier consistency
  - [~] 23.1 Ensure message ID consistency across redeliveries
    - Verify HttpDeliveryClient uses same message ID for all delivery attempts
    - Verify message ID included in all HTTP requests
    - Verify message ID stored in all delivery attempt records
    - _Requirements: 12.1, 12.3_
  
  - [ ]* 23.2 Write property test for message identifier consistency
    - **Property 16: Message Identifier Consistency**
    - **Validates: Requirements 12.1, 12.3**
    - Generate random messages, deliver multiple times (simulate redelivery), verify same message ID used in all delivery attempts

- [~] 24. Implement error handling for all failure scenarios
  - [~] 24.1 Add error handling for RabbitMQ connection failure
    - Catch QueuePublishException in RPC service
    - Return error code MQ_UNAVAILABLE to upstream
    - Rollback database transaction
    - Log error with message details
    - _Requirements: 2.4_
  
  - [~] 24.2 Add error handling for MySQL connection failure
    - Catch DataAccessException in RPC service
    - Return error code DB_UNAVAILABLE to upstream
    - Rollback RabbitMQ publish
    - Log error with message details
    - _Requirements: 1.5_
  
  - [~] 24.3 Add error handling for Redis connection failure
    - Catch RedisConnectionException in consumer
    - Nack message with requeue=true
    - Log warning with message ID
    - _Requirements: 11.3_
  
  - [ ]* 24.4 Write integration tests for error scenarios
    - Test RabbitMQ connection failure during publish
    - Test MySQL connection failure during persist
    - Test Redis connection failure during lock acquisition
    - Test downstream timeout handling
    - Test downstream 4xx error handling
    - Test downstream 5xx error handling
    - _Requirements: 2.4, 1.5, 11.3, 3.6, 5.1_

- [~] 25. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 26. Integration and end-to-end testing
  - [ ]* 26.1 Write end-to-end integration tests
    - Test complete success flow: RPC submission → delivery → receipt → status update
    - Test retry and recovery flow: failure → retry with backoff → success
    - Test DLQ flow: 5 failures → move to DLQ
    - Test distributed lock coordination with multiple consumers
    - Test graceful shutdown with in-flight messages
    - Test management backend operations: query, resend, statistics, configuration
    - Use Testcontainers for RabbitMQ, MySQL, and Redis
    - Use MockWebServer for downstream HTTP endpoints
    - _Requirements: All (end-to-end validation)_

- [ ] 27. Performance testing and optimization
  - [ ]* 27.1 Conduct performance testing
    - Test throughput: submit 10,000 messages, measure messages/second (target ≥ 1000)
    - Test latency: measure P95 end-to-end latency (target ≤ 500ms)
    - Test query performance: query 1000 messages (target ≤ 2 seconds)
    - Test health check performance: verify response time ≤ 1 second
    - Use JMeter or Gatling for load testing
    - _Requirements: 9.1, 9.2, 7.4, 13.6_

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation at reasonable breaks
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- Integration tests validate component interactions and end-to-end flows
- The implementation uses Java with Spring Boot, RabbitMQ, MySQL (MyBatis-Plus), Redis, and Dubbo
- All property-based tests use jqwik library with minimum 100 iterations
- Database migrations should be created using Flyway or Liquibase
- Configuration should support both application.yml and environment variables

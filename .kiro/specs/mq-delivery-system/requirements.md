# Requirements Document

## Introduction

The Message Queue Delivery System is a high-availability message queue wrapper built on RabbitMQ that provides reliable message delivery with receipt tracking. The system accepts messages from upstream systems via RPC (Dubbo), persists them in RabbitMQ, delivers them to downstream channels via HTTP callbacks, and processes receipts back to upstream systems. It ensures reliability through message persistence, acknowledgment mechanisms, automatic retry with exponential backoff, and dead letter queue handling.

## Glossary

- **MQ_Delivery_System**: The complete message queue delivery system being specified
- **Upstream_System**: External systems that send messages to the MQ_Delivery_System via Dubbo RPC
- **Downstream_Channel**: External HTTP endpoints that receive messages from the MQ_Delivery_System
- **RPC_Interface**: The Dubbo-based remote procedure call interface for upstream integration
- **Message**: A data payload sent from Upstream_System to Downstream_Channel
- **Receipt**: An acknowledgment response from Downstream_Channel confirming message delivery
- **Message_Queue**: RabbitMQ instance used for message persistence and routing
- **HTTP_Delivery_Client**: Component that delivers messages to Downstream_Channel via HTTP
- **Receipt_Queue**: RabbitMQ queue dedicated to forwarding receipts to Upstream_System
- **Dead_Letter_Queue**: RabbitMQ queue for messages that failed after all retry attempts
- **Message_Store**: MySQL database for persistent message storage
- **Retry_Scheduler**: Component that manages exponential backoff retry logic
- **Management_Backend**: Web interface for message query, resend, and monitoring
- **Distributed_Lock**: Redis-based locking mechanism for coordinating operations
- **Message_Status**: Current state of a message (PENDING, DELIVERED, FAILED, DEAD_LETTER)
- **Acknowledgment_Mode**: Manual acknowledgment mode for RabbitMQ consumers
- **Durable_Queue**: RabbitMQ queue configured to survive broker restarts

## Requirements

### Requirement 1: Message Reception via RPC

**User Story:** As an Upstream_System, I want to send messages via Dubbo RPC, so that messages are reliably accepted into the delivery system.

#### Acceptance Criteria

1. THE RPC_Interface SHALL expose a Dubbo service endpoint for message submission
2. WHEN an Upstream_System invokes the RPC endpoint with a valid message, THE RPC_Interface SHALL return a unique message identifier within 100 milliseconds
3. WHEN an Upstream_System invokes the RPC endpoint with an invalid message, THE RPC_Interface SHALL return a descriptive error code
4. THE RPC_Interface SHALL validate message payload structure before acceptance
5. WHEN a message is accepted, THE MQ_Delivery_System SHALL persist the message to Message_Store before returning success

### Requirement 2: Message Persistence to RabbitMQ

**User Story:** As the MQ_Delivery_System, I want to persist messages to RabbitMQ, so that messages survive system failures.

#### Acceptance Criteria

1. WHEN a message is accepted via RPC_Interface, THE MQ_Delivery_System SHALL publish the message to a Durable_Queue
2. THE MQ_Delivery_System SHALL configure all queues as durable to survive broker restarts
3. THE MQ_Delivery_System SHALL publish messages with persistent delivery mode
4. WHEN publishing to Message_Queue fails, THE MQ_Delivery_System SHALL return an error to the Upstream_System
5. THE MQ_Delivery_System SHALL store message metadata in Message_Store with status PENDING

### Requirement 3: Message Delivery to Downstream Channels

**User Story:** As the MQ_Delivery_System, I want to deliver messages to Downstream_Channels via HTTP, so that messages reach their intended recipients.

#### Acceptance Criteria

1. THE HTTP_Delivery_Client SHALL consume messages from Message_Queue using Acknowledgment_Mode
2. WHEN a message is consumed, THE HTTP_Delivery_Client SHALL send an HTTP POST request to the configured Downstream_Channel endpoint
3. WHEN the Downstream_Channel returns HTTP status 200-299, THE HTTP_Delivery_Client SHALL acknowledge the message to Message_Queue
4. WHEN the Downstream_Channel returns HTTP status 200-299, THE MQ_Delivery_System SHALL update Message_Status to DELIVERED in Message_Store
5. THE HTTP_Delivery_Client SHALL include message identifier and timestamp in the HTTP request headers
6. THE HTTP_Delivery_Client SHALL set HTTP connection timeout to 5 seconds and read timeout to 30 seconds

### Requirement 4: Receipt Processing

**User Story:** As the MQ_Delivery_System, I want to process receipts from Downstream_Channels, so that Upstream_Systems receive delivery confirmation.

#### Acceptance Criteria

1. WHEN a Downstream_Channel returns a receipt in the HTTP response body, THE HTTP_Delivery_Client SHALL extract the receipt data
2. WHEN a receipt is extracted, THE MQ_Delivery_System SHALL publish the receipt to Receipt_Queue
3. THE MQ_Delivery_System SHALL associate each receipt with the original message identifier
4. THE Receipt_Queue SHALL be configured as a Durable_Queue
5. THE MQ_Delivery_System SHALL provide a mechanism for Upstream_System to consume receipts from Receipt_Queue

### Requirement 5: Automatic Retry with Exponential Backoff

**User Story:** As the MQ_Delivery_System, I want to automatically retry failed deliveries, so that transient failures do not result in message loss.

#### Acceptance Criteria

1. WHEN the Downstream_Channel returns HTTP status 500-599 or times out, THE Retry_Scheduler SHALL schedule a retry attempt
2. THE Retry_Scheduler SHALL implement exponential backoff with initial delay of 1 second
3. THE Retry_Scheduler SHALL double the delay after each failed attempt up to a maximum of 300 seconds
4. THE Retry_Scheduler SHALL attempt delivery up to 5 times before moving to Dead_Letter_Queue
5. WHILE a message is being retried, THE MQ_Delivery_System SHALL not acknowledge the message to Message_Queue
6. WHEN a retry succeeds, THE MQ_Delivery_System SHALL acknowledge the message and update Message_Status to DELIVERED

### Requirement 6: Dead Letter Queue Handling

**User Story:** As the MQ_Delivery_System, I want to move permanently failed messages to a Dead_Letter_Queue, so that they can be investigated and manually reprocessed.

#### Acceptance Criteria

1. WHEN a message fails after 5 retry attempts, THE MQ_Delivery_System SHALL publish the message to Dead_Letter_Queue
2. WHEN a message is moved to Dead_Letter_Queue, THE MQ_Delivery_System SHALL update Message_Status to DEAD_LETTER in Message_Store
3. THE MQ_Delivery_System SHALL store failure reason and retry history with dead letter messages
4. THE Dead_Letter_Queue SHALL be configured as a Durable_Queue
5. THE MQ_Delivery_System SHALL acknowledge the original message to Message_Queue after moving to Dead_Letter_Queue

### Requirement 7: Message Query Interface

**User Story:** As a system administrator, I want to query messages by various criteria, so that I can monitor and troubleshoot message delivery.

#### Acceptance Criteria

1. THE Management_Backend SHALL provide a query interface for messages by message identifier
2. THE Management_Backend SHALL provide a query interface for messages by Message_Status
3. THE Management_Backend SHALL provide a query interface for messages by time range
4. WHEN a query is executed, THE Management_Backend SHALL return results within 2 seconds for up to 1000 matching messages
5. THE Management_Backend SHALL display message identifier, status, timestamps, retry count, and failure reason for each message
6. THE Management_Backend SHALL support pagination for query results exceeding 100 messages

### Requirement 8: Message Resend Capability

**User Story:** As a system administrator, I want to manually resend failed messages, so that I can recover from delivery failures after fixing downstream issues.

#### Acceptance Criteria

1. THE Management_Backend SHALL provide a resend function for messages with Message_Status FAILED or DEAD_LETTER
2. WHEN a resend is requested, THE MQ_Delivery_System SHALL acquire a Distributed_Lock for the message identifier
3. WHEN a resend is requested, THE MQ_Delivery_System SHALL publish the message back to Message_Queue
4. WHEN a message is resent, THE MQ_Delivery_System SHALL update Message_Status to PENDING and reset retry count to zero
5. WHEN a resend is requested for a message that is currently being processed, THE MQ_Delivery_System SHALL return an error indicating the message is locked
6. THE Management_Backend SHALL support batch resend for up to 100 messages simultaneously

### Requirement 9: Monitoring and Statistics

**User Story:** As a system administrator, I want to view system statistics, so that I can monitor system health and performance.

#### Acceptance Criteria

1. THE Management_Backend SHALL display message throughput as messages per second over the last 1 minute, 5 minutes, and 15 minutes
2. THE Management_Backend SHALL display average delivery latency over the last 1 minute, 5 minutes, and 15 minutes
3. THE Management_Backend SHALL display failure rate as percentage of failed deliveries over the last 1 minute, 5 minutes, and 15 minutes
4. THE Management_Backend SHALL display current queue depths for Message_Queue, Receipt_Queue, and Dead_Letter_Queue
5. THE Management_Backend SHALL refresh statistics automatically every 10 seconds
6. THE Management_Backend SHALL display statistics for the last 24 hours in graphical format

### Requirement 10: Queue Configuration Management

**User Story:** As a system administrator, I want to configure queue parameters, so that I can optimize system performance for different workloads.

#### Acceptance Criteria

1. THE Management_Backend SHALL provide an interface to configure queue prefetch count
2. THE Management_Backend SHALL provide an interface to configure consumer concurrency level
3. THE Management_Backend SHALL provide an interface to configure retry attempt limits
4. THE Management_Backend SHALL provide an interface to configure exponential backoff parameters
5. WHEN configuration is updated, THE MQ_Delivery_System SHALL apply changes within 30 seconds without requiring restart
6. THE Management_Backend SHALL validate configuration values before applying changes

### Requirement 11: Distributed Lock for Concurrent Operations

**User Story:** As the MQ_Delivery_System, I want to use distributed locks, so that concurrent operations on the same message are prevented.

#### Acceptance Criteria

1. WHEN processing a message, THE MQ_Delivery_System SHALL acquire a Distributed_Lock using the message identifier as the key
2. THE Distributed_Lock SHALL have a timeout of 60 seconds to prevent deadlocks
3. WHEN a Distributed_Lock cannot be acquired, THE MQ_Delivery_System SHALL skip processing and requeue the message
4. WHEN message processing completes, THE MQ_Delivery_System SHALL release the Distributed_Lock
5. THE MQ_Delivery_System SHALL use Redis for Distributed_Lock implementation

### Requirement 12: Message Idempotency

**User Story:** As the MQ_Delivery_System, I want to ensure idempotent message delivery, so that duplicate deliveries do not cause issues.

#### Acceptance Criteria

1. THE MQ_Delivery_System SHALL include a unique message identifier in every HTTP delivery request
2. THE MQ_Delivery_System SHALL store delivery attempts with timestamps in Message_Store
3. WHEN a message is redelivered due to acknowledgment failure, THE HTTP_Delivery_Client SHALL include the same message identifier
4. THE MQ_Delivery_System SHALL provide documentation to Downstream_Channels on implementing idempotent message handling
5. THE Management_Backend SHALL display all delivery attempts for each message including timestamps and responses

### Requirement 13: System Health Monitoring

**User Story:** As a system administrator, I want to monitor system health, so that I can detect and respond to issues proactively.

#### Acceptance Criteria

1. THE MQ_Delivery_System SHALL expose a health check endpoint that returns HTTP 200 when all components are operational
2. THE MQ_Delivery_System SHALL verify Message_Queue connectivity as part of health check
3. THE MQ_Delivery_System SHALL verify Message_Store connectivity as part of health check
4. THE MQ_Delivery_System SHALL verify Redis connectivity as part of health check
5. WHEN any component fails health check, THE MQ_Delivery_System SHALL return HTTP 503 with details of the failing component
6. THE health check endpoint SHALL respond within 1 second

### Requirement 14: Message Payload Validation

**User Story:** As the MQ_Delivery_System, I want to validate message payloads, so that invalid messages are rejected early.

#### Acceptance Criteria

1. THE RPC_Interface SHALL validate that message payload is not null
2. THE RPC_Interface SHALL validate that message payload size does not exceed 1 megabyte
3. THE RPC_Interface SHALL validate that required fields (message identifier, destination URL, payload) are present
4. THE RPC_Interface SHALL validate that destination URL is a valid HTTP or HTTPS URL
5. WHEN validation fails, THE RPC_Interface SHALL return a specific error code indicating the validation failure reason
6. THE MQ_Delivery_System SHALL log all validation failures with message details for auditing

### Requirement 15: Graceful Shutdown

**User Story:** As a system administrator, I want the system to shut down gracefully, so that in-flight messages are not lost during deployment or maintenance.

#### Acceptance Criteria

1. WHEN a shutdown signal is received, THE MQ_Delivery_System SHALL stop accepting new messages via RPC_Interface
2. WHEN a shutdown signal is received, THE MQ_Delivery_System SHALL complete processing of all in-flight messages
3. THE MQ_Delivery_System SHALL wait up to 60 seconds for in-flight messages to complete before forcing shutdown
4. WHEN forcing shutdown, THE MQ_Delivery_System SHALL negatively acknowledge all incomplete messages to Message_Queue
5. THE MQ_Delivery_System SHALL log shutdown progress including count of completed and incomplete messages
6. WHEN shutdown completes, THE MQ_Delivery_System SHALL close all connections to Message_Queue, Message_Store, and Redis


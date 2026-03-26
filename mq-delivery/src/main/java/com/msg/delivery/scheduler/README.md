# RetryScheduler Component

## Overview

The `RetryScheduler` component manages exponential backoff retry logic for failed message deliveries in the MQ Delivery System. It implements automatic retry scheduling with exponentially increasing delays and moves messages to the dead letter queue after maximum retry attempts.

## Responsibilities

- Calculate exponential backoff delays using the formula: `min(1 * 2^(n-1), 300)` seconds
- Schedule retry attempts with appropriate delays for failed deliveries
- Republish messages to the main queue for retry (attempts < 5)
- Move messages to the dead letter queue after max retries (attempts >= 5)
- Record retry history in the database for audit and monitoring

## Key Features

### Exponential Backoff Calculation

The component implements exponential backoff with the following delay progression:

| Attempt | Delay (seconds) | Formula |
|---------|----------------|---------|
| 1       | 1              | min(1 * 2^0, 300) |
| 2       | 2              | min(1 * 2^1, 300) |
| 3       | 4              | min(1 * 2^2, 300) |
| 4       | 8              | min(1 * 2^3, 300) |
| 5       | 16             | min(1 * 2^4, 300) |
| 10      | 300 (capped)   | min(1 * 2^9, 300) |

### Retry Logic

- **Attempts 1-4**: Message is scheduled for retry with exponential backoff delay
- **Attempt 5+**: Message is moved to the dead letter queue with status `DEAD_LETTER`

### Database Recording

Every retry attempt is recorded in the `t_mq_delivery_attempt` table with:
- Message ID
- Attempt number
- Delivery result
- Error message
- Attempt timestamp

## Usage

### Basic Usage

```java
@Autowired
private RetryScheduler retryScheduler;

// Schedule retry for a failed delivery
Message message = Message.builder()
    .messageId("msg-123")
    .destinationUrl("https://example.com/webhook")
    .payload("{\"data\":\"test\"}")
    .retryCount(0)
    .maxRetries(5)
    .build();

retryScheduler.scheduleRetry(message, 1, "Connection timeout");
```

### Calculate Backoff Delay

```java
// Calculate delay for specific attempt
int delay = retryScheduler.calculateBackoffDelay(3); // Returns 4 seconds
```

## Configuration

### Constants

- `INITIAL_DELAY_SECONDS`: 1 second (initial retry delay)
- `MAX_DELAY_SECONDS`: 300 seconds (maximum retry delay cap)
- `MAX_RETRY_ATTEMPTS`: 5 (maximum number of retry attempts before DLQ)

### Thread Pool

The component uses a `ScheduledExecutorService` with 10 threads to handle concurrent retry scheduling.

## Dependencies

- **RabbitMQPublisher**: Publishes messages to main queue and dead letter queue
- **MessageMapper**: Updates message status and retry count in database
- **DeliveryAttemptMapper**: Records delivery attempt history

## Error Handling

The component handles errors gracefully:

- **Database failures**: Logged but do not prevent retry scheduling
- **Publish failures**: Logged but do not throw exceptions
- **Invalid inputs**: Throw `IllegalArgumentException` with descriptive messages

## Testing

### Unit Tests

Located in `RetrySchedulerTest.java`:
- Exponential backoff calculation for all attempt numbers
- Retry scheduling logic for attempts < 5
- Dead letter queue handling for attempts >= 5
- Input validation and error handling

### Property-Based Tests

Located in `RetrySchedulerProperties.java`:
- Property 9: Exponential backoff calculation formula
- Property 7: Retry scheduling for failures
- Property 8: Dead letter queue after max retries
- Backoff delay monotonicity
- Retry attempt recording consistency

### Integration Tests

Located in `RetrySchedulerIntegrationTest.java`:
- Complete retry flow with scheduled task execution
- Multiple retries in sequence
- Database and publish failure handling

## Requirements Validation

This component validates the following requirements:

- **Requirement 5.1**: Automatic retry for 500-599 status codes and timeouts
- **Requirement 5.2**: Exponential backoff with initial delay of 1 second
- **Requirement 5.3**: Delay doubles after each attempt, capped at 300 seconds
- **Requirement 5.4**: Maximum 5 retry attempts before DLQ
- **Requirement 6.1**: Publish to dead letter queue after max retries
- **Requirement 6.2**: Update status to DEAD_LETTER
- **Requirement 6.3**: Store failure reason and retry history

## Performance Considerations

- Uses scheduled executor service for non-blocking retry scheduling
- Database operations are asynchronous and do not block retry scheduling
- Thread pool size (10) can be adjusted based on system load
- Retry history retrieval is optimized with indexed queries

## Graceful Shutdown

The component provides a `shutdown()` method for graceful termination:

```java
retryScheduler.shutdown();
```

This method:
1. Stops accepting new retry schedules
2. Waits up to 60 seconds for in-flight tasks to complete
3. Forces shutdown if tasks don't complete in time
4. Logs shutdown summary

## Future Enhancements

Potential improvements for future versions:

- Configurable backoff parameters (initial delay, max delay, multiplier)
- Configurable max retry attempts
- Retry priority queue for critical messages
- Metrics and monitoring integration
- Circuit breaker pattern for downstream failures

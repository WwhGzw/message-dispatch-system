package com.msg.delivery;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * MQ Delivery Application Test
 * 
 * Verifies that the Spring Boot application context loads successfully
 */
@SpringBootTest
@ActiveProfiles("test")
class MqDeliveryApplicationTest {

    @Test
    void contextLoads() {
        // This test verifies that the application context loads successfully
        // If the context fails to load, this test will fail
    }
}

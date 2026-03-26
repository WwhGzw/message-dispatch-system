package com.msg.delivery.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * HTTP Client Configuration
 * 
 * Configures RestTemplate with appropriate timeouts for HTTP delivery.
 * 
 * Configuration:
 * - Connection timeout: 5 seconds
 * - Read timeout: 30 seconds
 * 
 * @author MQ Delivery System
 */
@Configuration
public class HttpClientConfig {
    
    /**
     * RestTemplate bean with configured timeouts
     * 
     * @param builder RestTemplate builder
     * @return Configured RestTemplate
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }
}

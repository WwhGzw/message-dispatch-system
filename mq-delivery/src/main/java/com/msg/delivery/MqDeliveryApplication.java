package com.msg.delivery;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MQ Delivery System Application
 * 
 * 基于RabbitMQ的消息队列投递系统
 * 提供可靠消息投递、回执跟踪、自动重试、死信队列处理
 */
@SpringBootApplication
@EnableDubbo
@EnableScheduling
@MapperScan("com.msg.delivery.mapper")
public class MqDeliveryApplication {

    public static void main(String[] args) {
        SpringApplication.run(MqDeliveryApplication.class, args);
    }
}

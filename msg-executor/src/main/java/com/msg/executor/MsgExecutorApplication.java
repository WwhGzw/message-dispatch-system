package com.msg.executor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 渠道执行器启动类
 * 负责MQ消费、多渠道统一抽象、渠道下发
 */
@SpringBootApplication(scanBasePackages = "com.msg")
public class MsgExecutorApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsgExecutorApplication.class, args);
    }
}

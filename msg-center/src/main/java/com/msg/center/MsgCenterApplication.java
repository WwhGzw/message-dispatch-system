package com.msg.center;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 消息中心启动类
 * 负责幂等校验、路由决策、模板渲染、状态管理
 */
@SpringBootApplication(scanBasePackages = "com.msg")
@EnableScheduling
public class MsgCenterApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsgCenterApplication.class, args);
    }
}

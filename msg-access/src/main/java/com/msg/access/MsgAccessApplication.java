package com.msg.access;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 接入服务启动类
 * 通过 Dubbo RPC 对外暴露消息下发服务
 */
@SpringBootApplication(scanBasePackages = "com.msg")
@EnableDubbo
public class MsgAccessApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsgAccessApplication.class, args);
    }
}

package com.msg.access;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 接入服务启动类
 * 负责鉴权、参数校验、QPS限流
 */
@SpringBootApplication(scanBasePackages = "com.msg")
public class MsgAccessApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsgAccessApplication.class, args);
    }
}

package com.push.system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 推送系统启动类
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class PushSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(PushSystemApplication.class, args);
    }
}

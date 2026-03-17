package com.hyang.ich.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class UserProviderApplication {
    public static void main(String[] args) {
        System.setProperty("curator-dont-use-containers", "true");
        SpringApplication.run(UserProviderApplication.class, args);
        System.out.println("====================================");
        System.out.println("  用户服务提供者 (UserProvider) 启动成功!");
        System.out.println("  端口: 8081 | Dubbo端口: 20881");
        System.out.println("====================================");
    }
}


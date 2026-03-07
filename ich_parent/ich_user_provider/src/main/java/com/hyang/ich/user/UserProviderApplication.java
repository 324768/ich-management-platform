package com.hyang.ich.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UserProviderApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserProviderApplication.class, args);
        System.out.println("====================================");
        System.out.println("  用户服务提供者 (UserProvider) 启动成功!");
        System.out.println("  端口: 8081 | Dubbo端口: 20881");
        System.out.println("====================================");
    }
}


package com.hyang.ich.userweb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.hyang.ich.userweb", "com.hyang.ich.common"})
public class UserWebApplication {
    public static void main(String[] args) {
        System.setProperty("curator-dont-use-containers", "true");
        SpringApplication.run(UserWebApplication.class, args);
        System.out.println("====================================");
        System.out.println("  用户端API (UserWeb) 启动成功!");
        System.out.println("  端口: 8090");
        System.out.println("  接口地址: http://localhost:8090/api/user");
        System.out.println("====================================");
    }
}

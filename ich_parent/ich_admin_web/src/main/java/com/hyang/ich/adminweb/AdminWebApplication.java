package com.hyang.ich.adminweb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.hyang.ich.adminweb", "com.hyang.ich.common"})
public class AdminWebApplication {
    public static void main(String[] args) {
        System.setProperty("curator-dont-use-containers", "true");
        SpringApplication.run(AdminWebApplication.class, args);
        System.out.println("====================================");
        System.out.println("  管理端API (AdminWeb) 启动成功!");
        System.out.println("  端口: 8091");
        System.out.println("  接口地址: http://localhost:8091/api/admin");
        System.out.println("====================================");
    }
}

package com.hyang.ich.content;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ContentProviderApplication {
    public static void main(String[] args) {
        System.setProperty("curator-dont-use-containers", "true");
        SpringApplication.run(ContentProviderApplication.class, args);
        System.out.println("====================================");
        System.out.println("  内容服务提供者 (ContentProvider) 启动成功!");
        System.out.println("  端口: 8082 | Dubbo端口: 20882");
        System.out.println("====================================");
    }
}

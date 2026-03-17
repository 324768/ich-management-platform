package com.hyang.ich.omnitrix;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OmnitrixApplication {
    public static void main(String[] args) {
        System.setProperty("curator-dont-use-containers", "true");
        SpringApplication.run(OmnitrixApplication.class, args);
        System.out.println("====================================");
        System.out.println("  AI智能服务 (Omnitrix) 启动成功!");
        System.out.println("  端口: 8083 | Dubbo端口: 20883");
        System.out.println("====================================");
    }
}

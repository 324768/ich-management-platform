package com.hyang.ich.omnitrix.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * 测试配置
 */
@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public SimpleMeterRegistry simpleMeterRegistry() {
        return new SimpleMeterRegistry();
    }
}

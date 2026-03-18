package com.hyang.ich.omnitrix.infrastructure.llm;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Anthropic Claude 模型配置
 * 使用 LangChain4j 原生支持
 */
@Slf4j
@Configuration
public class AnthropicChatModelConfig {

    /**
     * 流式 Chat Model - 用于 SSE 流式响应
     */
    @Bean
    public AnthropicStreamingChatModel anthropicStreamingChatModel(LlmProperties properties) {
        LlmProperties.ClaudeConfig config = properties.getClaudeConfig();
        
        if (!config.isConfigured()) {
            log.warn("Claude API 未配置，AnthropicStreamingChatModel 将不可用");
            return null;
        }
        
        log.info("初始化 AnthropicStreamingChatModel: model={}", config.getModel());
        
        return AnthropicStreamingChatModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModel())
                .maxTokens(config.getMaxOutputTokens())
                .temperature(config.getTemperature())
                .timeout(java.time.Duration.ofSeconds(config.getTimeoutSeconds()))
                .build();
    }
    
    /**
     * 同步 Chat Model - 用于普通对话
     */
    @Bean
    public AnthropicChatModel anthropicChatModel(LlmProperties properties) {
        LlmProperties.ClaudeConfig config = properties.getClaudeConfig();
        
        if (!config.isConfigured()) {
            log.warn("Claude API 未配置，AnthropicChatModel 将不可用");
            return null;
        }
        
        return AnthropicChatModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModel())
                .maxTokens(config.getMaxOutputTokens())
                .temperature(config.getTemperature())
                .timeout(java.time.Duration.ofSeconds(config.getTimeoutSeconds()))
                .build();
    }
}

package com.hyang.ich.omnitrix.infrastructure.llm;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * LLM 配置属性。
 *
 * 支持多种配置模式：
 *
 * 模式1（向后兼容，单模型）：
 *   omnitrix.llm.api-url=xxx
 *   omnitrix.llm.model=xxx
 *
 * 模式2（双模型）：
 *   omnitrix.llm.primary.api-url=xxx
 *   omnitrix.llm.primary.model=xxx
 *   omnitrix.llm.auxiliary.api-url=xxx    # 辅助任务用更便宜/更快的模型
 *   omnitrix.llm.auxiliary.model=xxx
 *
 * 模式3（Claude API，多模态 + 大上下文）：
 *   omnitrix.llm.claude.enabled=true
 *   omnitrix.llm.claude.api-url=xxx
 *   omnitrix.llm.claude.model=xxx (支持200K上下文)
 *   omnitrix.llm.claude.api-key=xxx
 *
 * 上下文窗口配置：
 *   omnitrix.llm.max-context-tokens=200000 (默认200K)
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "omnitrix.llm")
public class LlmProperties {

    // ---- 向后兼容的顶层字段 ----
    private String apiUrl;
    private String model;
    private String apiKey;
    private int timeoutSeconds = 60;
    private int maxTokens = 4096;
    private double temperature = 0.7;

    /** 
     * 上下文窗口 token 上限
     * 根据使用的模型动态调整：
     * - Claude Sonnet 4: 200K tokens
     * - Claude Opus 4: 200K tokens  
     * - DeepSeek V3: 64K tokens
     * - Qwen 2.5: 32K tokens
     */
    private int maxContextTokens = 200000;

    // ---- 双模型配置 ----
    private ModelConfig primary;
    private ModelConfig auxiliary;

    // ---- Claude API 配置（多模态 + 大上下文） ----
    private ClaudeConfig claude;

    // ---- 多模态配置（图片理解） ----
    private MultimodalConfig multimodal;

    @PostConstruct
    public void init() {
        // 校验必需配置
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("❌ LLM API Key 未配置！请设置环境变量 SILICONFLOW_API_KEY");
        }

        // 如果未配置 apiUrl，使用默认值
        if (apiUrl == null || apiUrl.trim().isEmpty()) {
            apiUrl = "https://api.siliconflow.cn/v1/chat/completions";
        }
        if (model == null || model.trim().isEmpty()) {
            model = "deepseek-ai/DeepSeek-V3";
        }

        // 如果未配置 primary，从顶层字段构建（向后兼容）
        if (primary == null) {
            primary = new ModelConfig();
            primary.setApiUrl(apiUrl);
            primary.setModel(model);
            primary.setApiKey(apiKey);
            primary.setTimeoutSeconds(timeoutSeconds);
            primary.setMaxTokens(maxTokens);
            primary.setTemperature(temperature);
        }

        // 如果未配置 auxiliary，复用 primary（辅助任务使用相同模型）
        if (auxiliary == null) {
            auxiliary = new ModelConfig();
            auxiliary.setApiUrl(primary.getApiUrl());
            auxiliary.setModel(primary.getModel());
            auxiliary.setApiKey(primary.getApiKey());
            auxiliary.setTimeoutSeconds(Math.min(primary.getTimeoutSeconds(), 30));
            auxiliary.setMaxTokens(Math.min(primary.getMaxTokens(), 1024));
            auxiliary.setTemperature(0.3);
        }

        // 初始化 Claude 配置
        if (claude == null) {
            claude = new ClaudeConfig();
        }

        // 初始化多模态配置
        if (multimodal == null) {
            multimodal = new MultimodalConfig();
        }

        log.info("✅ LLM 配置初始化完成: model={}, maxTokens={}, maxContextTokens={}",
                primary.getModel(), primary.getMaxTokens(), maxContextTokens);
    }

    /** 获取 Claude 配置（多模态 + 大上下文） */
    public ClaudeConfig getClaudeConfig() {
        return claude;
    }

    /** 获取多模态配置 */
    public MultimodalConfig getMultimodal() {
        return multimodal;
    }

    /**
     * Claude API 配置类（支持200K上下文窗口）
     */
    @Data
    public static class ClaudeConfig {
        private boolean enabled = false;
        private String apiUrl = "https://api.anthropic.com/v1/messages";
        private String model = "claude-sonnet-4-20250514";
        private String apiKey;
        private int timeoutSeconds = 180;
        private int maxTokens = 8192;
        private int maxOutputTokens = 8192;
        private double temperature = 0.7;
        /** 是否启用 SystemMessage Cache（仅Claude支持，建议开启） */
        private boolean cacheSystemMessages = true;
        /** 是否启用 Tools Cache（默认关闭，有已知bug） */
        private boolean cacheTools = false;

        /** 是否已配置 */
        public boolean isConfigured() {
            return enabled && apiKey != null && !apiKey.trim().isEmpty();
        }
    }

    /**
     * 多模态模型配置（图片理解）
     */
    @Data
    public static class MultimodalConfig {
        /** 是否启用多模态 */
        private boolean enabled = false;

        /** 模型提供商：anthropic / openai */
        private String provider = "anthropic";

        /** 模型名称 */
        private String model = "claude-sonnet-4-20250514";

        /** API Key */
        private String apiKey;

        /** 超时时间（秒） */
        private int timeoutSeconds = 180;

        /** 最大输出 tokens */
        private int maxTokens = 4096;

        /** 温度参数 */
        private double temperature = 0.7;

        /** 是否已配置 */
        public boolean isConfigured() {
            return enabled && apiKey != null && !apiKey.trim().isEmpty();
        }
    }
}

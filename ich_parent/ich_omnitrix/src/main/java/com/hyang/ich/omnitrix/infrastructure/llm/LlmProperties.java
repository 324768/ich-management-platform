package com.hyang.ich.omnitrix.infrastructure.llm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * LLM 配置属性。
 *
 * 支持两种配置模式：
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
 */
@Data
@Component
@ConfigurationProperties(prefix = "omnitrix.llm")
public class LlmProperties {

    // ---- 向后兼容的顶层字段 ----
    private String apiUrl = "https://api.siliconflow.cn/v1/chat/completions";
    private String model = "deepseek-ai/DeepSeek-V3";
    private String apiKey = "sk-utgmwknrslowgyfkjtpqfllzclvzeyaevaayqqubpcmlmglw";
    private int timeoutSeconds = 120;
    private int maxTokens = 2048;
    private double temperature = 0.7;

    /** 上下文窗口 token 上限（换大模型时只改配置即可） */
    private int maxContextTokens = 3000;

    // ---- 双模型配置 ----
    private ModelConfig primary;
    private ModelConfig auxiliary;

    @PostConstruct
    public void init() {
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
            auxiliary.setMaxTokens(Math.min(primary.getMaxTokens(), 512));
            auxiliary.setTemperature(0.3); // 辅助任务用更低温度
        }
    }

    /** 获取主模型配置（主聊天） */
    public ModelConfig getPrimaryConfig() {
        return primary;
    }

    /** 获取辅助模型配置（评分/标题/意图/摘要） */
    public ModelConfig getAuxiliaryConfig() {
        return auxiliary;
    }
}

package com.hyang.ich.omnitrix.infrastructure.llm;

import lombok.Data;

/**
 * 单个模型端点的配置。
 * 可用于 primary（主聊天）或 auxiliary（评分/标题/意图分类/摘要等辅助任务）。
 */
@Data
public class ModelConfig {

    private String apiUrl;
    private String model;
    private String apiKey;
    private int timeoutSeconds = 60;
    private int maxTokens = 2048;
    private double temperature = 0.7;

    public static ModelConfig of(String apiUrl, String model) {
        ModelConfig c = new ModelConfig();
        c.setApiUrl(apiUrl);
        c.setModel(model);
        return c;
    }

    /**
     * 是否需要 Bearer 认证
     */
    public boolean hasApiKey() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }
}

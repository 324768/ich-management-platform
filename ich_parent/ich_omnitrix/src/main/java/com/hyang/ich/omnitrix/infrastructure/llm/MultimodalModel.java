package com.hyang.ich.omnitrix.infrastructure.llm;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;

/**
 * 多模态模型服务
 * 支持 Claude Vision 和 GPT-4V 进行图片理解
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "omnitrix.multimodal", name = "enabled", havingValue = "true")
public class MultimodalModel {

    private final LlmProperties llmProperties;
    private ChatLanguageModel multimodalModel;

    public MultimodalModel(LlmProperties properties) {
        this.llmProperties = properties;
        this.multimodalModel = createMultimodalModel();
    }

    /**
     * 创建多模态模型
     * 支持 Claude Vision 和 OpenAI GPT-4V
     */
    private ChatLanguageModel createMultimodalModel() {
        LlmProperties.MultimodalConfig config = llmProperties.getMultimodal();

        if (config == null) {
            log.warn("多模态配置未设置，使用默认Claude模型");
            config = new LlmProperties.MultimodalConfig();
            config.setProvider("anthropic");
            config.setModel("claude-sonnet-4-20250514");
        }

        log.info("初始化多模态模型: provider={}, model={}",
                config.getProvider(), config.getModel());

        // 根据提供商选择模型
        if ("anthropic".equalsIgnoreCase(config.getProvider())) {
            // 使用 Claude Vision
            return AnthropicChatModel.builder()
                    .apiKey(config.getApiKey())
                    .modelName(config.getModel())
                    .maxTokens(config.getMaxTokens())
                    .temperature(config.getTemperature())
                    .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                    .defaultUserMessage("You are a helpful AI assistant that can see and analyze images.")
                    .build();
        } else {
            // 使用 OpenAI GPT-4V（LangChain4j 0.35.0可能需要额外配置）
            throw new UnsupportedOperationException("OpenAI多模态模型暂未支持，请使用Claude");
        }
    }

    /**
     * 获取多模态模型实例
     */
    public ChatLanguageModel getModel() {
        return multimodalModel;
    }

    /**
     * 检查是否已配置多模态
     */
    public boolean isConfigured() {
        LlmProperties.MultimodalConfig config = llmProperties.getMultimodal();
        return config != null && config.getApiKey() != null && !config.getApiKey().isEmpty();
    }
}

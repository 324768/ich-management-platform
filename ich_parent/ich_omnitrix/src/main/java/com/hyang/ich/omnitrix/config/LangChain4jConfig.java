package com.hyang.ich.omnitrix.config;

import com.hyang.ich.omnitrix.infrastructure.llm.LlmProperties;
import com.hyang.ich.omnitrix.infrastructure.llm.ModelConfig;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

/**
 * LangChain4j 配置 — 基于现有 LlmProperties 构建 ChatLanguageModel。
 * SiliconFlow 兼容 OpenAI API 格式，使用 langchain4j-open-ai 模块即可。
 * 
 * 支持多种模型：
 * - SiliconFlow (默认): deepseek-ai/DeepSeek-V3 等
 * - Claude: claude-sonnet-4, claude-opus-4-6 等
 */
@Configuration
public class LangChain4jConfig {

    /**
     * 主聊天模型（同步）— 用于 MasterBrain 及 SystemSubAgent 内部 AiService
     * 默认使用 SiliconFlow
     */
    @Bean
    @Primary
    public ChatLanguageModel chatLanguageModel(LlmProperties props) {
        ModelConfig primary = props.getPrimaryConfig();
        return OpenAiChatModel.builder()
                .baseUrl(toBaseUrl(primary.getApiUrl()))
                .apiKey(primary.getApiKey())
                .modelName(primary.getModel())
                .maxTokens(primary.getMaxTokens())
                .temperature(primary.getTemperature())
                .timeout(Duration.ofSeconds(primary.getTimeoutSeconds()))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    /**
     * 主聊天模型（流式）— 用于 OrchestratorService SSE 推送
     * 支持动态参数调整（根据modelCode）
     */
    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel(LlmProperties props) {
        ModelConfig primary = props.getPrimaryConfig();
        return OpenAiStreamingChatModel.builder()
                .baseUrl(toBaseUrl(primary.getApiUrl()))
                .apiKey(primary.getApiKey())
                .modelName(primary.getModel())
                .maxTokens(primary.getMaxTokens())
                .temperature(primary.getTemperature())
                .timeout(Duration.ofSeconds(primary.getTimeoutSeconds()))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    /**
     * 创建指定模型的流式ChatLanguageModel（动态创建，支持大上下文）
     */
    public StreamingChatLanguageModel createStreamingModel(String modelCode, LlmProperties props) {
        // 如果是Claude模型，返回默认的流式模型（实际使用ClaudeStreamingModel）
        if (modelCode != null && modelCode.startsWith("claude-")) {
            // Claude由专门的ClaudeStreamingModel处理
            return streamingChatLanguageModel(props);
        }
        
        // SiliconFlow模型
        ModelConfig primary = props.getPrimaryConfig();
        return OpenAiStreamingChatModel.builder()
                .baseUrl(toBaseUrl(primary.getApiUrl()))
                .apiKey(primary.getApiKey())
                .modelName(primary.getModel())
                .maxTokens(primary.getMaxTokens())
                .temperature(primary.getTemperature())
                .timeout(Duration.ofSeconds(primary.getTimeoutSeconds()))
                .build();
    }

    /**
     * 辅助模型（同步）— 用于评分、标题生成、意图分类等轻量任务
     */
    @Bean("auxiliaryChatModel")
    public ChatLanguageModel auxiliaryChatModel(LlmProperties props) {
        ModelConfig aux = props.getAuxiliaryConfig();
        return OpenAiChatModel.builder()
                .baseUrl(toBaseUrl(aux.getApiUrl()))
                .apiKey(aux.getApiKey())
                .modelName(aux.getModel())
                .maxTokens(aux.getMaxTokens())
                .temperature(aux.getTemperature())
                .timeout(Duration.ofSeconds(aux.getTimeoutSeconds()))
                .build();
    }

    /**
     * SiliconFlow API URL 格式转换：
     * 输入: https://api.siliconflow.cn/v1/chat/completions
     * 输出: https://api.siliconflow.cn/v1/   (OpenAI 客户端自动拼接 chat/completions)
     */
    private String toBaseUrl(String apiUrl) {
        if (apiUrl == null) return "https://api.siliconflow.cn/v1/";
        // 去掉 /chat/completions 后缀，保留到 /v1/
        String base = apiUrl.replaceAll("/chat/completions/?$", "");
        if (!base.endsWith("/")) base += "/";
        return base;
    }
}

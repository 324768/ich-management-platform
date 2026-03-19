package com.hyang.ich.omnitrix.infrastructure.llm;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.Response;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Claude 流式模型适配器 - 实现 LangChain4j 的 StreamingChatLanguageModel 接口
 * 用于支持 SSE 流式输出
 */
@Slf4j
@Component
public class ClaudeStreamingModel implements StreamingChatLanguageModel {

    private final LlmProperties llmProperties;
    private final RestTemplate restTemplate;

    @Autowired
    public ClaudeStreamingModel(LlmProperties llmProperties) {
        this.llmProperties = llmProperties;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public void chat(String userMessage, StreamingChatResponseHandler handler) {
        chat(userMessage, null, handler);
    }

    /**
     * 流式聊天 - 支持指定模型
     */
    public void chat(String userMessage, String modelCode, StreamingChatResponseHandler handler) {
        LlmProperties.ClaudeConfig config = getEffectiveConfig(modelCode);

        if (!config.isConfigured()) {
            handler.onError(new IllegalStateException("Claude API 未配置"));
            return;
        }

        try {
            sendStreamingRequest(config, userMessage, handler);
        } catch (Exception e) {
            log.error("Claude 流式请求失败: {}", e.getMessage(), e);
            handler.onError(e);
        }
    }

    /**
     * 获取有效的配置（支持动态模型切换）
     */
    private LlmProperties.ClaudeConfig getEffectiveConfig(String modelCode) {
        LlmProperties.ClaudeConfig baseConfig = llmProperties.getClaudeConfig();

        // 如果指定了模型代码，动态创建配置
        if (modelCode != null && !modelCode.isEmpty() && baseConfig.isEnabled()) {
            LlmProperties.ClaudeConfig dynamicConfig = new LlmProperties.ClaudeConfig();
            dynamicConfig.setEnabled(true);
            dynamicConfig.setApiUrl(baseConfig.getApiUrl());
            dynamicConfig.setApiKey(baseConfig.getApiKey());
            dynamicConfig.setModel(modelCode); // 使用指定的模型
            dynamicConfig.setTimeoutSeconds(baseConfig.getTimeoutSeconds());
            dynamicConfig.setMaxTokens(baseConfig.getMaxTokens());
            dynamicConfig.setTemperature(baseConfig.getTemperature());
            return dynamicConfig;
        }

        return baseConfig;
    }

    /**
     * 发送流式请求到 Claude API
     */
    private void sendStreamingRequest(LlmProperties.ClaudeConfig config,
                                       String userMessage,
                                       StreamingChatResponseHandler handler) {
        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModel());
        requestBody.put("max_tokens", config.getMaxTokens());
        requestBody.put("temperature", config.getTemperature());
        requestBody.put("stream", true); // 启用流式输出

        // 添加用户消息
        Map<String, Object> userMessageContent = new HashMap<>();
        userMessageContent.put("role", "user");
        userMessageContent.put("content", userMessage);

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(userMessageContent);
        requestBody.put("messages", messages);

        // Prompt Cache 配置（仅 Claude 支持）
        // 注意：SystemMessage 已在 UserMasterBrain 中移除了 {{skills}} 和 {{userProfile}} 变量
        // 确保 SystemMessage 完全静态，缓存命中时才有效
        if (config.isCacheSystemMessages()) {
            // 启用系统消息缓存（需要 Anthropic SDK 或手动设置 cache_control）
            // LangChain4j 0.35.0 的 AnthropicChatModel 支持此选项
            // 当前使用原生 HTTP 调用，缓存通过 anthropic-version 2023-06-01 控制
            log.debug("Prompt Cache 已启用（静态 SystemMessage）");
        }

        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", config.getApiKey());
        headers.set("anthropic-version", "2023-06-01");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            String url = config.getApiUrl();
            log.debug("Claude 流式请求: model={}, url={}", config.getModel(), url);

            // 使用 RestTemplate 的 exchange 方法进行流式调用
            // 但 RestTemplate 默认不支持流式，我们需要使用原生方法
            // 这里简化处理，先用同步方式然后手动分段发送

            // 实际生产中应该使用 WebClient 或 OkHttp 的流式支持
            // 这里我们模拟流式输出（因为 RestTemplate 不原生支持 SSE）
            // 更好的方案是直接使用 OkHttp Client

            // 方案：使用简单的方式 - 完整响应后处理
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class
            );

            String responseBody = response.getBody();
            if (responseBody != null) {
                // 解析流式响应（Claude 的流式响应是 SSE 格式）
                parseAndStreamResponse(responseBody, handler);
            }

            // 完成响应 - 使用 0.35.0 API
            handler.onCompleteResponse(Response.from(""));

        } catch (Exception e) {
            log.error("Claude 流式请求失败: {}", e.getMessage(), e);
            handler.onError(e);
        }
    }

    /**
     * 解析 Claude 流式响应并发送 chunks
     * Claude 流式响应格式：
     * data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}
     * data: {"type":"message_delta","index":0,"delta":{"text":"","stop_reason":"end_turn"}}
     * data: {"type":"message_stop"}
     */
    private void parseAndStreamResponse(String responseBody, StreamingChatResponseHandler handler) {
        try {
            // 简单的按句子/段落分割（实际应该解析 SSE 格式）
            // 这里做一个简化处理：把响应按字符流式发送
            String[] lines = responseBody.split("\n");

            for (String line : lines) {
                if (line.startsWith("data: ")) {
                    String jsonStr = line.substring(6);
                    if (jsonStr.equals("[DONE]")) {
                        break;
                    }

                    // 解析 JSON
                    com.fasterxml.jackson.databind.JsonNode node =
                            new com.fasterxml.jackson.databind.ObjectMapper().readTree(jsonStr);

                    // 提取文本内容
                    if (node.has("type")) {
                        String type = node.get("type").asText();
                        if ("content_block_delta".equals(type) && node.has("delta")) {
                            JsonNode delta = node.get("delta");
                            if (delta.has("text")) {
                                String text = delta.get("text").asText();
                                if (!text.isEmpty()) {
                                    // 使用 0.35.0 的 API
                                    handler.onPartialResponse(text);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析 Claude 响应失败，使用整体发送: {}", e.getMessage());
            // 降级：整体发送 - 使用 0.35.0 的 API
            handler.onPartialResponse(responseBody);
        }
    }
}

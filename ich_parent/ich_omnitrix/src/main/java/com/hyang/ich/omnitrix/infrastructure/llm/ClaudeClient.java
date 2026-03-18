package com.hyang.ich.omnitrix.infrastructure.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.Base64Utils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Claude API 客户端 - 支持多模态输入（图像理解、文件理解）
 * 
 * 基于 Anthropic Claude API
 * 支持：图像理解、PDF/文档理解、音频理解等
 */
@Slf4j
@Component
public class ClaudeClient {

    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Autowired
    public ClaudeClient(LlmProperties llmProperties) {
        this.llmProperties = llmProperties;
        this.objectMapper = new ObjectMapper();
        this.restTemplate = new RestTemplate();
    }

    /**
     * 多模态理解：图像/文件 + 文本
     * 
     * @param userMessage 用户消息
     * @param attachments 附件列表（图像、PDF等）
     * @return Claude 的理解结果
     */
    public ClaudeResponse understandMultimodal(String userMessage, List<MultipartFile> attachments) {
        LlmProperties.ClaudeConfig config = llmProperties.getClaudeConfig();
        
        if (!config.isConfigured()) {
            throw new IllegalStateException("Claude API 未配置，请先配置 omnitrix.llm.claude");
        }

        // 构建多模态消息
        List<Map<String, Object>> contentBlocks = buildMultimodalContent(userMessage, attachments);
        
        return sendClaudeRequest(config, userMessage, contentBlocks);
    }

    /**
     * 仅图像理解（无文本）
     * 
     * @param imageBase64 图像的Base64编码
     * @param mimeType 图像MIME类型
     * @param systemPrompt 系统提示（可选）
     * @return 图像理解结果
     */
    public String understandImage(String imageBase64, String mimeType, String systemPrompt) {
        LlmProperties.ClaudeConfig config = llmProperties.getClaudeConfig();
        
        if (!config.isConfigured()) {
            throw new IllegalStateException("Claude API 未配置");
        }

        // 构建图像内容块
        List<Map<String, Object>> contentBlocks = new ArrayList<>();
        
        Map<String, Object> imageBlock = new HashMap<>();
        imageBlock.put("type", "image");
        imageBlock.put("source", Map.of(
            "type", "base64",
            "media_type", mimeType,
            "data", imageBase64
        ));
        contentBlocks.add(imageBlock);

        ClaudeResponse response = sendClaudeRequest(config, systemPrompt, contentBlocks);
        return response.getContent();
    }

    /**
     * 仅文件理解（PDF、文档等）
     * 
     * @param fileBytes 文件字节数组
     * @param mimeType 文件MIME类型
     * @param userQuestion 用户问题
     * @return 文件理解结果
     */
    public String understandFile(byte[] fileBytes, String mimeType, String userQuestion) {
        LlmProperties.ClaudeConfig config = llmProperties.getClaudeConfig();
        
        if (!config.isConfigured()) {
            throw new IllegalStateException("Claude API 未配置");
        }

        String base64Data = Base64Utils.encodeToString(fileBytes);
        
        // 根据文件类型选择source type
        String sourceType = getSourceType(mimeType);
        
        List<Map<String, Object>> contentBlocks = new ArrayList<>();
        
        // 根据文件类型选择处理方式
        String fileType = getFileType(mimeType);
        
        // PDF、Word、Excel、PPT等文档使用document类型
        // 纯文本、CSV、Markdown使用text类型
        // 其他默认当作图像处理
        Map<String, Object> fileBlock = new HashMap<>();
        
        if ("text".equals(fileType)) {
            // 纯文本文件直接读取内容
            String content = new String(fileBytes, java.nio.charset.StandardCharsets.UTF_8);
            fileBlock.put("type", "text");
            fileBlock.put("text", content);
        } else if ("document".equals(fileType)) {
            // 文档类型（PDF、Word、Excel、PPT等）
            fileBlock.put("type", "document");
            fileBlock.put("source", Map.of(
                "type", "base64",
                "media_type", mimeType,
                "data", base64Data
            ));
        } else {
            // 图像类型
            fileBlock.put("type", "image");
            fileBlock.put("source", Map.of(
                "type", "base64",
                "media_type", mimeType,
                "data", base64Data
            ));
        }
        contentBlocks.add(fileBlock);

        // 添加用户问题
        if (userQuestion != null && !userQuestion.isEmpty()) {
            Map<String, Object> textBlock = new HashMap<>();
            textBlock.put("type", "text");
            textBlock.put("text", userQuestion);
            contentBlocks.add(textBlock);
        }

        ClaudeResponse response = sendClaudeRequest(config, null, contentBlocks);
        return response.getContent();
    }

    /**
     * 从 MultipartFile 构建多模态内容
     */
    private List<Map<String, Object>> buildMultimodalContent(String userMessage, 
                                                               List<MultipartFile> attachments) {
        List<Map<String, Object>> contentBlocks = new ArrayList<>();
        
        // 添加文本消息
        if (userMessage != null && !userMessage.isEmpty()) {
            Map<String, Object> textBlock = new HashMap<>();
            textBlock.put("type", "text");
            textBlock.put("text", userMessage);
            contentBlocks.add(textBlock);
        }
        
        // 处理附件
        if (attachments != null && !attachments.isEmpty()) {
            for (MultipartFile file : attachments) {
                try {
                    String mimeType = file.getContentType();
                    String base64Data = Base64Utils.encodeToString(file.getBytes());
                    
                    Map<String, Object> block = new HashMap<>();
                    
                    // 根据文件类型选择处理方式
                    String fileType = getFileType(mimeType);
                    
                    if ("text".equals(fileType)) {
                        // 纯文本文件直接读取内容
                        String content = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
                        block.put("type", "text");
                        block.put("text", content);
                    } else if ("document".equals(fileType)) {
                        // 文档类型（PDF、Word、Excel、PPT等）
                        block.put("type", "document");
                        block.put("source", Map.of(
                            "type", "base64",
                            "media_type", mimeType != null ? mimeType : "application/octet-stream",
                            "data", base64Data
                        ));
                    } else {
                        // 图像类型
                        block.put("type", "image");
                        block.put("source", Map.of(
                            "type", "base64",
                            "media_type", mimeType != null ? mimeType : "application/octet-stream",
                            "data", base64Data
                        ));
                    }
                    
                    contentBlocks.add(block);
                    
                } catch (IOException e) {
                    log.error("处理附件失败: {}", file.getOriginalFilename(), e);
                }
            }
        }
        
        return contentBlocks;
    }

    /**
     * 发送 Claude API 请求
     */
    private ClaudeResponse sendClaudeRequest(LlmProperties.ClaudeConfig config, 
                                              String systemPrompt,
                                              List<Map<String, Object>> contentBlocks) {
        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModel());
        requestBody.put("max_tokens", config.getMaxTokens());
        requestBody.put("temperature", config.getTemperature());
        
        // 添加系统提示
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            List<Map<String, Object>> systemBlocks = new ArrayList<>();
            Map<String, Object> systemBlock = new HashMap<>();
            systemBlock.put("type", "text");
            systemBlock.put("text", systemPrompt);
            systemBlocks.add(systemBlock);
            requestBody.put("system", systemBlocks);
        }
        
        // 添加用户消息
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", contentBlocks);
        
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(userMessage);
        requestBody.put("messages", messages);

        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", config.getApiKey());
        headers.set("anthropic-version", "2023-06-01");
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        
        try {
            String url = config.getApiUrl();
            log.debug("调用 Claude API: model={}, url={}", config.getModel(), url);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, String.class
            );
            
            String responseBody = response.getBody();
            log.debug("Claude API 响应: {}", responseBody != null ? 
                (responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody) : "null");
            
            return parseClaudeResponse(responseBody);
            
        } catch (Exception e) {
            log.error("Claude API 调用失败: {}", e.getMessage(), e);
            throw new RuntimeException("Claude API 调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析 Claude API 响应
     */
    private ClaudeResponse parseClaudeResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            
            ClaudeResponse response = new ClaudeResponse();
            
            // 提取 content
            JsonNode contentNode = root.get("content");
            if (contentNode != null && contentNode.isArray() && contentNode.size() > 0) {
                StringBuilder content = new StringBuilder();
                for (JsonNode block : contentNode) {
                    if (block.has("text")) {
                        content.append(block.get("text").asText());
                    }
                }
                response.setContent(content.toString());
            }
            
            // 提取 usage
            if (root.has("usage")) {
                JsonNode usage = root.get("usage");
                response.setInputTokens(usage.has("input_tokens") ? usage.get("input_tokens").asInt() : 0);
                response.setOutputTokens(usage.has("output_tokens") ? usage.get("output_tokens").asInt() : 0);
            }
            
            // 提取 stop_reason
            if (root.has("stop_reason")) {
                response.setStopReason(root.get("stop_reason").asText());
            }
            
            return response;
            
        } catch (Exception e) {
            log.error("解析 Claude 响应失败: {}", e.getMessage());
            throw new RuntimeException("解析 Claude 响应失败: " + e.getMessage(), e);
        }
    }

    /**
     * 文件类型处理映射表
     * Claude API 支持的文件类型
     */
    private static final Map<String, String> FILE_TYPE_MAP = new HashMap<>();
    static {
        // 图像
        FILE_TYPE_MAP.put("image/jpeg", "image");
        FILE_TYPE_MAP.put("image/png", "image");
        FILE_TYPE_MAP.put("image/gif", "image");
        FILE_TYPE_MAP.put("image/webp", "image");
        FILE_TYPE_MAP.put("image/bmp", "image");
        FILE_TYPE_MAP.put("image/svg+xml", "image");
        
        // 文档
        FILE_TYPE_MAP.put("application/pdf", "document");
        FILE_TYPE_MAP.put("application/msword", "document");
        FILE_TYPE_MAP.put("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "document");
        FILE_TYPE_MAP.put("application/vnd.ms-word", "document");
        FILE_TYPE_MAP.put("application/rtf", "document");
        FILE_TYPE_MAP.put("text/plain", "text");
        FILE_TYPE_MAP.put("text/markdown", "text");
        FILE_TYPE_MAP.put("text/csv", "text");
        
        // 电子表格
        FILE_TYPE_MAP.put("application/vnd.ms-excel", "document");
        FILE_TYPE_MAP.put("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "document");
        FILE_TYPE_MAP.put("text/csv", "text");
        
        // 演示文稿
        FILE_TYPE_MAP.put("application/vnd.ms-powerpoint", "document");
        FILE_TYPE_MAP.put("application/vnd.openxmlformats-officedocument.presentationml.presentation", "document");
    }

    /**
     * 获取文件处理类型
     */
    private String getFileType(String mimeType) {
        if (mimeType == null) return "image"; // 默认当作图像处理
        return FILE_TYPE_MAP.getOrDefault(mimeType, "image");
    }

    /**
     * Claude 响应封装
     */
    @lombok.Data
    public static class ClaudeResponse {
        private String content;
        private int inputTokens;
        private int outputTokens;
        private String stopReason;
    }
}

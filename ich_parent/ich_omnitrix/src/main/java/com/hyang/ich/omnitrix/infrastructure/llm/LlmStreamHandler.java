package com.hyang.ich.omnitrix.infrastructure.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyang.ich.omnitrix.dto.StreamResult;
import com.hyang.ich.omnitrix.infrastructure.sse.SseEmitterManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Component
public class LlmStreamHandler {

    /** 手机号正则 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("(1[3-9]\\d)\\d{4}(\\d{4})");
    /** 身份证正则 */
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("(\\d{6})\\d{8}(\\d{4})");
    /** 流式 PII 缓冲区大小: 需足够容纳一个完整手机号/身份证号 */
    private static final int PII_BUFFER_SIZE = 20;

    private final LlmProperties properties;
    private final SseEmitterManager sseManager;
    private final LlmCircuitBreaker circuitBreaker;
    private final ObjectMapper objectMapper;

    public LlmStreamHandler(LlmProperties properties, SseEmitterManager sseManager,
                              LlmCircuitBreaker circuitBreaker) {
        this.properties = properties;
        this.sseManager = sseManager;
        this.circuitBreaker = circuitBreaker;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 使用主模型进行流式调用（默认入口）
     */
    public StreamResult streamChat(SseEmitter emitter, String systemPrompt,
                                    List<Map<String, String>> history, String userMessage) {
        return streamChatWithConfig(properties.getPrimaryConfig(), emitter, systemPrompt, history, userMessage);
    }

    /**
     * 使用指定模型配置进行流式调用
     */
    public StreamResult streamChatWithConfig(ModelConfig config, SseEmitter emitter, String systemPrompt,
                                              List<Map<String, String>> history, String userMessage) {
        // 熔断检查
        if (!circuitBreaker.allowRequest()) {
            log.warn("LLM 熔断器 OPEN，拒绝流式请求: model={}", config.getModel());
            sseManager.sendError(emitter, "AI服务暂时不可用（熔断保护中），请稍后再试");
            return StreamResult.of("", 0, 0);
        }

        // 统一构建消息列表
        List<Map<String, String>> messages = LlmMessageBuilder.build(systemPrompt, history, userMessage);
        int inputCharCount = LlmMessageBuilder.totalCharCount(messages);

        StringBuilder fullContent = new StringBuilder();
        StringBuilder thinkingContent = new StringBuilder();
        StringBuilder piiBuffer = new StringBuilder();
        boolean insideThink = false;
        StringBuilder tagDetectBuffer = new StringBuilder();
        HttpURLConnection connection = null;

        try {
            LlmRequest request = LlmRequest.of(
                    config.getModel(), messages,
                    config.getTemperature(), config.getMaxTokens(), true);

            String body = objectMapper.writeValueAsString(request);

            URL url = new URL(config.getApiUrl());
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            if (config.hasApiKey()) {
                connection.setRequestProperty("Authorization", "Bearer " + config.getApiKey());
            }
            connection.setDoOutput(true);
            connection.setConnectTimeout(config.getTimeoutSeconds() * 1000);
            connection.setReadTimeout(config.getTimeoutSeconds() * 1000);

            java.io.OutputStream os = connection.getOutputStream();
            os.write(body.getBytes("UTF-8"));
            os.flush();
            os.close();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), "UTF-8"));

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;

                if (line.startsWith("data: ")) {
                    String data = line.substring(6).trim();

                    if ("[DONE]".equals(data)) {
                        break;
                    }

                    try {
                        JsonNode node = objectMapper.readTree(data);
                        JsonNode choices = node.get("choices");
                        if (choices != null && choices.size() > 0) {
                            JsonNode delta = choices.get(0).get("delta");
                            if (delta != null && delta.has("content")) {
                                String chunk = delta.get("content").asText();

                                // 思维链标签检测与内容分流
                                tagDetectBuffer.append(chunk);
                                String buf = tagDetectBuffer.toString();

                                while (!buf.isEmpty()) {
                                    if (insideThink) {
                                        int closeIdx = buf.indexOf("</think>");
                                        if (closeIdx >= 0) {
                                            String thinkPart = buf.substring(0, closeIdx);
                                            if (!thinkPart.isEmpty()) {
                                                thinkingContent.append(thinkPart);
                                                sseManager.sendThinking(emitter, thinkPart);
                                            }
                                            insideThink = false;
                                            buf = buf.substring(closeIdx + 8);
                                        } else {
                                            // 可能 </think> 被截断，保留尾部
                                            if (buf.length() > 8) {
                                                String safe = buf.substring(0, buf.length() - 8);
                                                thinkingContent.append(safe);
                                                sseManager.sendThinking(emitter, safe);
                                                buf = buf.substring(buf.length() - 8);
                                            }
                                            break;
                                        }
                                    } else {
                                        int openIdx = buf.indexOf("<think>");
                                        if (openIdx >= 0) {
                                            String normalPart = buf.substring(0, openIdx);
                                            if (!normalPart.isEmpty()) {
                                                fullContent.append(normalPart);
                                                piiBuffer.append(normalPart);
                                                if (piiBuffer.length() >= PII_BUFFER_SIZE) {
                                                    String s = flushPiiBuffer(piiBuffer, false);
                                                    if (!s.isEmpty()) sseManager.sendChunk(emitter, s);
                                                }
                                            }
                                            insideThink = true;
                                            buf = buf.substring(openIdx + 7);
                                        } else {
                                            // 可能 <think> 被截断，保留尾部
                                            if (buf.length() > 7) {
                                                String safe = buf.substring(0, buf.length() - 7);
                                                fullContent.append(safe);
                                                piiBuffer.append(safe);
                                                if (piiBuffer.length() >= PII_BUFFER_SIZE) {
                                                    String s = flushPiiBuffer(piiBuffer, false);
                                                    if (!s.isEmpty()) sseManager.sendChunk(emitter, s);
                                                }
                                                buf = buf.substring(buf.length() - 7);
                                            }
                                            break;
                                        }
                                    }
                                }
                                tagDetectBuffer.setLength(0);
                                tagDetectBuffer.append(buf);
                            }
                        }
                    } catch (Exception e) {
                        log.debug("解析 SSE chunk 失败: {}", e.getMessage());
                    }
                }
            }

            // 刷新标签检测缓冲区残余
            if (tagDetectBuffer.length() > 0) {
                String remaining = tagDetectBuffer.toString();
                if (insideThink) {
                    thinkingContent.append(remaining);
                    sseManager.sendThinking(emitter, remaining);
                } else {
                    fullContent.append(remaining);
                    piiBuffer.append(remaining);
                }
            }

            // 刷新剩余 PII 缓冲区
            if (piiBuffer.length() > 0) {
                String safe = flushPiiBuffer(piiBuffer, true);
                if (!safe.isEmpty()) sseManager.sendChunk(emitter, safe);
            }

            reader.close();
            circuitBreaker.recordSuccess();

        } catch (Exception e) {
            circuitBreaker.recordFailure();
            log.error("流式 LLM 调用失败: model={}, error={}", config.getModel(), e.getMessage(), e);
            sseManager.sendError(emitter, "AI 服务暂时不可用");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        int estimatedInputTokens = inputCharCount / 2;
        int estimatedOutputTokens = fullContent.length() / 2;
        return StreamResult.of(fullContent.toString(), estimatedInputTokens, estimatedOutputTokens);
    }

    /**
     * 流式 PII 过滤：对缓冲区内容进行手机号/身份证号脱敏
     * @param buffer 缓冲区
     * @param flush  true=全部刷新，false=保留尾部安全边界
     */
    private String flushPiiBuffer(StringBuilder buffer, boolean flush) {
        String text = buffer.toString();
        // PII 脱敏
        text = PHONE_PATTERN.matcher(text).replaceAll("$1****$2");
        text = ID_CARD_PATTERN.matcher(text).replaceAll("$1********$2");

        if (flush) {
            buffer.setLength(0);
            return text;
        }

        // 非全量刷新时，保留尾部 PII_BUFFER_SIZE 个字符以防跨 chunk 匹配
        int safeEnd = Math.max(0, text.length() - PII_BUFFER_SIZE);
        String safe = text.substring(0, safeEnd);
        buffer.setLength(0);
        buffer.append(text.substring(safeEnd));
        return safe;
    }
}

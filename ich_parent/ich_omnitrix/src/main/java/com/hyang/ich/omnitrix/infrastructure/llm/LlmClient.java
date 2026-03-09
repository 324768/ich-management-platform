package com.hyang.ich.omnitrix.infrastructure.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class LlmClient {

    private static final int MAX_RETRIES = 2;
    private static final long RETRY_BASE_DELAY_MS = 500;
    private static final long AUX_CACHE_TTL_MS = 120_000; // 2 分钟

    private final LlmProperties properties;
    private final ObjectMapper objectMapper;
    private final LlmCircuitBreaker circuitBreaker;
    private final Map<String, RestTemplate> restTemplateCache = new ConcurrentHashMap<>();

    /** 辅助 LLM 调用缓存: key=hash(prompt+query), value=CachedResponse */
    private final Map<String, CachedResponse> auxResponseCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cacheCleanerExecutor;

    public LlmClient(LlmProperties properties, LlmCircuitBreaker circuitBreaker) {
        this.properties = properties;
        this.circuitBreaker = circuitBreaker;
        this.objectMapper = new ObjectMapper();
        // 定时清理过期缓存
        this.cacheCleanerExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "aux-cache-cleaner");
            t.setDaemon(true);
            return t;
        });
        this.cacheCleanerExecutor.scheduleAtFixedRate(this::evictExpiredCache, 60, 60, TimeUnit.SECONDS);
    }

    /**
     * 使用主模型调用 LLM（主聊天场景）
     */
    public LlmResponse chat(String systemPrompt, List<Map<String, String>> history, String userMessage) {
        return chatWithConfig(properties.getPrimaryConfig(), systemPrompt, history, userMessage);
    }

    /**
     * 使用辅助模型调用 LLM（评分/标题/意图分类/摘要等轻量任务）
     */
    public LlmResponse chatAuxiliary(String systemPrompt, List<Map<String, String>> history, String userMessage) {
        return cachedAuxiliaryCall(systemPrompt, history, userMessage, false);
    }

    /**
     * 使用辅助模型 + JSON Mode 调用 LLM（强制输出合法 JSON）
     */
    public LlmResponse chatAuxiliaryJson(String systemPrompt, List<Map<String, String>> history, String userMessage) {
        return cachedAuxiliaryCall(systemPrompt, history, userMessage, true);
    }

    /**
     * 带缓存的辅助 LLM 调用：相同 prompt+query 在 TTL 内直接返回缓存
     */
    private LlmResponse cachedAuxiliaryCall(String systemPrompt, List<Map<String, String>> history,
                                             String userMessage, boolean jsonMode) {
        String cacheKey = buildCacheKey(systemPrompt, userMessage, jsonMode);
        CachedResponse cached = auxResponseCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.debug("辅助 LLM 缓存命中: key={}", cacheKey.substring(0, Math.min(16, cacheKey.length())));
            return cached.response;
        }
        LlmResponse response = chatWithConfig(properties.getAuxiliaryConfig(), systemPrompt, history, userMessage, jsonMode);
        auxResponseCache.put(cacheKey, new CachedResponse(response, System.currentTimeMillis() + AUX_CACHE_TTL_MS));
        return response;
    }

    private String buildCacheKey(String systemPrompt, String userMessage, boolean jsonMode) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            md.update((systemPrompt != null ? systemPrompt : "").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            md.update((userMessage != null ? userMessage : "").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            md.update((byte) (jsonMode ? 1 : 0));
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to exist, fallback just in case
            return String.valueOf(
                    31 * (systemPrompt != null ? systemPrompt.hashCode() : 0)
                    + (userMessage != null ? userMessage.hashCode() : 0)
                    + (jsonMode ? 1 : 0));
        }
    }

    private void evictExpiredCache() {
        auxResponseCache.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    private static class CachedResponse {
        final LlmResponse response;
        final long expireAt;
        CachedResponse(LlmResponse response, long expireAt) {
            this.response = response;
            this.expireAt = expireAt;
        }
        boolean isExpired() { return System.currentTimeMillis() > expireAt; }
    }

    /**
     * 使用指定模型配置调用 LLM
     */
    public LlmResponse chatWithConfig(ModelConfig config, String systemPrompt,
                                       List<Map<String, String>> history, String userMessage) {
        return chatWithConfig(config, systemPrompt, history, userMessage, false);
    }

    /**
     * 使用指定模型配置调用 LLM（可选 JSON Mode）
     */
    public LlmResponse chatWithConfig(ModelConfig config, String systemPrompt,
                                       List<Map<String, String>> history, String userMessage,
                                       boolean jsonMode) {
        // 熔断检查
        if (!circuitBreaker.allowRequest()) {
            log.warn("LLM 熔断器 OPEN，拒绝请求: model={}", config.getModel());
            throw new RuntimeException("AI服务暂时不可用（熔断保护中），请稍后再试");
        }

        List<Map<String, String>> messages = LlmMessageBuilder.build(systemPrompt, history, userMessage);

        LlmRequest request = LlmRequest.of(
                config.getModel(), messages,
                config.getTemperature(), config.getMaxTokens(), false
        );
        if (jsonMode) {
            request.withJsonMode();
        }

        HttpHeaders headers = buildHeaders(config);
        RestTemplate restTemplate = getOrCreateRestTemplate(config);
        Exception lastException = null;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                String body = objectMapper.writeValueAsString(request);
                HttpEntity<String> entity = new HttpEntity<>(body, headers);

                if (attempt > 0) {
                    log.info("LLM 调用重试 ({}/{}): model={}", attempt, MAX_RETRIES, config.getModel());
                } else {
                    log.debug("调用 LLM: model={}, messages数量={}", config.getModel(), messages.size());
                }

                ResponseEntity<LlmResponse> response = restTemplate.exchange(
                        config.getApiUrl(), HttpMethod.POST, entity, LlmResponse.class
                );

                LlmResponse llmResponse = response.getBody();
                if (llmResponse != null) {
                    log.debug("LLM 响应: model={}, tokens(in={}, out={})",
                            config.getModel(), llmResponse.getInputTokens(), llmResponse.getOutputTokens());
                }
                circuitBreaker.recordSuccess();
                return llmResponse;

            } catch (Exception e) {
                lastException = e;
                log.warn("LLM 调用失败 (attempt {}/{}): model={}, error={}",
                        attempt + 1, MAX_RETRIES + 1, config.getModel(), e.getMessage());
                if (attempt < MAX_RETRIES) {
                    // 指数退避 + 随机抖动，防止雷群效应
                    long delay = RETRY_BASE_DELAY_MS * (1L << attempt)
                            + ThreadLocalRandom.current().nextLong(0, RETRY_BASE_DELAY_MS);
                    try { Thread.sleep(delay); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                }
            }
        }

        circuitBreaker.recordFailure();
        log.error("LLM 调用最终失败 (已重试{}次): model={}, error={}", MAX_RETRIES, config.getModel(), lastException.getMessage(), lastException);
        throw new RuntimeException("AI服务调用失败: " + lastException.getMessage(), lastException);
    }

    /**
     * 构建请求头，包含 API Key 认证（如果配置了）
     */
    private HttpHeaders buildHeaders(ModelConfig config) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (config.hasApiKey()) {
            headers.set("Authorization", "Bearer " + config.getApiKey());
        }
        return headers;
    }

    /**
     * 为不同超时配置缓存 RestTemplate 实例
     */
    @PreDestroy
    public void shutdown() {
        if (cacheCleanerExecutor != null && !cacheCleanerExecutor.isShutdown()) {
            cacheCleanerExecutor.shutdownNow();
            log.debug("LlmClient 缓存清理线程已关闭");
        }
    }

    private RestTemplate getOrCreateRestTemplate(ModelConfig config) {
        String key = config.getTimeoutSeconds() + "s";
        return restTemplateCache.computeIfAbsent(key, k -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(config.getTimeoutSeconds() * 1000);
            factory.setReadTimeout(config.getTimeoutSeconds() * 1000);
            return new RestTemplate(factory);
        });
    }
}

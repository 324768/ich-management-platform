package com.hyang.ich.omnitrix.infrastructure.telemetry;

import com.hyang.ich.omnitrix.entity.AiTraceLog;
import com.hyang.ich.omnitrix.mapper.AiTraceLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class TelemetryTracer {

    private final AiTraceLogMapper traceLogMapper;

    public TelemetryTracer(AiTraceLogMapper traceLogMapper) {
        this.traceLogMapper = traceLogMapper;
    }

    /**
     * 异步记录追踪日志
     */
    @Async("aiAsyncExecutor")
    public void record(Long conversationId, Long userId, String userQuery,
                       String intent, String subAgent, String model,
                       int inputTokens, int outputTokens, int latencyMs,
                       String status, String errorMessage) {
        doRecord(conversationId, userId, userQuery, intent, subAgent, model,
                inputTokens, outputTokens, latencyMs, status, errorMessage);
    }

    /**
     * 同步记录追踪日志并返回 traceId，供后续自评分使用。
     * 注意：不可标注 @Async，否则返回值为代理对象的 null。
     */
    public String recordAndReturnTraceId(Long conversationId, Long userId, String userQuery,
                                          String intent, String subAgent, String model,
                                          int inputTokens, int outputTokens, int latencyMs) {
        return doRecord(conversationId, userId, userQuery, intent, subAgent, model,
                inputTokens, outputTokens, latencyMs, "success", null);
    }

    private String doRecord(Long conversationId, Long userId, String userQuery,
                             String intent, String subAgent, String model,
                             int inputTokens, int outputTokens, int latencyMs,
                             String status, String errorMessage) {
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        try {
            AiTraceLog traceLog = new AiTraceLog();
            traceLog.setTraceId(traceId);
            traceLog.setConversationId(conversationId);
            traceLog.setUserId(userId);
            traceLog.setUserQuery(userQuery);
            traceLog.setIntent(intent);
            traceLog.setSubAgent(subAgent);
            traceLog.setModel(model);
            traceLog.setInputTokens(inputTokens);
            traceLog.setOutputTokens(outputTokens);
            traceLog.setLatencyMs(latencyMs);
            traceLog.setStatus(status != null ? status : "success");
            traceLog.setErrorMessage(errorMessage);

            traceLogMapper.insert(traceLog);
            log.debug("追踪日志已记录: traceId={}, subAgent={}", traceId, subAgent);
        } catch (Exception e) {
            log.error("追踪日志记录失败: {}", e.getMessage());
        }
        return traceId;
    }
}

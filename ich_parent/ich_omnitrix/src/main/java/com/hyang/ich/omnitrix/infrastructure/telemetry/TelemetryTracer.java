package com.hyang.ich.omnitrix.infrastructure.telemetry;

import com.hyang.ich.omnitrix.entity.AiTraceLog;
import com.hyang.ich.omnitrix.entity.AiTraceSpan;
import com.hyang.ich.omnitrix.mapper.AiTraceLogMapper;
import com.hyang.ich.omnitrix.mapper.AiTraceSpanMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class TelemetryTracer {

    private final AiTraceLogMapper traceLogMapper;
    private final AiTraceSpanMapper traceSpanMapper;

    public TelemetryTracer(AiTraceLogMapper traceLogMapper, AiTraceSpanMapper traceSpanMapper) {
        this.traceLogMapper = traceLogMapper;
        this.traceSpanMapper = traceSpanMapper;
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

    // ========== Span 嵌套追踪 ==========

    /**
     * 异步记录一个 Span
     */
    @Async("aiAsyncExecutor")
    public void recordSpan(String traceId, String spanId, String parentSpanId,
                           String spanType, String spanName, int durationMs,
                           String status, String metadata) {
        try {
            AiTraceSpan span = new AiTraceSpan();
            span.setTraceId(traceId);
            span.setSpanId(spanId);
            span.setParentSpanId(parentSpanId);
            span.setSpanType(spanType);
            span.setSpanName(spanName);
            span.setDurationMs(durationMs);
            span.setStatus(status != null ? status : "success");
            span.setMetadata(metadata);
            traceSpanMapper.insert(span);
        } catch (Exception e) {
            log.error("Span记录失败: traceId={}, spanType={}, error={}", traceId, spanType, e.getMessage());
        }
    }

    /** 便捷方法：记录 Agent 执行 Span */
    public void recordAgentSpan(String traceId, String parentSpanId,
                                String agentCode, int durationMs, boolean success) {
        String spanId = generateSpanId();
        recordSpan(traceId, spanId, parentSpanId, "AGENT", agentCode,
                durationMs, success ? "success" : "error", null);
    }

    /** 便捷方法：记录 Tool 执行 Span */
    public void recordToolSpan(String traceId, String parentSpanId,
                               String toolName, int durationMs, boolean success) {
        String spanId = generateSpanId();
        recordSpan(traceId, spanId, parentSpanId, "TOOL", toolName,
                durationMs, success ? "success" : "error", null);
    }

    /** 便捷方法：记录 LLM 调用 Span */
    public void recordLlmSpan(String traceId, String parentSpanId,
                              String model, int durationMs, int inputTokens, int outputTokens) {
        String spanId = generateSpanId();
        String meta = String.format("{\"inputTokens\":%d,\"outputTokens\":%d}", inputTokens, outputTokens);
        recordSpan(traceId, spanId, parentSpanId, "LLM", model,
                durationMs, "success", meta);
    }

    /** 便捷方法：记录 SubBrain 执行 Span */
    public void recordSubBrainSpan(String traceId, String parentSpanId,
                                   String subBrainCode, String subBrainName,
                                   int durationMs, boolean success) {
        String spanId = generateSpanId();
        recordSpan(traceId, spanId, parentSpanId, "SUB_BRAIN", subBrainCode + ":" + subBrainName,
                durationMs, success ? "success" : "error", null);
    }

    /** 便捷方法：记录黑板执行 Span */
    public void recordBlackboardSpan(String traceId, String parentSpanId,
                                     String boardType, int taskCount, int durationMs) {
        String spanId = generateSpanId();
        String meta = String.format("{\"taskCount\":%d,\"boardType\":\"%s\"}", taskCount, boardType);
        recordSpan(traceId, spanId, parentSpanId, "BLACKBOARD", boardType + "_board",
                durationMs, "success", meta);
    }

    /** 查询某个 traceId 下的所有 Span（用于调试/展示） */
    public List<AiTraceSpan> getSpansByTraceId(String traceId) {
        return traceSpanMapper.selectByTraceId(traceId);
    }

    /** Agent 性能统计（最近 N 天） */
    public List<Map<String, Object>> getAgentPerformanceStats(int days) {
        return traceSpanMapper.agentPerformanceStats(days);
    }

    /** Span 类型统计（最近 N 天） */
    public List<Map<String, Object>> getSpanTypeStats(int days) {
        return traceSpanMapper.spanTypeStats(days);
    }

    private static String generateSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}

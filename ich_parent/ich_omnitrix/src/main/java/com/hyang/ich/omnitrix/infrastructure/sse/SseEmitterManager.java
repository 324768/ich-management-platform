package com.hyang.ich.omnitrix.infrastructure.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Slf4j
@Component
public class SseEmitterManager {

    private static final long SSE_TIMEOUT = 120_000L;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 创建新的 SseEmitter
     */
    public SseEmitter create() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emitter.onTimeout(() -> log.debug("SSE 连接超时"));
        emitter.onCompletion(() -> log.debug("SSE 连接完成"));
        emitter.onError(e -> log.debug("SSE 连接错误: {}", e.getMessage()));
        return emitter;
    }

    /**
     * 发送文本 chunk
     */
    public void sendChunk(SseEmitter emitter, String content) {
        try {
            emitter.send(SseEmitter.event().name("chunk").data(content));
        } catch (IOException e) {
            log.info("SSE 发送 chunk 失败: {}", e.getMessage());
        }
    }

    /**
     * 发送思维链 chunk（<think> 标签内容）
     */
    public void sendThinking(SseEmitter emitter, String content) {
        try {
            emitter.send(SseEmitter.event().name("thinking").data(content));
        } catch (IOException e) {
            log.info("SSE 发送 thinking 失败: {}", e.getMessage());
        }
    }

    /**
     * 发送子代理信息
     */
    public void sendAgentInfo(SseEmitter emitter, String agentCode) {
        try {
            AgentEvent event = new AgentEvent();
            event.setAgent(agentCode);
            emitter.send(SseEmitter.event().name("agent").data(objectMapper.writeValueAsString(event)));
        } catch (IOException e) {
            log.info("SSE 发送 agent 信息失败: {}", e.getMessage());
        }
    }

    /**
     * 发送完成事件（兼容旧调用）
     */
    public void sendDone(SseEmitter emitter, Long messageId, int latencyMs) {
        sendDone(emitter, messageId, latencyMs, null);
    }

    /**
     * 发送完成事件（含模型信息）
     */
    public void sendDone(SseEmitter emitter, Long messageId, int latencyMs, String model) {
        try {
            DoneEvent event = new DoneEvent();
            event.setMessageId(messageId);
            event.setLatencyMs(latencyMs);
            event.setModel(model);
            emitter.send(SseEmitter.event().name("done")
                    .data(objectMapper.writeValueAsString(event)));
            emitter.complete();
        } catch (IOException e) {
            log.info("SSE 发送 done 失败: {}", e.getMessage());
        }
    }

    /**
     * 发送错误事件
     */
    public void sendError(SseEmitter emitter, String message) {
        try {
            ErrorEvent event = new ErrorEvent();
            event.setMessage(message);
            emitter.send(SseEmitter.event().name("error").data(objectMapper.writeValueAsString(event)));
            emitter.complete();
        } catch (IOException e) {
            log.info("SSE 发送 error 失败: {}", e.getMessage());
        }
    }

    // ========== SSE 事件 DTO ==========

    @Data
    static class AgentEvent {
        private String agent;
    }

    @Data
    static class DoneEvent {
        private Long messageId;
        private int latencyMs;
        private String model;
    }

    @Data
    static class ErrorEvent {
        private String message;
    }

    // ========== 子代理实时推送事件 ==========

    /**
     * 发送子代理调度事件（主脑开始调用子代理时）
     * 事件名: agent_dispatch
     */
    public void sendAgentDispatch(SseEmitter emitter, String agentCode, String agentName, String query) {
        try {
            AgentDispatchEvent event = new AgentDispatchEvent();
            event.setAgent(agentCode);
            event.setAgentName(agentName);
            event.setQuery(query);
            event.setTimestamp(System.currentTimeMillis());
            String eventData = objectMapper.writeValueAsString(event);
            emitter.send(SseEmitter.event().name("agent_dispatch").data(eventData));
            log.info("SSE 发送 agent_dispatch: agent={}, agentName={}", agentCode, agentName);
        } catch (IOException e) {
            log.info("SSE 发送 agent_dispatch 失败: {}", e.getMessage());
        }
    }

    /**
     * 发送子代理结果事件（子代理执行完成后）
     * 事件名: agent_result
     */
    public void sendAgentResult(SseEmitter emitter, String agentCode, String agentName,
                                String result, String status, int latencyMs) {
        try {
            AgentResultEvent event = new AgentResultEvent();
            event.setAgent(agentCode);
            event.setAgentName(agentName);
            event.setResult(result);
            event.setStatus(status);
            event.setLatencyMs(latencyMs);
            event.setTimestamp(System.currentTimeMillis());
            String eventData = objectMapper.writeValueAsString(event);
            emitter.send(SseEmitter.event().name("agent_result").data(eventData));
            log.info("SSE 发送 agent_result: agent={}, status={}, latencyMs={}", agentCode, status, latencyMs);
        } catch (IOException e) {
            log.info("SSE 发送 agent_result 失败: {}", e.getMessage());
        }
    }

    @Data
    static class AgentDispatchEvent {
        private String agent;
        private String agentName;
        private String query;
        private long timestamp;
    }

    @Data
    static class AgentResultEvent {
        private String agent;
        private String agentName;
        private String result;
        private String status;
        private int latencyMs;
        private long timestamp;
    }
}

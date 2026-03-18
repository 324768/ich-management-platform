package com.hyang.ich.omnitrix.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 自定义业务指标监控
 * 监控 AI 对话、工具调用等关键业务指标
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "omnitrix.metrics.enabled", havingValue = "true", matchIfMissing = true)
public class OmnitrixMetrics {

    private final MeterRegistry registry;
    
    // 对话指标
    private final Counter chatRequestCounter;
    private final Counter chatSuccessCounter;
    private final Counter chatFailureCounter;
    private final Timer chatLatencyTimer;
    
    // 工具调用指标
    private final Counter toolInvokeCounter;
    private final Counter toolSuccessCounter;
    private final Counter toolFailureCounter;
    
    // LLM调用指标
    private final Counter llmRequestCounter;
    private final Counter llmTokenUsageCounter;
    private final Timer llmLatencyTimer;
    
    // 当前活跃会话数
    private final AtomicInteger activeSessions = new AtomicInteger(0);

    public OmnitrixMetrics(MeterRegistry registry) {
        this.registry = registry;
        
        // 对话指标
        this.chatRequestCounter = Counter.builder("omnitrix.chat.requests")
                .description("AI对话请求总数")
                .register(registry);
        this.chatSuccessCounter = Counter.builder("omnitrix.chat.success")
                .description("AI对话成功次数")
                .register(registry);
        this.chatFailureCounter = Counter.builder("omnitrix.chat.failures")
                .description("AI对话失败次数")
                .register(registry);
        this.chatLatencyTimer = Timer.builder("omnitrix.chat.latency")
                .description("AI对话响应延迟")
                .register(registry);
        
        // 工具调用指标
        this.toolInvokeCounter = Counter.builder("omnitrix.tool.invokes")
                .description("工具调用总次数")
                .register(registry);
        this.toolSuccessCounter = Counter.builder("omnitrix.tool.success")
                .description("工具调用成功次数")
                .register(registry);
        this.toolFailureCounter = Counter.builder("omnitrix.tool.failures")
                .description("工具调用失败次数")
                .register(registry);
        
        // LLM调用指标
        this.llmRequestCounter = Counter.builder("omnitrix.llm.requests")
                .description("LLM调用总次数")
                .register(registry);
        this.llmTokenUsageCounter = Counter.builder("omnitrix.llm.tokens")
                .description("LLM Token消耗量")
                .register(registry);
        this.llmLatencyTimer = Timer.builder("omnitrix.llm.latency")
                .description("LLM响应延迟")
                .register(registry);
        
        // 活跃会话数
        Gauge.builder("omnitrix.sessions.active", activeSessions, AtomicInteger::get)
                .description("当前活跃会话数")
                .register(registry);
        
        log.info("✅ Omnitrix 自定义指标初始化完成");
    }

    // ========== 对话指标方法 ==========
    
    public void recordChatRequest() {
        chatRequestCounter.increment();
    }
    
    public void recordChatSuccess() {
        chatSuccessCounter.increment();
    }
    
    public void recordChatFailure() {
        chatFailureCounter.increment();
    }
    
    public Timer.Sample startChatTimer() {
        return Timer.start(registry);
    }
    
    public void recordChatLatency(Timer.Sample sample) {
        sample.stop(chatLatencyTimer);
    }

    // ========== 工具调用指标方法 ==========
    
    public void recordToolInvoke() {
        toolInvokeCounter.increment();
    }
    
    public void recordToolSuccess() {
        toolSuccessCounter.increment();
    }
    
    public void recordToolFailure() {
        toolFailureCounter.increment();
    }

    // ========== LLM调用指标方法 ==========
    
    public void recordLlmRequest() {
        llmRequestCounter.increment();
    }
    
    public void recordLlmTokenUsage(int tokens) {
        llmTokenUsageCounter.increment(tokens);
    }
    
    public Timer.Sample startLlmTimer() {
        return Timer.start(registry);
    }
    
    public void recordLlmLatency(Timer.Sample sample) {
        sample.stop(llmLatencyTimer);
    }

    // ========== 会话管理 ==========
    
    public void incrementActiveSessions() {
        activeSessions.incrementAndGet();
    }
    
    public void decrementActiveSessions() {
        activeSessions.decrementAndGet();
    }
}

package com.hyang.ich.omnitrix.controller;

import com.hyang.ich.common.vo.Result;
import com.hyang.ich.omnitrix.dto.*;
import com.hyang.ich.omnitrix.entity.AiConversation;
import com.hyang.ich.omnitrix.entity.AiMessage;
import com.hyang.ich.omnitrix.entity.AiTraceLog;
import com.hyang.ich.omnitrix.mapper.AiConversationMapper;
import com.hyang.ich.omnitrix.mapper.AiTraceLogMapper;
import com.hyang.ich.omnitrix.orchestrator.OrchestratorService;
import com.hyang.ich.omnitrix.infrastructure.guardrails.RateLimiter;
import com.hyang.ich.omnitrix.infrastructure.guardrails.TokenBudget;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmCircuitBreaker;
import com.hyang.ich.omnitrix.infrastructure.sse.SseEmitterManager;
import com.hyang.ich.omnitrix.infrastructure.telemetry.CostTracker;
import com.hyang.ich.omnitrix.infrastructure.telemetry.TelemetryTracer;
import com.hyang.ich.omnitrix.service.ConversationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/admin/ai")
public class AiAdminController {

    private final AiTraceLogMapper traceLogMapper;
    private final AiConversationMapper conversationMapper;
    private final ConversationService conversationService;
    private final OrchestratorService orchestratorService;
    private final SseEmitterManager sseEmitterManager;
    private final LlmCircuitBreaker circuitBreaker;
    private final RateLimiter rateLimiter;
    private final TokenBudget tokenBudget;
    private final TelemetryTracer telemetryTracer;
    private final CostTracker costTracker;
    private final Executor aiAsyncExecutor;

    public AiAdminController(AiTraceLogMapper traceLogMapper,
                             AiConversationMapper conversationMapper,
                             ConversationService conversationService,
                             OrchestratorService orchestratorService,
                             SseEmitterManager sseEmitterManager,
                             LlmCircuitBreaker circuitBreaker,
                             RateLimiter rateLimiter,
                             TokenBudget tokenBudget,
                             TelemetryTracer telemetryTracer,
                             CostTracker costTracker,
                             @org.springframework.beans.factory.annotation.Qualifier("aiAsyncExecutor") Executor aiAsyncExecutor) {
        this.traceLogMapper = traceLogMapper;
        this.conversationMapper = conversationMapper;
        this.conversationService = conversationService;
        this.orchestratorService = orchestratorService;
        this.sseEmitterManager = sseEmitterManager;
        this.circuitBreaker = circuitBreaker;
        this.rateLimiter = rateLimiter;
        this.tokenBudget = tokenBudget;
        this.telemetryTracer = telemetryTracer;
        this.costTracker = costTracker;
        this.aiAsyncExecutor = aiAsyncExecutor;
    }

    // ========== 管理员 AI 聊天 ==========

    /**
     * 管理员同步聊天
     */
    @PostMapping("/chat")
    public Result<ChatResponse> adminChat(@RequestBody ChatRequest request,
                                          @RequestParam(defaultValue = "1") Long adminId) {
        if (request.getSessionId() == null || request.getSessionId().isEmpty()) {
            request.setSessionId(UUID.randomUUID().toString().replace("-", ""));
        }
        ChatResponse response = orchestratorService.adminChat(adminId, request.getSessionId(), request.getMessage());
        return Result.success(response);
    }

    /**
     * 管理员流式聊天 (SSE)
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter adminChatStream(@RequestParam String sessionId,
                                      @RequestParam String message,
                                      @RequestParam(defaultValue = "1") Long adminId) {
        SseEmitter emitter = sseEmitterManager.create();
        aiAsyncExecutor.execute(() -> orchestratorService.adminChatStream(adminId, sessionId, message, emitter));
        return emitter;
    }

    /**
     * 管理员创建新对话
     */
    @PostMapping("/chat/conversation/create")
    public Result<ConversationCreateVO> createAdminConversation(
            @RequestParam(defaultValue = "1") Long adminId,
            @RequestBody(required = false) Map<String, String> body) {
        String title = (body != null && body.containsKey("title")) ? body.get("title") : "新对话";
        AiConversation conv = conversationService.create(adminId, title);
        return Result.success(ConversationCreateVO.from(conv));
    }

    /**
     * 管理员对话列表
     */
    @GetMapping("/chat/conversation/list")
    public Result<List<ConversationVO>> listAdminConversations(
            @RequestParam(defaultValue = "1") Long adminId) {
        List<AiConversation> conversations = conversationService.listByUserId(adminId);
        List<ConversationVO> result = conversations.stream()
                .map(ConversationVO::from)
                .collect(Collectors.toList());
        return Result.success(result);
    }

    /**
     * 管理员对话详情
     */
    @GetMapping("/chat/conversation/{id}")
    public Result<ConversationDetailVO> getAdminConversation(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Long adminId) {
        AiConversation conv = conversationService.getById(id);
        if (conv == null || !conv.getUserId().equals(adminId)) {
            return Result.failed("对话不存在");
        }
        List<AiMessage> messages = conversationService.listMessages(id);
        List<MessageVO> messageVOs = messages.stream()
                .map(MessageVO::from)
                .collect(Collectors.toList());
        return Result.success(ConversationDetailVO.from(conv, messageVOs));
    }

    /**
     * 管理员删除对话
     */
    @DeleteMapping("/chat/conversation/{id}")
    public Result<Void> deleteAdminConversation(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Long adminId) {
        conversationService.deleteConversation(id, adminId);
        return Result.success();
    }

    // ========== AI Health & Dashboard ==========

    /**
     * AI 系统健康检查（熔断器状态、限流器、Token预算）
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> healthCheck(
            @RequestParam(required = false) Long userId) {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("circuitBreakerState", circuitBreaker.getState().name());
        if (userId != null) {
            health.put("rateLimitRemaining", rateLimiter.remaining(userId));
            health.put("tokenBudgetRemaining", tokenBudget.remaining(userId));
        }
        health.put("timestamp", System.currentTimeMillis());
        // 如果熔断器 OPEN，标记降级状态
        if (circuitBreaker.getState() == LlmCircuitBreaker.State.OPEN) {
            health.put("status", "DEGRADED");
        }
        return Result.success(health);
    }

    /**
     * AI Dashboard 统计数据
     */
    @GetMapping("/dashboard")
    public Result<DashboardStatsVO> dashboard() {
        DashboardStatsVO stats = new DashboardStatsVO();
        stats.setTotalConversations(traceLogMapper.countTotalConversations());
        stats.setTotalMessages(traceLogMapper.countTotalMessages());
        stats.setTodayMessages(traceLogMapper.countTodayMessages());
        stats.setAvgScore(traceLogMapper.avgSelfScore());
        stats.setAvgLatencyMs(traceLogMapper.avgLatencyMs());
        stats.setTopAgents(traceLogMapper.topAgents());

        List<Map<String, Object>> scoreDist = traceLogMapper.scoreDistribution();
        if (scoreDist != null && !scoreDist.isEmpty()) {
            stats.setScoreDistribution(scoreDist.get(0));
        }

        // Agent 性能统计（最近 7 天）
        stats.setAgentPerformance(telemetryTracer.getAgentPerformanceStats(7));
        stats.setSpanTypeStats(telemetryTracer.getSpanTypeStats(7));

        return Result.success(stats);
    }

    /**
     * Agent 性能统计仪表板（可指定时间范围）
     */
    @GetMapping("/agent-stats")
    public Result<Map<String, Object>> agentPerformanceStats(
            @RequestParam(defaultValue = "7") int days) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("agentPerformance", telemetryTracer.getAgentPerformanceStats(days));
        result.put("spanTypeStats", telemetryTracer.getSpanTypeStats(days));
        result.put("days", days);
        return Result.success(result);
    }

    /**
     * 成本统计仪表板（Token→RMB，按模型/按天统计）
     */
    @GetMapping("/cost-stats")
    public Result<Map<String, Object>> costStats(
            @RequestParam(defaultValue = "7") int days) {
        return Result.success(costTracker.getCostStats(days));
    }

    /**
     * 查看某个请求的完整 Span 链（调试用）
     */
    @GetMapping("/traces/{traceId}/spans")
    public Result<List<?>> getTraceSpans(@PathVariable String traceId) {
        return Result.success(telemetryTracer.getSpansByTraceId(traceId));
    }

    /**
     * 分页查询追踪日志
     */
    @GetMapping("/traces")
    public Result<PageVO<AiTraceLog>> listTraces(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        int offset = (pageNum - 1) * pageSize;
        List<AiTraceLog> list = traceLogMapper.selectPage(offset, pageSize, startDate, endDate);
        int total = traceLogMapper.countAll(startDate, endDate);
        return Result.success(PageVO.of(list, total));
    }

    /**
     * 分页查询所有用户对话（审计用）
     */
    @GetMapping("/conversations")
    public Result<List<ConversationVO>> listConversations(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        List<AiConversation> all = conversationMapper.selectAll(
                (pageNum - 1) * pageSize, pageSize);
        List<ConversationVO> result = all.stream()
                .map(ConversationVO::from)
                .collect(Collectors.toList());
        return Result.success(result);
    }

    /**
     * 查看对话详情（管理员可查看任何用户的对话）
     */
    @GetMapping("/conversations/{id}")
    public Result<ConversationDetailVO> getConversation(@PathVariable Long id) {
        AiConversation conv = conversationService.getById(id);
        if (conv == null) {
            return Result.failed("对话不存在");
        }

        List<AiMessage> messages = conversationService.listMessages(id);
        List<MessageVO> messageVOs = messages.stream()
                .map(MessageVO::from)
                .collect(Collectors.toList());
        return Result.success(ConversationDetailVO.from(conv, messageVOs));
    }
}

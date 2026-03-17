package com.hyang.ich.omnitrix.orchestrator;

import com.hyang.ich.omnitrix.brain.AiRequestContext;
import com.hyang.ich.omnitrix.brain.MasterBrainFactory;
import com.hyang.ich.omnitrix.brain.tools.SubBrainTools;
import com.hyang.ich.omnitrix.dto.ChatResponse;
import com.hyang.ich.omnitrix.dto.PendingAction;
import com.hyang.ich.omnitrix.entity.AiConversation;
import com.hyang.ich.omnitrix.entity.AiMessage;
import com.hyang.ich.omnitrix.entity.AiUserAiConfig;
import com.hyang.ich.omnitrix.mapper.AiUserAiConfigMapper;
import com.hyang.ich.omnitrix.infrastructure.guardrails.GuardrailsFilter;
import com.hyang.ich.omnitrix.infrastructure.guardrails.RateLimiter;
import com.hyang.ich.omnitrix.infrastructure.guardrails.TokenBudget;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmCircuitBreaker;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmProperties;
import com.hyang.ich.omnitrix.infrastructure.memory.ChatMemoryManager;
import com.hyang.ich.omnitrix.infrastructure.memory.MemoryExtractor;
import com.hyang.ich.omnitrix.infrastructure.memory.MemorySummarizer;
import com.hyang.ich.omnitrix.infrastructure.sse.SseEmitterManager;
import com.hyang.ich.omnitrix.infrastructure.telemetry.AiSelfEvaluator;
import com.hyang.ich.omnitrix.infrastructure.telemetry.CostTracker;
import com.hyang.ich.omnitrix.infrastructure.telemetry.TelemetryTracer;
import com.hyang.ich.omnitrix.infrastructure.telemetry.TitleGenerator;
import com.hyang.ich.omnitrix.service.ConversationService;
import com.hyang.ich.omnitrix.service.UserMemoryService;
import com.hyang.ich.user.UserService;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class OrchestratorService {

    private static final String LAST_AGENT_KEY_PREFIX = "omnitrix:last_agent:";

    private final ActionExecutor actionExecutor;
    private final GuardrailsFilter guardrailsFilter;
    private final RateLimiter rateLimiter;
    private final TokenBudget tokenBudget;
    private final LlmProperties llmProperties;
    private final LlmCircuitBreaker circuitBreaker;
    private final ConversationService conversationService;
    private final ChatMemoryManager memoryManager;
    private final MemoryExtractor memoryExtractor;
    private final MemorySummarizer memorySummarizer;
    private final UserMemoryService userMemoryService;
    private final SseEmitterManager sseEmitterManager;
    private final TelemetryTracer telemetryTracer;
    private final AiSelfEvaluator selfEvaluator;
    private final CostTracker costTracker;
    private final TitleGenerator titleGenerator;
    private final StringRedisTemplate redisTemplate;
    private final AiUserAiConfigMapper aiUserAiConfigMapper;
    private final UserService userService;
    private final MasterBrainFactory masterBrainFactory;
    private final Executor agentExecutor;

    public OrchestratorService(ActionExecutor actionExecutor,
                               GuardrailsFilter guardrailsFilter,
                               RateLimiter rateLimiter,
                               TokenBudget tokenBudget,
                               LlmProperties llmProperties,
                               LlmCircuitBreaker circuitBreaker,
                               ConversationService conversationService,
                               ChatMemoryManager memoryManager,
                               MemoryExtractor memoryExtractor,
                               MemorySummarizer memorySummarizer,
                               UserMemoryService userMemoryService,
                               SseEmitterManager sseEmitterManager,
                               TelemetryTracer telemetryTracer,
                               AiSelfEvaluator selfEvaluator,
                               CostTracker costTracker,
                               TitleGenerator titleGenerator,
                               StringRedisTemplate redisTemplate,
                               AiUserAiConfigMapper aiUserAiConfigMapper,
                               UserService userService,
                               MasterBrainFactory masterBrainFactory,
                               @Qualifier("aiAsyncExecutor") Executor agentExecutor) {
        this.actionExecutor = actionExecutor;
        this.guardrailsFilter = guardrailsFilter;
        this.rateLimiter = rateLimiter;
        this.tokenBudget = tokenBudget;
        this.llmProperties = llmProperties;
        this.circuitBreaker = circuitBreaker;
        this.conversationService = conversationService;
        this.memoryManager = memoryManager;
        this.memoryExtractor = memoryExtractor;
        this.memorySummarizer = memorySummarizer;
        this.userMemoryService = userMemoryService;
        this.sseEmitterManager = sseEmitterManager;
        this.telemetryTracer = telemetryTracer;
        this.selfEvaluator = selfEvaluator;
        this.costTracker = costTracker;
        this.titleGenerator = titleGenerator;
        this.redisTemplate = redisTemplate;
        this.aiUserAiConfigMapper = aiUserAiConfigMapper;
        this.userService = userService;
        this.masterBrainFactory = masterBrainFactory;
        this.agentExecutor = agentExecutor;
    }

    // ========== 公共入口 ==========

    public ChatResponse chat(Long userId, String sessionId, String userMessage) {
        String aiCheck = checkUserAiAccess(userId);
        if (aiCheck != null) return ChatResponse.of(null, sessionId, aiCheck, "access_denied", 0);
        refreshOnlineHeartbeat(userId);
        return doChatSync(userId, sessionId, userMessage, "user");
    }

    public void chatStream(Long userId, String sessionId, String userMessage, SseEmitter emitter) {
        String aiCheck = checkUserAiAccess(userId);
        if (aiCheck != null) { sseEmitterManager.sendError(emitter, aiCheck); return; }
        refreshOnlineHeartbeat(userId);
        doChatStream(userId, sessionId, userMessage, emitter, "user");
    }

    public ChatResponse adminChat(Long adminId, String sessionId, String userMessage) {
        return doChatSync(adminId, sessionId, userMessage, "admin");
    }

    public void adminChatStream(Long adminId, String sessionId, String userMessage, SseEmitter emitter) {
        doChatStream(adminId, sessionId, userMessage, emitter, "admin");
    }

    public ChatResponse ultraChat(Long adminId, String sessionId, String userMessage) {
        return doChatSync(adminId, sessionId, userMessage, "ultra");
    }

    public void ultraChatStream(Long adminId, String sessionId, String userMessage, SseEmitter emitter) {
        doChatStream(adminId, sessionId, userMessage, emitter, "ultra");
    }

    // ========== 核心同步流程 ==========

    private ChatResponse doChatSync(Long userId, String sessionId, String userMessage, String role) {
        long startTime = System.currentTimeMillis();
        boolean isUltra = "ultra".equals(role);

        if (!isUltra) {
            if (!rateLimiter.tryAcquire(userId))
                return ChatResponse.of(null, sessionId, "您的提问过于频繁，请稍后再试", "rate_limit", 0);
            if (!tokenBudget.hasRemaining(userId))
                return ChatResponse.of(null, sessionId, "您今日的AI对话额度已用完，请明天再试", "token_budget", 0);
        }
        String inputRejection = guardrailsFilter.validateInput(userMessage);
        if (inputRejection != null)
            return ChatResponse.of(null, sessionId, inputRejection, "guardrails", 0);

        AiConversation conversation = conversationService.findOrCreate(sessionId, userId);
        log.info("{}聊天开始: userId={}, sessionId={}", role, userId, sessionId);

        ChatResponse pendingResult = handlePendingActionSync(userId, sessionId, userMessage, conversation);
        if (pendingResult != null) return pendingResult;

        String agentCode = "langchain4j_" + role;
        AiRequestContext.set(userId, sessionId);
        SubBrainTools.resetCallCounter();
        try {
            BrainResult result = invokeBrainSync(role, userId, sessionId, userMessage);
            String aiContent = guardrailsFilter.sanitizeOutput(result.content);
            int latencyMs = (int) (System.currentTimeMillis() - startTime);

            AiMessage assistantMsg = saveAndPostProcess(
                    conversation, sessionId, userId, userMessage, aiContent,
                    result.model, agentCode, latencyMs,
                    result.inputTokens, result.outputTokens);

            log.info("处理完成: role={}, model={}, latency={}ms, tokens={}/{}",
                    role, result.model, latencyMs, result.inputTokens, result.outputTokens);
            return ChatResponse.of(assistantMsg.getId(), sessionId, aiContent, agentCode, latencyMs);
        } catch (Exception e) {
            log.error("同步聊天异常: role={}, error={}", role, e.getMessage(), e);
            String errorContent = "AI 服务暂时不可用，请稍后再试。";
            AiMessage errMsg = conversationService.saveMessage(
                    conversation.getId(), sessionId, "assistant", errorContent, 0,
                    llmProperties.getPrimaryConfig().getModel(), agentCode, 0);
            return ChatResponse.of(errMsg.getId(), sessionId, errorContent, agentCode, 0);
        } finally {
            SubBrainTools.clearCallCounter();
            AiRequestContext.clear();
        }
    }

    // ========== 核心流式流程（真流式 TokenStream） ==========

    private void doChatStream(Long userId, String sessionId, String userMessage,
                               SseEmitter emitter, String role) {
        boolean isUltra = "ultra".equals(role);

        if (!isUltra) {
            if (!rateLimiter.tryAcquire(userId)) {
                sseEmitterManager.sendError(emitter, "您的提问过于频繁，请稍后再试"); return;
            }
            if (!tokenBudget.hasRemaining(userId)) {
                sseEmitterManager.sendError(emitter, "您今日的AI对话额度已用完，请明天再试"); return;
            }
        }
        String inputRejection = guardrailsFilter.validateInput(userMessage);
        if (inputRejection != null) { sseEmitterManager.sendError(emitter, inputRejection); return; }

        if (!circuitBreaker.allowRequest()) {
            sseEmitterManager.sendError(emitter, "AI服务暂时过载，请稍后重试"); return;
        }

        try {
            AiConversation conversation = conversationService.findOrCreate(sessionId, userId);
            if (handlePendingActionStream(userId, sessionId, userMessage, conversation, emitter)) return;

            String agentCode = "langchain4j_" + role;
            sseEmitterManager.sendAgentInfo(emitter, agentCode);

            long startTime = System.currentTimeMillis();

            CompletableFuture.runAsync(() -> {
                AiRequestContext.set(userId, sessionId, emitter);
                SubBrainTools.resetCallCounter();
                try {
                    String skills = masterBrainFactory.buildSkillsPrompt();
                    TokenStream tokenStream = buildTokenStream(role, userId, sessionId, userMessage, skills);

                    StringBuilder fullContent = new StringBuilder();
                    StringBuilder thinkBuffer = new StringBuilder();
                    AtomicReference<Boolean> insideThink = new AtomicReference<>(false);
                    AtomicReference<String> partialTag = new AtomicReference<>("");
                    tokenStream
                        .onNext(token -> {
                            String chunk = partialTag.get() + token;
                            partialTag.set("");

                            // <think> 标签跨chunk缓冲
                            if (chunk.endsWith("<") || chunk.endsWith("<t") || chunk.endsWith("<th")
                                    || chunk.endsWith("<thi") || chunk.endsWith("<thin")
                                    || chunk.endsWith("<think") || chunk.endsWith("</")
                                    || chunk.endsWith("</t") || chunk.endsWith("</th")
                                    || chunk.endsWith("</thi") || chunk.endsWith("</thin")
                                    || chunk.endsWith("</think")) {
                                int cutIdx = chunk.lastIndexOf('<');
                                partialTag.set(chunk.substring(cutIdx));
                                chunk = chunk.substring(0, cutIdx);
                            }

                            if (chunk.contains("<think>")) {
                                String before = chunk.substring(0, chunk.indexOf("<think>"));
                                if (!before.isEmpty()) sseEmitterManager.sendChunk(emitter, before);
                                fullContent.append(before);
                                insideThink.set(true);
                                String afterTag = chunk.substring(chunk.indexOf("<think>") + 7);
                                if (!afterTag.isEmpty()) {
                                    sseEmitterManager.sendThinking(emitter, afterTag);
                                    thinkBuffer.append(afterTag);
                                }
                                return;
                            }
                            if (chunk.contains("</think>")) {
                                String before = chunk.substring(0, chunk.indexOf("</think>"));
                                if (!before.isEmpty()) {
                                    sseEmitterManager.sendThinking(emitter, before);
                                    thinkBuffer.append(before);
                                }
                                insideThink.set(false);
                                String afterTag = chunk.substring(chunk.indexOf("</think>") + 8);
                                if (!afterTag.isEmpty()) {
                                    sseEmitterManager.sendChunk(emitter, afterTag);
                                    fullContent.append(afterTag);
                                }
                                return;
                            }

                            if (insideThink.get()) {
                                sseEmitterManager.sendThinking(emitter, chunk);
                                thinkBuffer.append(chunk);
                            } else if (!chunk.isEmpty()) {
                                sseEmitterManager.sendChunk(emitter, chunk);
                                fullContent.append(chunk);
                            }
                        })
                        .onComplete(response -> {
                            AiRequestContext.clearCrossThread(userId);
                            circuitBreaker.recordSuccess();
                            // flush partial tag buffer
                            String remaining = partialTag.get();
                            if (!remaining.isEmpty()) {
                                if (insideThink.get()) {
                                    sseEmitterManager.sendThinking(emitter, remaining);
                                } else {
                                    sseEmitterManager.sendChunk(emitter, remaining);
                                    fullContent.append(remaining);
                                }
                            }

                            String aiContent = guardrailsFilter.sanitizeOutput(fullContent.toString());
                            int latencyMs = (int) (System.currentTimeMillis() - startTime);
                            String usedModel = llmProperties.getPrimaryConfig().getModel();

                            // 从 Response 提取真实 token 使用量
                            int inputTokens = 0, outputTokens = 0;
                            if (response != null && response.tokenUsage() != null) {
                                TokenUsage usage = response.tokenUsage();
                                inputTokens = usage.inputTokenCount() != null ? usage.inputTokenCount() : 0;
                                outputTokens = usage.outputTokenCount() != null ? usage.outputTokenCount() : 0;
                            }

                            AiMessage assistantMsg = saveAndPostProcess(
                                    conversation, sessionId, userId, userMessage, aiContent,
                                    usedModel, agentCode, latencyMs, inputTokens, outputTokens);

                            log.info("流式完成: role={}, model={}, latency={}ms, tokens={}/{}",
                                    role, usedModel, latencyMs, inputTokens, outputTokens);
                            sseEmitterManager.sendDone(emitter, assistantMsg.getId(), latencyMs, usedModel);
                        })
                        .onError(error -> {
                            AiRequestContext.clearCrossThread(userId);
                            circuitBreaker.recordFailure();
                            log.error("流式聊天异常: role={}, error={}", role, error.getMessage(), error);
                            sseEmitterManager.sendError(emitter, "AI 服务暂时不可用");
                        })
                        .start();

                } catch (Exception e) {
                    AiRequestContext.clearCrossThread(userId);
                    log.error("流式启动异常: role={}, error={}", role, e.getMessage(), e);
                    sseEmitterManager.sendError(emitter, "AI 服务暂时不可用");
                } finally {
                    SubBrainTools.clearCallCounter();
                    AiRequestContext.clearLocal();
                }
            }, agentExecutor);
        } catch (Exception e) {
            log.error("流式预处理异常: {}", e.getMessage(), e);
            sseEmitterManager.sendError(emitter, "AI 服务暂时不可用");
        }
    }

    // ========== Brain 调用（含降级链） ==========

    /**
     * 同步调用 Brain，返回 Result<String> 含真实 token 计数。
     * 主模型失败时自动降级到辅助模型。
     */
    private BrainResult invokeBrainSync(String role, Long userId, String sessionId, String userMessage) {
        String skills = masterBrainFactory.buildSkillsPrompt();
        String primaryModel = llmProperties.getPrimaryConfig().getModel();

        try {
            Result<String> result = invokeBrainPrimary(role, userId, sessionId, userMessage, skills);
            int inputTokens = 0, outputTokens = 0;
            if (result.tokenUsage() != null) {
                inputTokens = result.tokenUsage().inputTokenCount() != null ? result.tokenUsage().inputTokenCount() : 0;
                outputTokens = result.tokenUsage().outputTokenCount() != null ? result.tokenUsage().outputTokenCount() : 0;
            }
            return new BrainResult(result.content(), primaryModel, inputTokens, outputTokens);
        } catch (Exception primaryEx) {
            log.warn("主模型调用失败，尝试降级: model={}, error={}", primaryModel, primaryEx.getMessage());
            try {
                Result<String> fallbackResult = invokeBrainFallback(role, userId, sessionId, userMessage, skills);
                String auxModel = llmProperties.getAuxiliaryConfig().getModel() + "(fallback)";
                int inputTokens = 0, outputTokens = 0;
                if (fallbackResult.tokenUsage() != null) {
                    inputTokens = fallbackResult.tokenUsage().inputTokenCount() != null ? fallbackResult.tokenUsage().inputTokenCount() : 0;
                    outputTokens = fallbackResult.tokenUsage().outputTokenCount() != null ? fallbackResult.tokenUsage().outputTokenCount() : 0;
                }
                log.info("降级模型调用成功: model={}", auxModel);
                return new BrainResult(fallbackResult.content(), auxModel, inputTokens, outputTokens);
            } catch (Exception fallbackEx) {
                log.error("降级模型也失败: {}", fallbackEx.getMessage());
                throw primaryEx;
            }
        }
    }

    private Result<String> invokeBrainPrimary(String role, Long userId, String sessionId,
                                               String userMessage, String skills) {
        switch (role) {
            case "user": {
                var brain = masterBrainFactory.buildUserBrain(userId, sessionId);
                String profile = masterBrainFactory.buildUserProfile(userId);
                return brain.chat(userMessage, skills, profile);
            }
            case "admin": {
                var brain = masterBrainFactory.buildAdminBrain(sessionId);
                return brain.chat(userMessage, skills);
            }
            case "ultra": {
                var brain = masterBrainFactory.buildUltraBrain(sessionId);
                return brain.chat(userMessage, skills);
            }
            default:
                throw new IllegalArgumentException("Unknown role: " + role);
        }
    }

    private Result<String> invokeBrainFallback(String role, Long userId, String sessionId,
                                                String userMessage, String skills) {
        switch (role) {
            case "user": {
                var brain = masterBrainFactory.buildUserBrainFallback(userId, sessionId);
                String profile = masterBrainFactory.buildUserProfile(userId);
                return brain.chat(userMessage, skills, profile);
            }
            case "admin": {
                var brain = masterBrainFactory.buildAdminBrainFallback(sessionId);
                return brain.chat(userMessage, skills);
            }
            case "ultra": {
                var brain = masterBrainFactory.buildUltraBrainFallback(sessionId);
                return brain.chat(userMessage, skills);
            }
            default:
                throw new IllegalArgumentException("Unknown role: " + role);
        }
    }

    /** 构建流式 TokenStream（主模型） */
    private TokenStream buildTokenStream(String role, Long userId, String sessionId,
                                          String userMessage, String skills) {
        switch (role) {
            case "user": {
                var brain = masterBrainFactory.buildUserBrainStreaming(userId, sessionId);
                String profile = masterBrainFactory.buildUserProfile(userId);
                return brain.chatStream(userMessage, skills, profile);
            }
            case "admin": {
                var brain = masterBrainFactory.buildAdminBrainStreaming(sessionId);
                return brain.chatStream(userMessage, skills);
            }
            case "ultra": {
                var brain = masterBrainFactory.buildUltraBrainStreaming(sessionId);
                return brain.chatStream(userMessage, skills);
            }
            default:
                throw new IllegalArgumentException("Unknown role: " + role);
        }
    }

    // ========== 存储 & 后处理（含 Span 追踪） ==========

    private AiMessage saveAndPostProcess(AiConversation conversation, String sessionId, Long userId,
                                          String userMessage, String aiContent,
                                          String usedModel, String agentCode, int latencyMs,
                                          int inputTokens, int outputTokens) {
        // 保存用户消息
        conversationService.saveMessage(conversation.getId(), sessionId, "user", userMessage, 0, null, null, null);

        // 保存AI回复
        AiMessage assistantMsg = conversationService.saveMessage(
                conversation.getId(), sessionId, "assistant",
                aiContent, outputTokens, usedModel, agentCode, latencyMs);

        // 对话记忆
        memoryManager.appendMessage(sessionId, "user", userMessage);
        memoryManager.appendMessage(sessionId, "assistant", aiContent);

        // 首轮生成标题
        if (conversation.getMessageCount() == 0) {
            titleGenerator.generateTitle(conversation.getId(), userMessage);
        }

        // 摘要 + 长期记忆
        memorySummarizer.summarizeIfNeeded(sessionId, conversation.getId());
        memoryExtractor.extractAndSave(userId, userMessage, aiContent);
        userMemoryService.invalidateCache(userId);

        // Token 预算消费（真实值）
        if (inputTokens > 0 || outputTokens > 0) {
            tokenBudget.consume(userId, inputTokens + outputTokens);
        }

        // 遥测 + Span 追踪
        String traceId = telemetryTracer.recordAndReturnTraceId(
                conversation.getId(), userId, userMessage,
                agentCode, agentCode, usedModel,
                inputTokens, outputTokens, latencyMs);

        // 嵌套 Span: REQUEST → BRAIN → LLM
        String requestSpanId = traceId + "-req";
        String brainSpanId = traceId + "-brain";
        String llmSpanId = traceId + "-llm";
        telemetryTracer.recordSpan(traceId, requestSpanId, null,
                "REQUEST", "request", latencyMs, "success", null);
        telemetryTracer.recordSpan(traceId, brainSpanId, requestSpanId,
                "BRAIN", agentCode, latencyMs, "success",
                "{\"role\":\"" + agentCode.replace("langchain4j_", "") + "\",\"model\":\"" + usedModel + "\"}");
        telemetryTracer.recordSpan(traceId, llmSpanId, brainSpanId,
                "LLM", usedModel, latencyMs, "success",
                "{\"inputTokens\":" + inputTokens + ",\"outputTokens\":" + outputTokens + "}");

        selfEvaluator.evaluate(traceId, userMessage, aiContent);
        costTracker.recordCost(traceId, usedModel, inputTokens, outputTokens);
        saveLastAgentCode(sessionId, agentCode);

        return assistantMsg;
    }

    // ========== PendingAction 处理 ==========

    private ChatResponse handlePendingActionSync(Long userId, String sessionId,
                                                  String userMessage, AiConversation conversation) {
        PendingAction pendingAction = actionExecutor.getPendingAction(sessionId);
        if (pendingAction == null) return null;

        int confirmation = actionExecutor.detectConfirmation(userMessage);
        if (confirmation == 1) {
            actionExecutor.clearPendingAction(sessionId);
            String actionResult = actionExecutor.execute(pendingAction, userId);
            log.info("用户确认执行操作: type={}, result={}", pendingAction.getActionType(), actionResult);
            conversationService.saveMessage(conversation.getId(), sessionId, "user", userMessage, 0, null, null, null);
            AiMessage resultMsg = conversationService.saveMessage(conversation.getId(), sessionId, "assistant", actionResult, 0, "system", "action_executor", 0);
            memoryManager.appendMessage(sessionId, "user", userMessage);
            memoryManager.appendMessage(sessionId, "assistant", actionResult);
            return ChatResponse.of(resultMsg.getId(), sessionId, actionResult, "action_executor", 0);
        } else if (confirmation == -1) {
            actionExecutor.clearPendingAction(sessionId);
            String cancelMsg = "好的，已取消操作。还有其他可以帮您的吗？";
            conversationService.saveMessage(conversation.getId(), sessionId, "user", userMessage, 0, null, null, null);
            AiMessage cancelResult = conversationService.saveMessage(conversation.getId(), sessionId, "assistant", cancelMsg, 0, "system", "action_executor", 0);
            memoryManager.appendMessage(sessionId, "user", userMessage);
            memoryManager.appendMessage(sessionId, "assistant", cancelMsg);
            return ChatResponse.of(cancelResult.getId(), sessionId, cancelMsg, "action_executor", 0);
        }
        actionExecutor.clearPendingAction(sessionId);
        return null;
    }

    private boolean handlePendingActionStream(Long userId, String sessionId,
                                               String userMessage, AiConversation conversation,
                                               SseEmitter emitter) {
        PendingAction pendingAction = actionExecutor.getPendingAction(sessionId);
        if (pendingAction == null) return false;

        int confirmation = actionExecutor.detectConfirmation(userMessage);
        if (confirmation == 1) {
            actionExecutor.clearPendingAction(sessionId);
            String actionResult = actionExecutor.execute(pendingAction, userId);
            log.info("流式-用户确认执行操作: type={}, result={}", pendingAction.getActionType(), actionResult);
            conversationService.saveMessage(conversation.getId(), sessionId, "user", userMessage, 0, null, null, null);
            conversationService.saveMessage(conversation.getId(), sessionId, "assistant", actionResult, 0, "system", "action_executor", 0);
            memoryManager.appendMessage(sessionId, "user", userMessage);
            memoryManager.appendMessage(sessionId, "assistant", actionResult);
            sseEmitterManager.sendAgentInfo(emitter, "action_executor");
            sseEmitterManager.sendChunk(emitter, actionResult);
            sseEmitterManager.sendDone(emitter, null, 0, "system");
            return true;
        } else if (confirmation == -1) {
            actionExecutor.clearPendingAction(sessionId);
            String cancelMsg = "好的，已取消操作。还有其他可以帮您的吗？";
            conversationService.saveMessage(conversation.getId(), sessionId, "user", userMessage, 0, null, null, null);
            conversationService.saveMessage(conversation.getId(), sessionId, "assistant", cancelMsg, 0, "system", "action_executor", 0);
            memoryManager.appendMessage(sessionId, "user", userMessage);
            memoryManager.appendMessage(sessionId, "assistant", cancelMsg);
            sseEmitterManager.sendAgentInfo(emitter, "action_executor");
            sseEmitterManager.sendChunk(emitter, cancelMsg);
            sseEmitterManager.sendDone(emitter, null, 0, "system");
            return true;
        }
        actionExecutor.clearPendingAction(sessionId);
        return false;
    }

    // ========== 辅助方法 ==========

    private String checkUserAiAccess(Long userId) {
        try {
            AiUserAiConfig config = aiUserAiConfigMapper.selectByUserId(userId);
            if (config != null && config.getAiEnabled() != null && config.getAiEnabled() == 0) {
                String reason = config.getDisabledReason() != null ? config.getDisabledReason() : "管理员已禁用";
                log.info("用户AI访问被拒绝: userId={}, reason={}", userId, reason);
                return "抱歉，您的AI功能已被管理员禁用。原因：" + reason;
            }
        } catch (Exception e) {
            log.debug("检查用户AI访问权限失败: {}", e.getMessage());
        }
        return null;
    }

    private void saveLastAgentCode(String sessionId, String agentCode) {
        try {
            redisTemplate.opsForValue().set(
                    LAST_AGENT_KEY_PREFIX + sessionId, agentCode, 1, TimeUnit.HOURS);
        } catch (Exception e) {
            log.debug("保存 lastAgentCode 失败: {}", e.getMessage());
        }
    }

    private void refreshOnlineHeartbeat(Long userId) {
        if (userId == null) return;
        CompletableFuture.runAsync(() -> {
            try {
                userService.setOnlineStatus(userId, true);
            } catch (Exception e) {
                log.debug("刷新在线心跳失败: userId={}, error={}", userId, e.getMessage());
            }
        }, agentExecutor);
    }

    // ========== 内部 DTO ==========

    private static class BrainResult {
        final String content;
        final String model;
        final int inputTokens;
        final int outputTokens;

        BrainResult(String content, String model, int inputTokens, int outputTokens) {
            this.content = content;
            this.model = model;
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
        }
    }
}

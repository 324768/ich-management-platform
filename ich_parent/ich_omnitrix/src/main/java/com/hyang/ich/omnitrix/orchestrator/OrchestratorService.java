package com.hyang.ich.omnitrix.orchestrator;

import com.hyang.ich.omnitrix.agent.AgentContext;
import com.hyang.ich.omnitrix.agent.SubAgent;
import com.hyang.ich.omnitrix.agent.SubAgentRegistry;
import com.hyang.ich.omnitrix.blackboard.TaskBoard;
import com.hyang.ich.omnitrix.blackboard.TaskDecomposer;
import com.hyang.ich.omnitrix.blackboard.TaskNode;
import com.hyang.ich.omnitrix.blackboard.UltraTaskDecomposer;
import com.hyang.ich.omnitrix.dto.AgentQueryResult;
import com.hyang.ich.omnitrix.dto.ChatResponse;
import com.hyang.ich.omnitrix.dto.PendingAction;
import com.hyang.ich.omnitrix.dto.StreamResult;
import com.hyang.ich.omnitrix.entity.AiConversation;
import com.hyang.ich.omnitrix.entity.AiMessage;
import com.hyang.ich.omnitrix.entity.AiUserAiConfig;
import com.hyang.ich.omnitrix.mapper.AiUserAiConfigMapper;
import com.hyang.ich.omnitrix.infrastructure.guardrails.GuardrailsFilter;
import com.hyang.ich.omnitrix.infrastructure.guardrails.RateLimiter;
import com.hyang.ich.omnitrix.infrastructure.guardrails.TokenBudget;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmClient;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmProperties;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmResponse;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmStreamHandler;
import com.hyang.ich.omnitrix.infrastructure.memory.ChatMemoryManager;
import com.hyang.ich.omnitrix.infrastructure.memory.MemoryExtractor;
import com.hyang.ich.omnitrix.infrastructure.memory.MemorySummarizer;
import com.hyang.ich.omnitrix.infrastructure.prompt.PromptAssembler;
import com.hyang.ich.omnitrix.infrastructure.sse.SseEmitterManager;
import com.hyang.ich.omnitrix.infrastructure.telemetry.AiSelfEvaluator;
import com.hyang.ich.omnitrix.infrastructure.telemetry.TelemetryTracer;
import com.hyang.ich.omnitrix.infrastructure.telemetry.TitleGenerator;
import com.hyang.ich.user.UserService;
import com.hyang.ich.omnitrix.service.ConversationService;
import com.hyang.ich.omnitrix.service.SystemMemoryService;
import com.hyang.ich.omnitrix.service.UserMemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class OrchestratorService {

    private static final String LAST_AGENT_KEY_PREFIX = "omnitrix:last_agent:";
    private static final int AGENT_TIMEOUT_SECONDS = 10;
    private static final int BLACKBOARD_MAX_ROUNDS = 5;

    private final ActionExecutor actionExecutor;
    private final GuardrailsFilter guardrailsFilter;
    private final RateLimiter rateLimiter;
    private final TokenBudget tokenBudget;
    private final IntentRouter intentRouter;
    private final AdminIntentRouter adminIntentRouter;
    private final UltraIntentRouter ultraIntentRouter;
    private final SubAgentRegistry subAgentRegistry;
    private final PromptAssembler promptAssembler;
    private final LlmClient llmClient;
    private final LlmProperties llmProperties;
    private final ConversationService conversationService;
    private final ChatMemoryManager memoryManager;
    private final MemoryExtractor memoryExtractor;
    private final MemorySummarizer memorySummarizer;
    private final UserMemoryService userMemoryService;
    private final SystemMemoryService systemMemoryService;
    private final LlmStreamHandler llmStreamHandler;
    private final SseEmitterManager sseEmitterManager;
    private final TelemetryTracer telemetryTracer;
    private final AiSelfEvaluator selfEvaluator;
    private final TitleGenerator titleGenerator;
    private final StringRedisTemplate redisTemplate;
    private final Executor agentExecutor;
    private final TaskDecomposer taskDecomposer;
    private final UltraTaskDecomposer ultraTaskDecomposer;
    private final AiUserAiConfigMapper aiUserAiConfigMapper;
    private final UserService userService;
    private final Executor l2BoardExecutor;

    public OrchestratorService(ActionExecutor actionExecutor,
                               GuardrailsFilter guardrailsFilter,
                               RateLimiter rateLimiter,
                               TokenBudget tokenBudget,
                               IntentRouter intentRouter,
                               AdminIntentRouter adminIntentRouter,
                               UltraIntentRouter ultraIntentRouter,
                               SubAgentRegistry subAgentRegistry,
                               PromptAssembler promptAssembler,
                               LlmClient llmClient,
                               LlmProperties llmProperties,
                               ConversationService conversationService,
                               ChatMemoryManager memoryManager,
                               MemoryExtractor memoryExtractor,
                               MemorySummarizer memorySummarizer,
                               UserMemoryService userMemoryService,
                               SystemMemoryService systemMemoryService,
                               LlmStreamHandler llmStreamHandler,
                               SseEmitterManager sseEmitterManager,
                               TelemetryTracer telemetryTracer,
                               AiSelfEvaluator selfEvaluator,
                               TitleGenerator titleGenerator,
                               StringRedisTemplate redisTemplate,
                               TaskDecomposer taskDecomposer,
                               UltraTaskDecomposer ultraTaskDecomposer,
                               AiUserAiConfigMapper aiUserAiConfigMapper,
                               UserService userService,
                               @org.springframework.beans.factory.annotation.Qualifier("aiAsyncExecutor") Executor agentExecutor,
                               @org.springframework.beans.factory.annotation.Qualifier("l2BoardExecutor") Executor l2BoardExecutor) {
        this.actionExecutor = actionExecutor;
        this.guardrailsFilter = guardrailsFilter;
        this.rateLimiter = rateLimiter;
        this.tokenBudget = tokenBudget;
        this.intentRouter = intentRouter;
        this.adminIntentRouter = adminIntentRouter;
        this.ultraIntentRouter = ultraIntentRouter;
        this.subAgentRegistry = subAgentRegistry;
        this.promptAssembler = promptAssembler;
        this.llmClient = llmClient;
        this.llmProperties = llmProperties;
        this.conversationService = conversationService;
        this.memoryManager = memoryManager;
        this.memoryExtractor = memoryExtractor;
        this.memorySummarizer = memorySummarizer;
        this.userMemoryService = userMemoryService;
        this.systemMemoryService = systemMemoryService;
        this.llmStreamHandler = llmStreamHandler;
        this.sseEmitterManager = sseEmitterManager;
        this.telemetryTracer = telemetryTracer;
        this.selfEvaluator = selfEvaluator;
        this.titleGenerator = titleGenerator;
        this.redisTemplate = redisTemplate;
        this.taskDecomposer = taskDecomposer;
        this.ultraTaskDecomposer = ultraTaskDecomposer;
        this.aiUserAiConfigMapper = aiUserAiConfigMapper;
        this.userService = userService;
        this.agentExecutor = agentExecutor;
        this.l2BoardExecutor = l2BoardExecutor;
    }

    // ========== 公共入口 ==========

    /** 用户同步聊天 */
    public ChatResponse chat(Long userId, String sessionId, String userMessage) {
        String aiCheck = checkUserAiAccess(userId);
        if (aiCheck != null) {
            return ChatResponse.of(null, sessionId, aiCheck, "access_denied", 0);
        }
        refreshOnlineHeartbeat(userId);
        return doChatSync(userId, sessionId, userMessage, "user", false);
    }

    /** 用户流式聊天 (SSE) */
    public void chatStream(Long userId, String sessionId, String userMessage, SseEmitter emitter) {
        String aiCheck = checkUserAiAccess(userId);
        if (aiCheck != null) {
            sseEmitterManager.sendError(emitter, aiCheck);
            return;
        }
        refreshOnlineHeartbeat(userId);
        doChatStreamCore(userId, sessionId, userMessage, emitter, "user", false);
    }

    /** 管理员同步聊天 */
    public ChatResponse adminChat(Long adminId, String sessionId, String userMessage) {
        return doChatSync(adminId, sessionId, userMessage, "admin", true);
    }

    /** 管理员流式聊天 */
    public void adminChatStream(Long adminId, String sessionId, String userMessage, SseEmitter emitter) {
        doChatStreamCore(adminId, sessionId, userMessage, emitter, "admin", true);
    }

    /** Ultra AI 同步聊天 */
    public ChatResponse ultraChat(Long adminId, String sessionId, String userMessage) {
        return doUltraChatSync(adminId, sessionId, userMessage);
    }

    /** Ultra AI 流式聊天 */
    public void ultraChatStream(Long adminId, String sessionId, String userMessage, SseEmitter emitter) {
        doUltraChatStreamCore(adminId, sessionId, userMessage, emitter);
    }

    // ========== 核心同步流程 ==========

    private ChatResponse doChatSync(Long userId, String sessionId, String userMessage,
                                     String role, boolean isAdmin) {
        long startTime = System.currentTimeMillis();

        // Step 0: 限流 + 安全 + Token预算
        if (!rateLimiter.tryAcquire(userId)) {
            return ChatResponse.of(null, sessionId, "您的提问过于频繁，请稍后再试", "rate_limit", 0);
        }
        String inputRejection = guardrailsFilter.validateInput(userMessage);
        if (inputRejection != null) {
            log.warn("输入被安全护栏拦截: userId={}, reason={}", userId, inputRejection);
            return ChatResponse.of(null, sessionId, inputRejection, "guardrails", 0);
        }
        if (!tokenBudget.hasRemaining(userId)) {
            return ChatResponse.of(null, sessionId, "您今日的AI对话额度已用完，请明天再试", "token_budget", 0);
        }

        // Step 1: 对话管理
        AiConversation conversation = conversationService.findOrCreate(sessionId, userId);
        AgentContext context = AgentContext.of(userId, sessionId, conversation.getId(), role);
        log.info("{}聊天开始: userId={}, sessionId={}", isAdmin ? "管理员" : "用户", userId, sessionId);

        // Step 1.5: 检查待确认操作
        ChatResponse pendingResult = handlePendingActionSync(userId, sessionId, userMessage, conversation);
        if (pendingResult != null) return pendingResult;

        // Step 2: 加载对话历史
        List<Map<String, String>> history = memoryManager.loadHistory(sessionId);
        String summary = memoryManager.getSummary(sessionId);
        if (summary == null) summary = conversation.getSummary();

        // Step 2.5: 黑板路径 —— 复杂多Agent查询检测与分解
        if (taskDecomposer.isComplexQuery(userMessage)) {
            ChatResponse bbResult = executeBlackboardSync(
                    userId, sessionId, userMessage, conversation, context,
                    history, summary, startTime, isAdmin);
            if (bbResult != null) return bbResult;
            // 黑板分解失败则降级到快速路径
        }

        // Step 3: 意图路由（快速路径：单Agent）
        String lastAgentCode = getLastAgentCode(sessionId);
        String agentCode = isAdmin
                ? adminIntentRouter.route(userMessage, lastAgentCode)
                : intentRouter.route(userMessage, lastAgentCode);
        SubAgent subAgent = subAgentRegistry.getOrDefault(agentCode);
        log.info("意图路由(快速路径): '{}' → {} ({})", userMessage, subAgent.getCode(), subAgent.getName());

        // Step 4: 执行子代理（超时保护）
        AgentQueryResult queryResult = executeAgent(subAgent, userMessage, context);

        // Step 5: 组装 Prompt（含用户画像）
        String userProfile = userMemoryService.buildUserProfile(userId);
        String systemPrompt = promptAssembler.assemble(context, subAgent, queryResult, summary, userProfile);

        // Step 6: 调用 LLM（主→辅降级链）
        LlmResponse llmResponse;
        String usedModel;
        try {
            llmResponse = llmClient.chat(systemPrompt, history, userMessage);
            usedModel = llmProperties.getPrimaryConfig().getModel();
        } catch (Exception e) {
            log.warn("主模型调用失败，降级到辅助模型: {}", e.getMessage());
            try {
                llmResponse = llmClient.chatWithConfig(
                        llmProperties.getAuxiliaryConfig(), systemPrompt, history, userMessage);
                usedModel = llmProperties.getAuxiliaryConfig().getModel();
                log.info("辅助模型降级成功: model={}", usedModel);
            } catch (Exception ex) {
                log.error("辅助模型也调用失败: {}", ex.getMessage(), ex);
                String errorContent = "抱歉，AI 服务暂时不可用，请稍后再试。";
                AiMessage errMsg = conversationService.saveMessage(
                        conversation.getId(), sessionId, "assistant", errorContent, 0,
                        llmProperties.getPrimaryConfig().getModel(), subAgent.getCode(), 0);
                return ChatResponse.of(errMsg.getId(), sessionId, errorContent, subAgent.getCode(), 0);
            }
        }

        String aiContent = guardrailsFilter.sanitizeOutput(llmResponse.getContent());
        int latencyMs = (int) (System.currentTimeMillis() - startTime);

        // Step 7: 存储 & 后处理
        AiMessage assistantMsg = saveAndPostProcess(
                conversation, sessionId, userId, userMessage, aiContent,
                llmResponse.getOutputTokens(), usedModel, subAgent, agentCode,
                llmResponse.getInputTokens(), llmResponse.getOutputTokens(), latencyMs, queryResult);

        log.info("处理完成: latency={}ms, tokens(in={}, out={})",
                latencyMs, llmResponse.getInputTokens(), llmResponse.getOutputTokens());

        return ChatResponse.of(assistantMsg.getId(), sessionId, aiContent, subAgent.getCode(), latencyMs);
    }

    // ========== 核心流式流程 ==========

    private void doChatStreamCore(Long userId, String sessionId, String userMessage,
                                   SseEmitter emitter, String role, boolean isAdmin) {
        long startTime = System.currentTimeMillis();

        // Step 0: 限流 + 安全 + Token预算
        if (!rateLimiter.tryAcquire(userId)) {
            sseEmitterManager.sendError(emitter, "您的提问过于频繁，请稍后再试");
            return;
        }
        String inputRejection = guardrailsFilter.validateInput(userMessage);
        if (inputRejection != null) {
            log.warn("流式输入被安全护栏拦截: userId={}, reason={}", userId, inputRejection);
            sseEmitterManager.sendError(emitter, inputRejection);
            return;
        }
        if (!tokenBudget.hasRemaining(userId)) {
            sseEmitterManager.sendError(emitter, "您今日的AI对话额度已用完，请明天再试");
            return;
        }

        try {
            AiConversation conversation = conversationService.findOrCreate(sessionId, userId);
            AgentContext context = AgentContext.of(userId, sessionId, conversation.getId(), role);

            // 检查待确认操作
            if (handlePendingActionStream(userId, sessionId, userMessage, conversation, emitter)) {
                return;
            }

            List<Map<String, String>> history = memoryManager.loadHistory(sessionId);
            String summary = memoryManager.getSummary(sessionId);
            if (summary == null) summary = conversation.getSummary();

            // 黑板路径 —— 复杂多Agent查询
            if (taskDecomposer.isComplexQuery(userMessage)) {
                if (executeBlackboardStream(userId, sessionId, userMessage, conversation, context,
                        history, summary, startTime, isAdmin, emitter)) {
                    return;
                }
            }

            // 意图路由（快速路径：单Agent）
            String lastAgentCode = getLastAgentCode(sessionId);
            String agentCode = isAdmin
                    ? adminIntentRouter.route(userMessage, lastAgentCode)
                    : intentRouter.route(userMessage, lastAgentCode);
            SubAgent subAgent = subAgentRegistry.getOrDefault(agentCode);
            sseEmitterManager.sendAgentInfo(emitter, subAgent.getCode());

            // 执行子代理
            AgentQueryResult queryResult = executeAgent(subAgent, userMessage, context);
            String userProfile = userMemoryService.buildUserProfile(userId);
            String systemPrompt = promptAssembler.assemble(context, subAgent, queryResult, summary, userProfile);

            // 流式 LLM（主→辅降级链）
            StreamResult streamResult;
            String usedModel;
            try {
                streamResult = llmStreamHandler.streamChat(emitter, systemPrompt, history, userMessage);
                usedModel = llmProperties.getPrimaryConfig().getModel();
            } catch (Exception streamEx) {
                log.warn("流式主模型失败，降级到辅助模型: {}", streamEx.getMessage());
                streamResult = llmStreamHandler.streamChatWithConfig(
                        llmProperties.getAuxiliaryConfig(), emitter, systemPrompt, history, userMessage);
                usedModel = llmProperties.getAuxiliaryConfig().getModel();
            }
            String aiContent = guardrailsFilter.sanitizeOutput(streamResult.getContent());
            int latencyMs = (int) (System.currentTimeMillis() - startTime);

            // 存储 & 后处理
            AiMessage assistantMsg = saveAndPostProcess(
                    conversation, sessionId, userId, userMessage, aiContent,
                    streamResult.getEstimatedOutputTokens(), usedModel, subAgent, agentCode,
                    streamResult.getEstimatedInputTokens(), streamResult.getEstimatedOutputTokens(),
                    latencyMs, queryResult);

            log.info("流式处理完成: latency={}ms, tokens(in≈{}, out≈{})",
                    latencyMs, streamResult.getEstimatedInputTokens(), streamResult.getEstimatedOutputTokens());

            sseEmitterManager.sendDone(emitter, assistantMsg.getId(), latencyMs, usedModel);

        } catch (Exception e) {
            log.error("流式聊天异常: {}", e.getMessage(), e);
            sseEmitterManager.sendError(emitter, "AI 服务暂时不可用");
        }
    }

    // ========== 抽取的公共方法 ==========

    /** 同步路径: 处理待确认操作，返回非null表示已处理 */
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

    /** 流式路径: 处理待确认操作，返回true表示已处理 */
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

    /** 子代理执行（带超时保护） */
    private AgentQueryResult executeAgent(SubAgent subAgent, String userMessage, AgentContext context) {
        try {
            AgentQueryResult result = CompletableFuture
                    .supplyAsync(() -> subAgent.execute(userMessage, context), agentExecutor)
                    .get(AGENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("子代理执行完成: agent={}, status={}", subAgent.getCode(), result.getStatus());
            return result;
        } catch (TimeoutException e) {
            log.warn("子代理执行超时({}s): agent={}", AGENT_TIMEOUT_SECONDS, subAgent.getCode());
            return AgentQueryResult.error(subAgent.getCode(), "查询超时，请稍后重试");
        } catch (Exception e) {
            log.error("子代理执行异常: agent={}, error={}", subAgent.getCode(), e.getMessage(), e);
            return AgentQueryResult.error(subAgent.getCode(), e.getMessage());
        }
    }

    /** 消息存储 + 记忆更新 + 标题生成 + 摘要 + Token消费 + 遥测 + PendingAction保存 */
    private AiMessage saveAndPostProcess(AiConversation conversation, String sessionId, Long userId,
                                          String userMessage, String aiContent, int outputTokens,
                                          String usedModel, SubAgent subAgent, String routedAgentCode,
                                          int inputTokens, int totalOutputTokens,
                                          int latencyMs, AgentQueryResult queryResult) {
        conversationService.saveMessage(conversation.getId(), sessionId, "user", userMessage, 0, null, null, null);
        AiMessage assistantMsg = conversationService.saveMessage(
                conversation.getId(), sessionId, "assistant",
                aiContent, outputTokens, usedModel, subAgent.getCode(), latencyMs);

        memoryManager.appendMessage(sessionId, "user", userMessage);
        memoryManager.appendMessage(sessionId, "assistant", aiContent);

        if (conversation.getMessageCount() == 0) {
            titleGenerator.generateTitle(conversation.getId(), userMessage);
        }
        memorySummarizer.summarizeIfNeeded(sessionId, conversation.getId());
        memoryExtractor.extractAndSave(userId, userMessage, aiContent);
        userMemoryService.invalidateCache(userId);
        tokenBudget.consume(userId, inputTokens + totalOutputTokens);

        String traceId = telemetryTracer.recordAndReturnTraceId(
                conversation.getId(), userId, userMessage,
                routedAgentCode, subAgent.getCode(), usedModel,
                inputTokens, totalOutputTokens, latencyMs);
        selfEvaluator.evaluate(traceId, userMessage, aiContent);

        saveLastAgentCode(sessionId, subAgent.getCode());

        if (queryResult.getPendingAction() != null) {
            actionExecutor.savePendingAction(sessionId, queryResult.getPendingAction());
        }

        return assistantMsg;
    }

    // ========== 黑板路径执行 ==========

    /**
     * 黑板路径（同步）：主脑分解 → 子智能体逐轮执行 → 收集结果 → 统一LLM回复
     * 返回 null 表示分解失败，应降级到快速路径
     */
    private ChatResponse executeBlackboardSync(Long userId, String sessionId, String userMessage,
                                                AiConversation conversation, AgentContext context,
                                                List<Map<String, String>> history, String summary,
                                                long startTime, boolean isAdmin) {
        // 主脑发牌
        TaskBoard board = taskDecomposer.decompose(userMessage, isAdmin);
        if (board == null) return null;
        log.info("黑板路径启动: {} 个任务", board.size());

        // 子智能体逐轮领取并执行任务
        for (int round = 0; round < BLACKBOARD_MAX_ROUNDS && !board.isAllDone(); round++) {
            List<TaskNode> readyTasks = board.ready();
            if (readyTasks.isEmpty()) break;

            // 并行执行同一轮中所有 READY 任务
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (TaskNode task : readyTasks) {
                task.setStatus(TaskNode.Status.RUNNING);
                futures.add(CompletableFuture.runAsync(() -> {
                    long taskStart = System.currentTimeMillis();
                    SubAgent agent = subAgentRegistry.getOrDefault(task.getAgentCode());
                    AgentQueryResult result = executeAgent(agent, task.getTaskQuery(), context);
                    int taskLatency = (int) (System.currentTimeMillis() - taskStart);
                    if (result.getStatus() == AgentQueryResult.Status.ERROR) {
                        board.fail(task.getId(), result.getData());
                    } else {
                        String resultData = result.getData() != null ? result.getData() : "";
                        board.close(task.getId(), resultData, taskLatency);
                    }
                }, agentExecutor));
            }
            // 等待本轮所有任务完成
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(AGENT_TIMEOUT_SECONDS * 2L, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("黑板轮次 {} 等待超时: {}", round, e.getMessage());
                break;
            }
        }

        // 收集所有结果
        String combinedResults = board.collectResults();
        if (combinedResults.isEmpty()) {
            log.warn("黑板路径: 所有任务均无结果，降级到快速路径");
            return null;
        }

        // 构建多Agent结果的 AgentQueryResult
        AgentQueryResult combinedQueryResult = AgentQueryResult.success(combinedResults, "blackboard");
        SubAgent generalAgent = subAgentRegistry.getOrDefault("general_assistant");
        String userProfile = userMemoryService.buildUserProfile(userId);
        String systemPrompt = promptAssembler.assemble(context, generalAgent, combinedQueryResult, summary, userProfile);

        // 调用 LLM（主→辅降级链）
        LlmResponse llmResponse;
        String usedModel;
        try {
            llmResponse = llmClient.chat(systemPrompt, history, userMessage);
            usedModel = llmProperties.getPrimaryConfig().getModel();
        } catch (Exception e) {
            log.warn("黑板路径主模型失败，降级辅助模型: {}", e.getMessage());
            try {
                llmResponse = llmClient.chatWithConfig(
                        llmProperties.getAuxiliaryConfig(), systemPrompt, history, userMessage);
                usedModel = llmProperties.getAuxiliaryConfig().getModel();
            } catch (Exception ex) {
                log.error("黑板路径辅助模型也失败: {}", ex.getMessage());
                return null; // 降级到快速路径
            }
        }

        String aiContent = guardrailsFilter.sanitizeOutput(llmResponse.getContent());
        int latencyMs = (int) (System.currentTimeMillis() - startTime);

        AiMessage assistantMsg = saveAndPostProcess(
                conversation, sessionId, userId, userMessage, aiContent,
                llmResponse.getOutputTokens(), usedModel, generalAgent, "blackboard",
                llmResponse.getInputTokens(), llmResponse.getOutputTokens(), latencyMs, combinedQueryResult);

        log.info("黑板路径完成: {} 个任务, latency={}ms", board.size(), latencyMs);
        return ChatResponse.of(assistantMsg.getId(), sessionId, aiContent, "blackboard", latencyMs);
    }

    /**
     * 黑板路径（流式）：主脑分解 → 子智能体逐轮执行 → 收集结果 → 流式LLM回复
     * 返回 false 表示分解失败，应降级到快速路径
     */
    private boolean executeBlackboardStream(Long userId, String sessionId, String userMessage,
                                             AiConversation conversation, AgentContext context,
                                             List<Map<String, String>> history, String summary,
                                             long startTime, boolean isAdmin, SseEmitter emitter) {
        TaskBoard board = taskDecomposer.decompose(userMessage, isAdmin);
        if (board == null) return false;
        log.info("黑板路径(流式)启动: {} 个任务", board.size());
        sseEmitterManager.sendAgentInfo(emitter, "blackboard");

        // 子智能体逐轮执行
        for (int round = 0; round < BLACKBOARD_MAX_ROUNDS && !board.isAllDone(); round++) {
            List<TaskNode> readyTasks = board.ready();
            if (readyTasks.isEmpty()) break;

            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (TaskNode task : readyTasks) {
                task.setStatus(TaskNode.Status.RUNNING);
                futures.add(CompletableFuture.runAsync(() -> {
                    long taskStart = System.currentTimeMillis();
                    SubAgent agent = subAgentRegistry.getOrDefault(task.getAgentCode());
                    AgentQueryResult result = executeAgent(agent, task.getTaskQuery(), context);
                    int taskLatency = (int) (System.currentTimeMillis() - taskStart);
                    if (result.getStatus() == AgentQueryResult.Status.ERROR) {
                        board.fail(task.getId(), result.getData());
                    } else {
                        board.close(task.getId(), result.getData() != null ? result.getData() : "", taskLatency);
                    }
                }, agentExecutor));
            }
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(AGENT_TIMEOUT_SECONDS * 2L, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("黑板流式轮次 {} 等待超时: {}", round, e.getMessage());
                break;
            }
        }

        String combinedResults = board.collectResults();
        if (combinedResults.isEmpty()) {
            log.warn("黑板路径(流式): 无结果，降级快速路径");
            return false;
        }

        AgentQueryResult combinedQueryResult = AgentQueryResult.success(combinedResults, "blackboard");
        SubAgent generalAgent = subAgentRegistry.getOrDefault("general_assistant");
        String userProfile = userMemoryService.buildUserProfile(userId);
        String systemPrompt = promptAssembler.assemble(context, generalAgent, combinedQueryResult, summary, userProfile);

        // 流式 LLM（主→辅降级链）
        StreamResult streamResult;
        String usedModel;
        try {
            streamResult = llmStreamHandler.streamChat(emitter, systemPrompt, history, userMessage);
            usedModel = llmProperties.getPrimaryConfig().getModel();
        } catch (Exception streamEx) {
            log.warn("黑板流式主模型失败，降级辅助: {}", streamEx.getMessage());
            try {
                streamResult = llmStreamHandler.streamChatWithConfig(
                        llmProperties.getAuxiliaryConfig(), emitter, systemPrompt, history, userMessage);
                usedModel = llmProperties.getAuxiliaryConfig().getModel();
            } catch (Exception ex) {
                log.error("黑板流式辅助模型也失败: {}", ex.getMessage());
                return false;
            }
        }

        String aiContent = guardrailsFilter.sanitizeOutput(streamResult.getContent());
        int latencyMs = (int) (System.currentTimeMillis() - startTime);

        AiMessage assistantMsg = saveAndPostProcess(
                conversation, sessionId, userId, userMessage, aiContent,
                streamResult.getEstimatedOutputTokens(), usedModel, generalAgent, "blackboard",
                streamResult.getEstimatedInputTokens(), streamResult.getEstimatedOutputTokens(),
                latencyMs, combinedQueryResult);

        log.info("黑板路径(流式)完成: {} 个任务, latency={}ms", board.size(), latencyMs);
        sseEmitterManager.sendDone(emitter, assistantMsg.getId(), latencyMs, usedModel);
        return true;
    }

    // ========== 上下文感知路由辅助方法 ==========

    private String getLastAgentCode(String sessionId) {
        try {
            return redisTemplate.opsForValue().get(LAST_AGENT_KEY_PREFIX + sessionId);
        } catch (Exception e) {
            log.debug("获取 lastAgentCode 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 检查用户AI访问权限（Ultra可禁用用户的AI）
     * @return null=允许访问，非null=拒绝消息
     */
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

    // ========== Ultra AI 专用流程 ==========

    private ChatResponse doUltraChatSync(Long adminId, String sessionId, String userMessage) {
        long startTime = System.currentTimeMillis();

        // Ultra 不受普通限流约束，但仍做安全过滤
        String inputRejection = guardrailsFilter.validateInput(userMessage);
        if (inputRejection != null) {
            log.warn("Ultra输入被安全护栏拦截: adminId={}, reason={}", adminId, inputRejection);
            return ChatResponse.of(null, sessionId, inputRejection, "guardrails", 0);
        }

        AiConversation conversation = conversationService.findOrCreate(sessionId, adminId);
        AgentContext context = AgentContext.of(adminId, sessionId, conversation.getId(), "ultra");
        log.info("Ultra聊天开始: adminId={}, sessionId={}", adminId, sessionId);

        // 待确认操作
        ChatResponse pendingResult = handlePendingActionSync(adminId, sessionId, userMessage, conversation);
        if (pendingResult != null) return pendingResult;

        List<Map<String, String>> history = memoryManager.loadHistory(sessionId);
        String summary = memoryManager.getSummary(sessionId);
        if (summary == null) summary = conversation.getSummary();

        // ===== L2 黑板路径：复杂指令走多元代理并行 =====
        AgentQueryResult queryResult;
        String agentCode;
        SubAgent subAgent;

        if (ultraTaskDecomposer.isComplexUltraQuery(userMessage)) {
            TaskBoard l2Board = ultraTaskDecomposer.decompose(userMessage);
            if (l2Board != null) {
                log.info("Ultra L2黑板启动: {} 个元代理任务", l2Board.size());
                String boardResults = executeL2Blackboard(l2Board, context);
                agentCode = "ultra_l2_blackboard";
                subAgent = subAgentRegistry.getOrDefault("general_assistant");
                queryResult = AgentQueryResult.success(boardResults, agentCode);
            } else {
                // L2 分解失败，回退单Agent
                String lastAgentCode = getLastAgentCode(sessionId);
                agentCode = ultraIntentRouter.route(userMessage, lastAgentCode);
                subAgent = subAgentRegistry.getOrDefault(agentCode);
                queryResult = executeAgent(subAgent, userMessage, context);
            }
        } else {
            // 简单指令：单Agent快速路径
            String lastAgentCode = getLastAgentCode(sessionId);
            agentCode = ultraIntentRouter.route(userMessage, lastAgentCode);
            subAgent = subAgentRegistry.getOrDefault(agentCode);
            log.info("Ultra意图路由: '{}' → {} ({})", userMessage, subAgent.getCode(), subAgent.getName());
            queryResult = executeAgent(subAgent, userMessage, context);
        }

        // 组装 Prompt（含 L4 系统记忆）
        String systemMemory = systemMemoryService.buildSystemMemoryPrompt();
        String systemPrompt = promptAssembler.assemble(context, subAgent, queryResult, summary, null, systemMemory);

        // 调用 LLM
        LlmResponse llmResponse;
        String usedModel;
        try {
            llmResponse = llmClient.chat(systemPrompt, history, userMessage);
            usedModel = llmProperties.getPrimaryConfig().getModel();
        } catch (Exception e) {
            log.warn("Ultra主模型失败，降级辅助: {}", e.getMessage());
            try {
                llmResponse = llmClient.chatWithConfig(
                        llmProperties.getAuxiliaryConfig(), systemPrompt, history, userMessage);
                usedModel = llmProperties.getAuxiliaryConfig().getModel();
            } catch (Exception ex) {
                log.error("Ultra辅助模型也失败: {}", ex.getMessage(), ex);
                String errorContent = "Ultra AI 服务暂时不可用，请稍后再试。";
                AiMessage errMsg = conversationService.saveMessage(
                        conversation.getId(), sessionId, "assistant", errorContent, 0,
                        llmProperties.getPrimaryConfig().getModel(), subAgent.getCode(), 0);
                return ChatResponse.of(errMsg.getId(), sessionId, errorContent, subAgent.getCode(), 0);
            }
        }

        String aiContent = guardrailsFilter.sanitizeOutput(llmResponse.getContent());
        int latencyMs = (int) (System.currentTimeMillis() - startTime);

        AiMessage assistantMsg = saveAndPostProcess(
                conversation, sessionId, adminId, userMessage, aiContent,
                llmResponse.getOutputTokens(), usedModel, subAgent, agentCode,
                llmResponse.getInputTokens(), llmResponse.getOutputTokens(), latencyMs, queryResult);

        log.info("Ultra处理完成: latency={}ms, agent={}", latencyMs, subAgent.getCode());
        return ChatResponse.of(assistantMsg.getId(), sessionId, aiContent, subAgent.getCode(), latencyMs);
    }

    private void doUltraChatStreamCore(Long adminId, String sessionId, String userMessage, SseEmitter emitter) {
        long startTime = System.currentTimeMillis();

        String inputRejection = guardrailsFilter.validateInput(userMessage);
        if (inputRejection != null) {
            sseEmitterManager.sendError(emitter, inputRejection);
            return;
        }

        try {
            AiConversation conversation = conversationService.findOrCreate(sessionId, adminId);
            AgentContext context = AgentContext.of(adminId, sessionId, conversation.getId(), "ultra");

            if (handlePendingActionStream(adminId, sessionId, userMessage, conversation, emitter)) {
                return;
            }

            List<Map<String, String>> history = memoryManager.loadHistory(sessionId);
            String summary = memoryManager.getSummary(sessionId);
            if (summary == null) summary = conversation.getSummary();

            // ===== L2 黑板路径 =====
            AgentQueryResult queryResult;
            String agentCode;
            SubAgent subAgent;

            if (ultraTaskDecomposer.isComplexUltraQuery(userMessage)) {
                TaskBoard l2Board = ultraTaskDecomposer.decompose(userMessage);
                if (l2Board != null) {
                    log.info("Ultra流式 L2黑板启动: {} 个元代理任务", l2Board.size());
                    sseEmitterManager.sendAgentInfo(emitter, "ultra_l2_blackboard");
                    String boardResults = executeL2Blackboard(l2Board, context, emitter);
                    agentCode = "ultra_l2_blackboard";
                    subAgent = subAgentRegistry.getOrDefault("general_assistant");
                    queryResult = AgentQueryResult.success(boardResults, agentCode);
                } else {
                    String lastAgentCode = getLastAgentCode(sessionId);
                    agentCode = ultraIntentRouter.route(userMessage, lastAgentCode);
                    subAgent = subAgentRegistry.getOrDefault(agentCode);
                    sseEmitterManager.sendAgentInfo(emitter, subAgent.getCode());
                    queryResult = executeAgent(subAgent, userMessage, context);
                }
            } else {
                String lastAgentCode = getLastAgentCode(sessionId);
                agentCode = ultraIntentRouter.route(userMessage, lastAgentCode);
                subAgent = subAgentRegistry.getOrDefault(agentCode);
                sseEmitterManager.sendAgentInfo(emitter, subAgent.getCode());
                queryResult = executeAgent(subAgent, userMessage, context);
            }

            String systemMemory = systemMemoryService.buildSystemMemoryPrompt();
            String systemPrompt = promptAssembler.assemble(context, subAgent, queryResult, summary, null, systemMemory);

            // 流式 LLM
            StreamResult streamResult;
            String usedModel;
            try {
                streamResult = llmStreamHandler.streamChat(emitter, systemPrompt, history, userMessage);
                usedModel = llmProperties.getPrimaryConfig().getModel();
            } catch (Exception streamEx) {
                log.warn("Ultra流式主模型失败，降级辅助: {}", streamEx.getMessage());
                streamResult = llmStreamHandler.streamChatWithConfig(
                        llmProperties.getAuxiliaryConfig(), emitter, systemPrompt, history, userMessage);
                usedModel = llmProperties.getAuxiliaryConfig().getModel();
            }

            String aiContent = guardrailsFilter.sanitizeOutput(streamResult.getContent());
            int latencyMs = (int) (System.currentTimeMillis() - startTime);

            AiMessage assistantMsg = saveAndPostProcess(
                    conversation, sessionId, adminId, userMessage, aiContent,
                    streamResult.getEstimatedOutputTokens(), usedModel, subAgent, agentCode,
                    streamResult.getEstimatedInputTokens(), streamResult.getEstimatedOutputTokens(),
                    latencyMs, queryResult);

            log.info("Ultra流式完成: latency={}ms, agent={}", latencyMs, subAgent.getCode());
            sseEmitterManager.sendDone(emitter, assistantMsg.getId(), latencyMs, usedModel);

        } catch (Exception e) {
            log.error("Ultra流式聊天异常: {}", e.getMessage(), e);
            sseEmitterManager.sendError(emitter, "Ultra AI 服务暂时不可用");
        }
    }

    // ========== L2 黑板执行引擎 ==========

    /** 同步场景（无 SSE 进度推送） */
    private String executeL2Blackboard(TaskBoard l2Board, AgentContext context) {
        return executeL2Blackboard(l2Board, context, null);
    }

    /**
     * 执行 L2 黑板上的多元代理任务。
     * 元代理（user_ai_meta / admin_ai_meta）内部会启动各自的 L1 流程。
     * Ultra 专属代理直接执行。
     *
     * @param emitter 非 null 时会通过 SSE 推送 L2 任务进度事件
     */
    private String executeL2Blackboard(TaskBoard l2Board, AgentContext context, SseEmitter emitter) {
        if (emitter != null) {
            sseEmitterManager.sendL2BoardStatus(emitter, "started", l2Board.size());
            // 发送每个任务的初始状态
            for (TaskNode node : l2Board.getAllNodes()) {
                sseEmitterManager.sendL2TaskProgress(emitter, node.getId(), node.getAgentCode(),
                        "pending", node.getTaskQuery());
            }
        }

        for (int round = 0; round < BLACKBOARD_MAX_ROUNDS && !l2Board.isAllDone(); round++) {
            List<TaskNode> readyTasks = l2Board.ready();
            if (readyTasks.isEmpty()) {
                log.debug("L2黑板 round {} 无就绪任务", round);
                break;
            }

            log.info("L2黑板 round {}: {} 个就绪任务", round, readyTasks.size());
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (TaskNode task : readyTasks) {
                task.setStatus(TaskNode.Status.RUNNING);
                if (emitter != null) {
                    sseEmitterManager.sendL2TaskProgress(emitter, task.getId(), task.getAgentCode(),
                            "running", task.getTaskQuery());
                }
                futures.add(CompletableFuture.runAsync(() -> {
                    if (l2Board.isCancelled()) return;
                    long start = System.currentTimeMillis();
                    try {
                        SubAgent metaAgent = subAgentRegistry.getOrDefault(task.getAgentCode());
                        AgentContext l2Context = context.withL2(task.getTaskQuery(), task.getId(), task.getAgentCode());
                        log.info("L2黑板执行: [{}] → {} ({})", task.getId(), metaAgent.getCode(), task.getTaskQuery());
                        AgentQueryResult result = metaAgent.execute(task.getTaskQuery(), l2Context);
                        int latency = (int) (System.currentTimeMillis() - start);
                        if (result.getStatus() == AgentQueryResult.Status.ERROR) {
                            l2Board.fail(task.getId(), result.getData());
                            if (emitter != null) {
                                sseEmitterManager.sendL2TaskProgress(emitter, task.getId(),
                                        task.getAgentCode(), "failed", task.getTaskQuery());
                            }
                        } else {
                            l2Board.close(task.getId(), result.getData() != null ? result.getData() : "", latency);
                            if (emitter != null) {
                                sseEmitterManager.sendL2TaskProgress(emitter, task.getId(),
                                        task.getAgentCode(), "done", task.getTaskQuery());
                            }
                        }
                    } catch (Exception e) {
                        log.error("L2黑板任务 [{}] 异常: {}", task.getId(), e.getMessage(), e);
                        l2Board.fail(task.getId(), "执行异常: " + e.getMessage());
                        if (emitter != null) {
                            sseEmitterManager.sendL2TaskProgress(emitter, task.getId(),
                                    task.getAgentCode(), "failed", task.getTaskQuery());
                        }
                    }
                }, l2BoardExecutor));
            }

            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(AGENT_TIMEOUT_SECONDS * 3L, TimeUnit.SECONDS);
            } catch (TimeoutException te) {
                log.warn("L2黑板 round {} 超时，取消剩余任务", round);
                l2Board.cancel();
                break;
            } catch (Exception e) {
                log.warn("L2黑板 round {} 异常: {}", round, e.getMessage());
                l2Board.cancel();
                break;
            }
        }

        if (emitter != null) {
            sseEmitterManager.sendL2BoardStatus(emitter, "completed", l2Board.size());
        }

        String results = l2Board.collectResults();
        log.info("L2黑板执行完毕:\n{}", l2Board);
        return results;
    }

    /** 异步刷新用户在线心跳 */
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
}

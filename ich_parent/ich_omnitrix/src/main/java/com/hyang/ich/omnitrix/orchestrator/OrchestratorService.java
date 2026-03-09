package com.hyang.ich.omnitrix.orchestrator;

import com.hyang.ich.omnitrix.agent.AgentContext;
import com.hyang.ich.omnitrix.agent.SubAgent;
import com.hyang.ich.omnitrix.agent.SubAgentRegistry;
import com.hyang.ich.omnitrix.blackboard.TaskBoard;
import com.hyang.ich.omnitrix.blackboard.TaskDecomposer;
import com.hyang.ich.omnitrix.blackboard.TaskNode;
import com.hyang.ich.omnitrix.dto.AgentQueryResult;
import com.hyang.ich.omnitrix.dto.ChatResponse;
import com.hyang.ich.omnitrix.dto.PendingAction;
import com.hyang.ich.omnitrix.dto.StreamResult;
import com.hyang.ich.omnitrix.entity.AiConversation;
import com.hyang.ich.omnitrix.entity.AiMessage;
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
import com.hyang.ich.omnitrix.service.ConversationService;
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
    private final SubAgentRegistry subAgentRegistry;
    private final PromptAssembler promptAssembler;
    private final LlmClient llmClient;
    private final LlmProperties llmProperties;
    private final ConversationService conversationService;
    private final ChatMemoryManager memoryManager;
    private final MemoryExtractor memoryExtractor;
    private final MemorySummarizer memorySummarizer;
    private final UserMemoryService userMemoryService;
    private final LlmStreamHandler llmStreamHandler;
    private final SseEmitterManager sseEmitterManager;
    private final TelemetryTracer telemetryTracer;
    private final AiSelfEvaluator selfEvaluator;
    private final TitleGenerator titleGenerator;
    private final StringRedisTemplate redisTemplate;
    private final Executor agentExecutor;
    private final TaskDecomposer taskDecomposer;

    public OrchestratorService(ActionExecutor actionExecutor,
                               GuardrailsFilter guardrailsFilter,
                               RateLimiter rateLimiter,
                               TokenBudget tokenBudget,
                               IntentRouter intentRouter,
                               AdminIntentRouter adminIntentRouter,
                               SubAgentRegistry subAgentRegistry,
                               PromptAssembler promptAssembler,
                               LlmClient llmClient,
                               LlmProperties llmProperties,
                               ConversationService conversationService,
                               ChatMemoryManager memoryManager,
                               MemoryExtractor memoryExtractor,
                               MemorySummarizer memorySummarizer,
                               UserMemoryService userMemoryService,
                               LlmStreamHandler llmStreamHandler,
                               SseEmitterManager sseEmitterManager,
                               TelemetryTracer telemetryTracer,
                               AiSelfEvaluator selfEvaluator,
                               TitleGenerator titleGenerator,
                               StringRedisTemplate redisTemplate,
                               TaskDecomposer taskDecomposer,
                               @org.springframework.beans.factory.annotation.Qualifier("aiAsyncExecutor") Executor agentExecutor) {
        this.actionExecutor = actionExecutor;
        this.guardrailsFilter = guardrailsFilter;
        this.rateLimiter = rateLimiter;
        this.tokenBudget = tokenBudget;
        this.intentRouter = intentRouter;
        this.adminIntentRouter = adminIntentRouter;
        this.subAgentRegistry = subAgentRegistry;
        this.promptAssembler = promptAssembler;
        this.llmClient = llmClient;
        this.llmProperties = llmProperties;
        this.conversationService = conversationService;
        this.memoryManager = memoryManager;
        this.memoryExtractor = memoryExtractor;
        this.memorySummarizer = memorySummarizer;
        this.userMemoryService = userMemoryService;
        this.llmStreamHandler = llmStreamHandler;
        this.sseEmitterManager = sseEmitterManager;
        this.telemetryTracer = telemetryTracer;
        this.selfEvaluator = selfEvaluator;
        this.titleGenerator = titleGenerator;
        this.redisTemplate = redisTemplate;
        this.taskDecomposer = taskDecomposer;
        this.agentExecutor = agentExecutor;
    }

    // ========== 公共入口 ==========

    /** 用户同步聊天 */
    public ChatResponse chat(Long userId, String sessionId, String userMessage) {
        return doChatSync(userId, sessionId, userMessage, "user", false);
    }

    /** 用户流式聊天 (SSE) */
    public void chatStream(Long userId, String sessionId, String userMessage, SseEmitter emitter) {
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

    private void saveLastAgentCode(String sessionId, String agentCode) {
        try {
            redisTemplate.opsForValue().set(
                    LAST_AGENT_KEY_PREFIX + sessionId, agentCode, 1, TimeUnit.HOURS);
        } catch (Exception e) {
            log.debug("保存 lastAgentCode 失败: {}", e.getMessage());
        }
    }
}

# Part 1: 架构与编排

## 1. 系统概述与设计理念

### 1.1 项目背景

Omnitrix AI 是基于大语言模型的企业级智能对话系统，服务于非物质文化遗产传承平台。支持用户端、管理员端和超级管理员（Ultra）三种角色。

系统实现了 V1/V2 双模式架构：
- **V1**：SubAgent + IntentRouter + Blackboard 传统多智能体协作
- **V2**：LangChain4j AiService + @Tool 注解的 Function Calling 自动路由

通过 `omnitrix.llm.langchain4j-enabled` 配置开关切换，V1 作为稳定降级路径保留。

### 1.2 核心设计理念

- **双模式渐进升级**：Feature Flag 控制 V1/V2 切换，零风险回滚
- **三层执行路径（V1）**：Quick Path / Blackboard / Direct Skill
- **Function Calling 自动路由（V2）**：LLM 自动选择 @Tool 方法
- **Skill 动态增强**：动态加载专业 Skills 注入 System Prompt
- **多层次记忆**：L1 会话记忆 → L2 对话摘要 → L3 用户画像 → L4 系统记忆
- **企业级可靠性**：熔断器 + 限流 + Token预算 + 安全护栏 + 主辅模型降级

### 1.3 技术选型

| 组件 | 技术 | 版本 |
|------|------|------|
| 运行时 | Java | 17 |
| 框架 | Spring Boot | 3.x |
| AI 框架 | LangChain4j | 0.35.0 |
| RPC | Apache Dubbo | 3.x |
| ORM | MyBatis | 3.x |
| 缓存 | Redis (Spring Data Redis) | — |
| 数据库 | MySQL | 8.x |
| 主 LLM | DeepSeek-V3 (SiliconFlow API) | — |
| 流式协议 | Server-Sent Events (SSE) | — |

---

## 2. 整体架构设计

### 2.1 系统架构图

```
┌───────────────────────────────────────────────────────────────┐
│                        客户端层                                │
│  用户App · 管理后台 · Ultra端 · API调用 · SSE流式             │
└──────────────────────┬────────────────────────────────────────┘
                       ▼
┌───────────────────────────────────────────────────────────────┐
│                   Controller 层 (REST API)                     │
│  AiChatController · AiAdminController · AiUltraController     │
│  AiSkillController · AiAgentController · AiConfigController   │
└──────────────────────┬────────────────────────────────────────┘
                       ▼
┌───────────────────────────────────────────────────────────────┐
│              OrchestratorService (编排中心)                     │
│                                                               │
│   langchain4j-enabled=false  │  langchain4j-enabled=true     │
│   ┌─────────────────────┐    │  ┌─────────────────────┐      │
│   │  V1: SubAgent +     │    │  │  V2: LangChain4j    │      │
│   │  IntentRouter +     │◄──►│  │  MasterBrain +      │      │
│   │  Blackboard         │    │  │  @Tool              │      │
│   └─────────────────────┘    │  └─────────────────────┘      │
└──────────────────────┬────────────────────────────────────────┘
                       ▼
┌───────────────────────────────────────────────────────────────┐
│                   基础设施层 (Infrastructure)                   │
│  LlmClient · LlmStreamHandler · CircuitBreaker               │
│  GuardrailsFilter · RateLimiter · TokenBudget                 │
│  ChatMemoryManager · RedisChatMemoryStore · MemoryExtractor   │
│  PromptAssembler · SseEmitterManager · TelemetryTracer        │
└──────────────────────┬────────────────────────────────────────┘
                       ▼
┌───────────────────────────────────────────────────────────────┐
│              外部服务层 (Dubbo RPC)                             │
│  ContentService · ProductService · OrderService               │
│  UserService · SystemService · BrowseHistoryService           │
└───────────────────────────────────────────────────────────────┘
```

### 2.2 V1 请求处理流程

```
用户请求 → 限流 → 安全护栏 → Token预算 → 对话管理 → 待确认操作检查
    → MasterBrain决策(blackboard/quick_path) 或 IntentRouter路由
    → Agent执行 → Prompt组装(含Skills+用户画像) → LLM调用(主→辅降级)
    → 输出安全过滤 → 存储+后处理(记忆/摘要/遥测) → 返回
```

### 2.3 V2 请求处理流程

```
用户请求 → 限流 → 安全护栏 → Token预算 → 待确认操作检查
    → AiRequestContext.set(userId, sessionId) [ThreadLocal]
    → MasterBrainFactory.buildXxxBrain() 按角色构建AiService
    → brain.chat(msg, skills, profile)
    → LLM自动选择@Tool → 执行工具 → LLM综合回答
    → AiRequestContext.clear()
    → 输出安全过滤 → 存储+后处理 → 返回
```

---

## 3. 项目结构与文件清单

```
src/main/java/com/hyang/ich/omnitrix/
├── config/                         # 7个配置类
│   ├── AsyncConfig.java            # 线程池(aiAsyncExecutor + l2BoardExecutor)
│   ├── DubboConsumerConfig.java    # Dubbo消费者(6个服务引用)
│   ├── LangChain4jConfig.java      # LangChain4j模型Bean配置
│   ├── RedisConfig.java            # Redis序列化
│   ├── ScheduledTaskConfig.java    # 定时任务
│   ├── SkillConfigCyclicReferenceSetup.java
│   └── WebMvcConfig.java
├── controller/                     # 6个REST控制器
│   ├── AiChatController.java       # 用户聊天(同步/流式/重新生成/导出)
│   ├── AiAdminController.java      # 管理员聊天+健康检查
│   ├── AiUltraController.java      # Ultra认证+聊天
│   ├── AiAgentController.java      # Agent配置CRUD
│   ├── AiConfigController.java     # Prompt/系统配置
│   └── AiSkillController.java      # Skill管理
├── orchestrator/                   # 编排层
│   ├── OrchestratorService.java    # ★核心编排(V1+V2, ~1758行)
│   ├── ActionExecutor.java         # 待确认操作执行器
│   ├── IntentRouter.java           # 用户意图路由(V1)
│   ├── AdminIntentRouter.java      # 管理员意图路由(V1)
│   └── UltraIntentRouter.java      # Ultra意图路由(V1)
├── brain/                          # ★V2 LangChain4j层
│   ├── UserMasterBrain.java        # 用户AiService接口
│   ├── AdminMasterBrain.java       # 管理员AiService接口
│   ├── UltraMasterBrain.java       # Ultra AiService接口
│   ├── MasterBrainFactory.java     # AiService实例工厂
│   ├── AiRequestContext.java       # ThreadLocal上下文
│   └── tools/                      # 9个@Tool工具类
│       ├── ContentTools.java       # 非遗内容(271行)
│       ├── CommerceTools.java      # 文创商城(271行)
│       ├── UserTools.java          # 用户服务(162行)
│       ├── AdminTools.java         # 管理员(321行)
│       ├── KnowledgeTools.java     # 知识库(44行)
│       ├── RecommendTools.java     # 推荐(91行)
│       ├── BrowseHistoryTools.java # 浏览历史(166行)
│       ├── UltraTools.java         # Ultra全功能(536行)
│       └── ToolRegistrationConfig.java
├── agent/                          # V1 Agent层
│   ├── SubAgent.java, SubAgentRegistry.java, AgentContext.java, AgentUtils.java
│   ├── tool/                       # ToolRegistry, AgentTool, ToolCallResult, ToolSelector, HintGenerator
│   └── impl/                       # 19个SubAgent实现
├── blackboard/                     # V1黑板架构
│   ├── TaskBoard.java, TaskNode.java, TaskDecomposer.java
│   ├── UltraTaskDecomposer.java, MasterBrain.java, UltraMasterBrain.java
├── infrastructure/
│   ├── guardrails/                 # GuardrailsFilter, RateLimiter, TokenBudget
│   ├── llm/                        # LlmProperties, LlmClient, LlmStreamHandler, LlmCircuitBreaker, LlmResponse
│   ├── memory/                     # ChatMemoryManager, RedisChatMemoryStore, MemoryExtractor, MemorySummarizer
│   ├── prompt/                     # PromptAssembler, PromptManager, PromptTemplate, HeritageSkillPrompt
│   ├── skill/                      # Skill, SkillPromptConfig, SkillSelector
│   ├── sse/                        # SseEmitterManager
│   └── telemetry/                  # TelemetryTracer, AiSelfEvaluator, CostTracker, TitleGenerator
├── service/                        # ConversationService, KnowledgeService, RecommendService, SkillConfigService,
│                                   # UserMemoryService, SystemMemoryService, UltraAuthService, AgentConfigService
├── dto/                            # 11个DTO (ChatRequest/Response, AgentQueryResult, PendingAction等)
├── entity/                         # 12个实体 (AiConversation, AiMessage, AiUserMemory等)
└── mapper/                         # 12个MyBatis Mapper
```

---

## 4. 核心编排服务 OrchestratorService

**文件**: `orchestrator/OrchestratorService.java` (~1758行)

### 4.1 核心常量

```java
private static final String LAST_AGENT_KEY_PREFIX = "omnitrix:last_agent:";
private static final int AGENT_TIMEOUT_SECONDS = 30;
private static final int BLACKBOARD_MAX_ROUNDS = 5;
private static final int MAX_REPLANS = 1;
```

### 4.2 依赖注入 (32个)

ActionExecutor, GuardrailsFilter, RateLimiter, TokenBudget, IntentRouter, AdminIntentRouter, UltraIntentRouter, SubAgentRegistry, PromptAssembler, LlmClient, LlmProperties, ConversationService, ChatMemoryManager, MemoryExtractor, MemorySummarizer, UserMemoryService, SystemMemoryService, LlmStreamHandler, SseEmitterManager, TelemetryTracer, AiSelfEvaluator, CostTracker, TitleGenerator, StringRedisTemplate, TaskDecomposer, UltraTaskDecomposer, UltraMasterBrain(blackboard), MasterBrain(blackboard), AiUserAiConfigMapper, UserService, **MasterBrainFactory**(V2), agentExecutor, l2BoardExecutor

### 4.3 六个公共入口

每个方法通过 `llmProperties.isLangchain4jEnabled()` 决定走V1还是V2:

```java
// 用户同步聊天
public ChatResponse chat(Long userId, String sessionId, String userMessage) {
    String aiCheck = checkUserAiAccess(userId);  // Ultra可禁用用户AI
    if (aiCheck != null) return ChatResponse.of(null, sessionId, aiCheck, "access_denied", 0);
    refreshOnlineHeartbeat(userId);
    if (llmProperties.isLangchain4jEnabled()) {
        return doChatSyncV2(userId, sessionId, userMessage, "user");
    }
    return doChatSync(userId, sessionId, userMessage, "user", false);
}

// 用户流式聊天 (SSE)
public void chatStream(Long userId, String sessionId, String userMessage, SseEmitter emitter)

// 管理员同步/流式聊天
public ChatResponse adminChat(Long adminId, String sessionId, String userMessage)
public void adminChatStream(Long adminId, String sessionId, String userMessage, SseEmitter emitter)

// Ultra同步/流式聊天
public ChatResponse ultraChat(Long adminId, String sessionId, String userMessage)
public void ultraChatStream(Long adminId, String sessionId, String userMessage, SseEmitter emitter)
```

### 4.4 V1 同步核心流程 doChatSync()

```java
private ChatResponse doChatSync(Long userId, String sessionId, String userMessage,
                                 String role, boolean isAdmin) {
    long startTime = System.currentTimeMillis();

    // Step 0: 限流 + 安全 + Token预算
    if (!rateLimiter.tryAcquire(userId))
        return ChatResponse.of(null, sessionId, "您的提问过于频繁，请稍后再试", "rate_limit", 0);
    String inputRejection = guardrailsFilter.validateInput(userMessage);
    if (inputRejection != null)
        return ChatResponse.of(null, sessionId, inputRejection, "guardrails", 0);
    if (!tokenBudget.hasRemaining(userId))
        return ChatResponse.of(null, sessionId, "您今日的AI对话额度已用完，请明天再试", "token_budget", 0);

    // Step 1: 对话管理
    AiConversation conversation = conversationService.findOrCreate(sessionId, userId);
    AgentContext context = AgentContext.of(userId, sessionId, conversation.getId(), role, userMessage);

    // Step 1.5: 检查待确认操作
    ChatResponse pendingResult = handlePendingActionSync(userId, sessionId, userMessage, conversation);
    if (pendingResult != null) return pendingResult;

    // Step 2: 加载对话历史 + 摘要
    List<Map<String, String>> history = memoryManager.loadHistory(sessionId);
    String summary = memoryManager.getSummary(sessionId);
    if (summary == null) summary = conversation.getSummary();

    // Step 2.5: MasterBrain 智能决策
    if (masterBrain.shouldUseLlmBrain(userMessage)) {
        Decision decision = masterBrain.decide(userMessage, isAdmin);
        if (decision.isBlackboard()) {
            TaskBoard board = masterBrain.toTaskBoard(decision);
            return executeBlackboardWithBoardSync(..., board);
        } else {
            String agentCode = decision.getAgent();
            SubAgent subAgent = subAgentRegistry.getOrDefault(agentCode);
            return executeQuickPathSync(..., subAgent, agentCode);
        }
    } else if (taskDecomposer.isComplexQuery(userMessage)) {
        return executeBlackboardSync(...);
    }

    // Step 3: 意图路由（快速路径）
    String lastAgentCode = getLastAgentCode(sessionId);
    String agentCode = isAdmin
            ? adminIntentRouter.route(userMessage, lastAgentCode)
            : intentRouter.route(userMessage, lastAgentCode);
    SubAgent subAgent = subAgentRegistry.getOrDefault(agentCode);

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
        log.warn("主模型失败，降级到辅助模型: {}", e.getMessage());
        try {
            llmResponse = llmClient.chatWithConfig(
                    llmProperties.getAuxiliaryConfig(), systemPrompt, history, userMessage);
            usedModel = llmProperties.getAuxiliaryConfig().getModel();
        } catch (Exception ex) {
            // 双模型都失败
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
            llmResponse.getInputTokens(), llmResponse.getOutputTokens(),
            latencyMs, queryResult, context.getAgentDurationMs());

    return ChatResponse.of(assistantMsg.getId(), sessionId, aiContent, subAgent.getCode(), latencyMs);
}
```

### 4.5 V2 同步核心流程 doChatSyncV2()

```java
private ChatResponse doChatSyncV2(Long userId, String sessionId, String userMessage, String role) {
    long startTime = System.currentTimeMillis();
    boolean isUltra = "ultra".equals(role);

    // Ultra不受限流和Token预算约束
    if (!isUltra) {
        if (!rateLimiter.tryAcquire(userId))
            return ChatResponse.of(null, sessionId, "您的提问过于频繁，请稍后再试", "rate_limit", 0);
        if (!tokenBudget.hasRemaining(userId))
            return ChatResponse.of(null, sessionId, "您今日的AI对话额度已用完", "token_budget", 0);
    }
    String inputRejection = guardrailsFilter.validateInput(userMessage);
    if (inputRejection != null)
        return ChatResponse.of(null, sessionId, inputRejection, "guardrails", 0);

    AiConversation conversation = conversationService.findOrCreate(sessionId, userId);

    // 待确认操作检查
    ChatResponse pendingResult = handlePendingActionSync(userId, sessionId, userMessage, conversation);
    if (pendingResult != null) return pendingResult;

    String agentCode = "langchain4j_" + role;
    AiRequestContext.set(userId, sessionId);  // 设置ThreadLocal上下文
    try {
        String aiContent = invokeBrain(role, userId, sessionId, userMessage);
        aiContent = guardrailsFilter.sanitizeOutput(aiContent);
        int latencyMs = (int) (System.currentTimeMillis() - startTime);
        String usedModel = llmProperties.getPrimaryConfig().getModel();

        AiMessage assistantMsg = saveAndPostProcessV2(
                conversation, sessionId, userId, userMessage, aiContent,
                usedModel, agentCode, latencyMs);
        return ChatResponse.of(assistantMsg.getId(), sessionId, aiContent, agentCode, latencyMs);
    } catch (Exception e) {
        String errorContent = "AI 服务暂时不可用，请稍后再试。";
        AiMessage errMsg = conversationService.saveMessage(
                conversation.getId(), sessionId, "assistant", errorContent, 0,
                llmProperties.getPrimaryConfig().getModel(), agentCode, 0);
        return ChatResponse.of(errMsg.getId(), sessionId, errorContent, agentCode, 0);
    } finally {
        AiRequestContext.clear();
    }
}
```

### 4.6 V2 Brain调用 invokeBrain()

```java
private String invokeBrain(String role, Long userId, String sessionId, String userMessage) {
    String skills = masterBrainFactory.buildSkillsPrompt();
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
        default: throw new IllegalArgumentException("Unknown role: " + role);
    }
}
```

### 4.7 V2 后处理 saveAndPostProcessV2()

```java
private AiMessage saveAndPostProcessV2(AiConversation conversation, String sessionId,
        Long userId, String userMessage, String aiContent,
        String usedModel, String agentCode, int latencyMs) {
    // 1. 保存用户消息
    conversationService.saveMessage(conversation.getId(), sessionId, "user",
            userMessage, 0, null, null, null);

    // 2. 保存AI回复（粗估token: 中文约1.5字/token）
    int estimatedOutputTokens = Math.max(1, aiContent.length() * 2 / 3);
    AiMessage assistantMsg = conversationService.saveMessage(
            conversation.getId(), sessionId, "assistant",
            aiContent, estimatedOutputTokens, usedModel, agentCode, latencyMs);

    // 3. 对话记忆
    memoryManager.appendMessage(sessionId, "user", userMessage);
    memoryManager.appendMessage(sessionId, "assistant", aiContent);

    // 4. 首轮生成标题
    if (conversation.getMessageCount() == 0)
        titleGenerator.generateTitle(conversation.getId(), userMessage);

    // 5. 摘要 + 长期记忆提取 + 缓存失效
    memorySummarizer.summarizeIfNeeded(sessionId, conversation.getId());
    memoryExtractor.extractAndSave(userId, userMessage, aiContent);
    userMemoryService.invalidateCache(userId);

    // 6. Token预算消费
    int estimatedInputTokens = Math.max(1, userMessage.length() * 2 / 3);
    tokenBudget.consume(userId, estimatedInputTokens + estimatedOutputTokens);

    // 7. 遥测追踪 + AI自评 + 成本记录
    String traceId = telemetryTracer.recordAndReturnTraceId(
            conversation.getId(), userId, userMessage,
            agentCode, agentCode, usedModel,
            estimatedInputTokens, estimatedOutputTokens, latencyMs);
    selfEvaluator.evaluate(traceId, userMessage, aiContent);
    costTracker.recordCost(traceId, usedModel, estimatedInputTokens, estimatedOutputTokens);
    saveLastAgentCode(sessionId, agentCode);

    return assistantMsg;
}
```

### 4.8 V2 流式聊天 doChatStreamV2()

```java
private void doChatStreamV2(Long userId, String sessionId, String userMessage,
                             SseEmitter emitter, String role) {
    // 限流 + 安全检查（同步部分）...

    CompletableFuture.runAsync(() -> {
        AiRequestContext.set(userId, sessionId);
        try {
            String aiContent = invokeBrain(role, userId, sessionId, userMessage);
            aiContent = guardrailsFilter.sanitizeOutput(aiContent);

            // 分句推送到SSE，模拟流式效果
            String[] sentences = aiContent.split("(?<=[。！？\\n])");
            for (String s : sentences) {
                if (!s.isEmpty()) sseEmitterManager.sendChunk(emitter, s);
            }

            AiMessage assistantMsg = saveAndPostProcessV2(...);
            sseEmitterManager.sendDone(emitter, assistantMsg.getId(), latencyMs, usedModel);
        } catch (Exception e) {
            sseEmitterManager.sendError(emitter, "AI 服务暂时不可用");
        } finally {
            AiRequestContext.clear();
        }
    }, agentExecutor);
}
```

### 4.9 V1 流式核心流程 doChatStreamCore()

与V1同步流程类似，但增加了SSE事件推送：
- `sendAgentInfo` → Agent信息
- `sendAgentDispatch` → Agent调度过程
- `sendAgentResult` → Agent执行结果
- LLM流式调用通过 `LlmStreamHandler` 处理 `<think>` 标签分流
- `sendDone` → 完成信号（含model信息）

### 4.10 配置类详解

#### AsyncConfig

```java
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean("aiAsyncExecutor")
    public Executor aiAsyncExecutor() {
        // core=4, max=10, queue=100, CallerRunsPolicy, 优雅关闭(30s)
    }

    @Bean("l2BoardExecutor")
    public Executor l2BoardExecutor() {
        // core=2, max=6, queue=20, CallerRunsPolicy
        // L2黑板专用，与普通AI线程池隔离
    }
}
```

#### DubboConsumerConfig

```java
@Configuration
public class DubboConsumerConfig {
    @DubboReference(check = false, timeout = 5000, retries = 1)
    private ContentService contentService;    // 内容服务
    private ProductService productService;    // 商品服务
    private UserService userService;          // 用户服务
    private OrderService orderService;        // 订单服务
    private SystemService systemService;      // 系统服务
    private BrowseHistoryService browseHistoryService; // 浏览历史
    // 每个都暴露为 @Bean
}
```

#### LangChain4jConfig

```java
@Configuration
public class LangChain4jConfig {
    @Bean
    public ChatLanguageModel chatLanguageModel(LlmProperties props) {
        return OpenAiChatModel.builder()
                .baseUrl(convertBaseUrl(props.getPrimaryConfig().getApiUrl()))
                .modelName(props.getPrimaryConfig().getModel())
                .apiKey(props.getPrimaryConfig().getApiKey())
                .timeout(Duration.ofSeconds(props.getPrimaryConfig().getTimeoutSeconds()))
                .maxTokens(props.getPrimaryConfig().getMaxTokens())
                .temperature(props.getPrimaryConfig().getTemperature())
                .build();
    }

    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel(LlmProperties props) {
        return OpenAiStreamingChatModel.builder()
                // 同样参数，但使用流式模型
                .build();
    }

    // baseUrl转换：SiliconFlow API需要将 /chat/completions 去掉
    private String convertBaseUrl(String apiUrl) {
        if (apiUrl.endsWith("/chat/completions")) {
            return apiUrl.replace("/chat/completions", "");
        }
        return apiUrl;
    }
}
```

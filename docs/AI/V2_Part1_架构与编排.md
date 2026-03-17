# Part 1: 架构与编排

## 1. 系统概述与设计理念

### 1.1 项目背景

Omnitrix AI 是基于大语言模型的企业级智能对话系统，服务于非物质文化遗产传承平台。支持用户端、管理员端和超级管理员（Ultra）三种角色。

系统采用纯 V2 架构（已完成 V1→V2 全量重构，V1 代码已删除）：
- **MasterBrain**：LangChain4j AiService + @Tool 注解的 Function Calling 自动路由
- **SubBrain 子脑编排**：Ultra 通过 SubBrainTools 将 UserMasterBrain / AdminMasterBrain 作为子脑调用，参考 Kortex AI 的 Master Brain + Sub-Agent 编排模式

### 1.2 核心设计理念

- **Function Calling 自动路由**：LLM 自动选择 @Tool 方法，无需手工意图路由
- **SubBrain 子脑编排（Ultra）**：Ultra LLM 统筹编排，委托 UserAI / AdminAI 子脑执行具体操作
- **Skill 动态增强**：动态加载专业 Skills 注入 System Prompt
- **多层次记忆**：L1 会话记忆 → L2 对话摘要 → L3 用户画像 → L4 系统记忆
- **企业级可靠性**：熔断器 + 限流 + Token预算 + 安全护栏 + 主辅模型降级
- **真实 Token 计数**：通过 Result<String>.tokenUsage() 获取真实输入/输出 token 数

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
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  MasterBrainFactory → 按角色构建 AiService 实例        │  │
│  │                                                         │  │
│  │  User → UserMasterBrain + [ContentTools, Commerce...]  │  │
│  │  Admin → AdminMasterBrain + [AdminTools, Content...]   │  │
│  │  Ultra → UltraMasterBrain + [UltraTools, SubBrainTools]│  │
│  │               │                                          │  │
│  │               ▼ SubBrain 子脑编排                       │  │
│  │       callUserAI → UserMasterBrain(独立 session)     │  │
│  │       callAdminAI → AdminMasterBrain(独立 session)   │  │
│  └─────────────────────────────────────────────────────────┘  │
└──────────────────────┬────────────────────────────────────────┘
                       ▼
┌───────────────────────────────────────────────────────────────┐
│                   基础设施层 (Infrastructure)                   │
│  LlmCircuitBreaker · GuardrailsFilter · RateLimiter           │
│  TokenBudget · ChatMemoryManager · MemoryExtractor            │
│  MemorySummarizer · SseEmitterManager · TelemetryTracer       │
│  CostTracker · AiSelfEvaluator · TitleGenerator               │
└──────────────────────┬────────────────────────────────────────┘
                       ▼
┌───────────────────────────────────────────────────────────────┐
│              外部服务层 (Dubbo RPC)                             │
│  ContentService · ProductService · OrderService               │
│  UserService · SystemService · BrowseHistoryService           │
└───────────────────────────────────────────────────────────────┘
```

### 2.2 请求处理流程（User / Admin）

```
用户请求 → 限流 → 安全护栏 → Token预算 → 待确认操作检查
    → AiRequestContext.set(userId, sessionId) [ThreadLocal]
    → SubBrainTools.resetCallCounter()
    → MasterBrainFactory.buildXxxBrain() 按角色构建AiService
    → brain.chat(msg, skills, profile) → Result<String>
    → LLM自动选择@Tool → 执行工具 → LLM综合回答
    → 主模型失败时自动降级到辅助模型
    → SubBrainTools.clearCallCounter()
    → AiRequestContext.clear()
    → 输出安全过滤 → 存储+后处理(记忆/摘要/遥测/成本) → 返回
```

### 2.3 Ultra 子脑编排流程

```
Ultra请求 → 安全护栏 → 待确认操作检查
    → UltraMasterBrain.chat(msg, skills)
    → LLM 决策调用哪个工具：
       │
       ├─ UltraTools.直属工具（跨用户查询、封禁用户、Skill管理等）
       │
       ├─ SubBrainTools.callUserAI("查询内容|目标用户")
       │    → 保存原始上下文
       │    → AiRequestContext.set(目标userId, subSessionId)
       │    → UserMasterBrain.chat() → 多轮 Function Calling
       │    → PendingAction 传递: subSession → ultraSession
       │    → 恢复原始上下文
       │    → SubBrainResultWrapper 包装结果(XML + ACTION/REASON/DO_NOT hint)
       │
       └─ SubBrainTools.callAdminAI("管理操作内容")
            → 同上，但不切换 userId
```

---

## 3. 项目结构与文件清单

```
src/main/java/com/hyang/ich/omnitrix/
├── config/                         # 配置类
│   ├── AsyncConfig.java            # 线程池(aiAsyncExecutor)
│   ├── DubboConsumerConfig.java    # Dubbo消费者(6个服务引用)
│   ├── LangChain4jConfig.java      # LangChain4j模型Bean配置(主模型+辅助模型+流式模型)
│   ├── RedisConfig.java            # Redis序列化
│   ├── ScheduledTaskConfig.java    # 定时任务
│   ├── SkillConfigCyclicReferenceSetup.java
│   └── WebMvcConfig.java
├── controller/                     # 6个REST控制器
│   ├── AiChatController.java       # 用户聊天(同步/流式/重新生成/导出/反馈)
│   ├── AiAdminController.java      # 管理员聊天+仪表板+Agent性能统计
│   ├── AiUltraController.java      # Ultra认证+聊天
│   ├── AiAgentController.java      # Agent/Tool配置CRUD
│   ├── AiConfigController.java     # Prompt版本管理/系统配置
│   └── AiSkillController.java      # Skill管理
├── orchestrator/                   # 编排层
│   ├── OrchestratorService.java    # ★核心编排(纯V2, ~620行)
│   └── ActionExecutor.java         # 待确认操作执行器(17种actionType)
├── brain/                          # ★LangChain4j MasterBrain层
│   ├── UserMasterBrain.java        # 用户AiService接口(chat + chatStream)
│   ├── AdminMasterBrain.java       # 管理员AiService接口
│   ├── UltraMasterBrain.java       # Ultra AiService接口(含子脑编排SystemMessage)
│   ├── MasterBrainFactory.java     # AiService实例工厂(主模型+降级模型+流式)
│   ├── AiRequestContext.java       # ThreadLocal上下文(userId/sessionId/工具调用统计)
│   ├── subbrain/                   # ★SubBrain子脑架构(参考Kortex)
│   │   ├── SubBrain.java           # 子脑接口(code/name/description/execute)
│   │   ├── SubBrainResult.java     # 子脑执行结果DTO(content/hasPendingAction)
│   │   ├── SubBrainResultWrapper.java  # 结构化XML包装器(状态检测+动态Hint三元组)
│   │   ├── UserSubBrain.java       # 用户端子脑(封装UserMasterBrain, 上下文切换, PendingAction传递)
│   │   └── AdminSubBrain.java      # 管理端子脑(封装AdminMasterBrain, 独立session)
│   └── tools/                      # 10个@Tool工具类
│       ├── ContentTools.java       # 非遗内容搜索
│       ├── CommerceTools.java      # 文创商城(加购/下单/查订单)
│       ├── UserTools.java          # 用户服务(地址/资质/通知)
│       ├── AdminTools.java         # 管理员(审批/发货/统计)
│       ├── KnowledgeTools.java     # 知识库RAG
│       ├── RecommendTools.java     # 个性化推荐
│       ├── BrowseHistoryTools.java # 浏览历史
│       ├── UltraTools.java         # Ultra专属(跨用户操作/Skill管理/封禁)
│       ├── SubBrainTools.java      # ★Ultra子脑工具(callUserAI/callAdminAI, 调用预算)
│       ├── ToolResultWrapper.java  # 工具结果XML包装器(STATUS/SUMMARY/DATA/HINT)
│       └── ToolRegistrationConfig.java  # 角色→工具集注册
├── agent/                          # 工具注册
│   ├── AgentUtils.java             # 工具方法
│   └── tool/ToolRegistry.java      # 角色工具注册表
├── infrastructure/
│   ├── guardrails/                 # GuardrailsFilter, RateLimiter, TokenBudget
│   ├── llm/                        # LlmProperties, LlmClient, LlmStreamHandler, LlmCircuitBreaker
│   ├── memory/                     # ChatMemoryManager, MemoryExtractor, MemorySummarizer
│   ├── skill/                      # Skill, SkillPromptConfig
│   ├── sse/                        # SseEmitterManager
│   └── telemetry/                  # TelemetryTracer, AiSelfEvaluator, CostTracker, TitleGenerator, TraceContext
├── service/                        # ConversationService, KnowledgeService, RecommendService, SkillConfigService,
│                                   # UserMemoryService, UltraAuthService, AgentConfigService
├── dto/                            # ChatRequest/Response, PendingAction等
├── entity/                         # AiConversation, AiMessage, AiUserMemory, AiTraceSpan等
└── mapper/                         # MyBatis Mapper + XML
```

---

## 4. 核心编排服务 OrchestratorService

**文件**: `orchestrator/OrchestratorService.java` (~620行)

### 4.1 核心常量

```java
private static final String LAST_AGENT_KEY_PREFIX = "omnitrix:last_agent:";
```

### 4.2 依赖注入 (20个)

ActionExecutor, GuardrailsFilter, RateLimiter, TokenBudget, LlmProperties, LlmCircuitBreaker, ConversationService, ChatMemoryManager, MemoryExtractor, MemorySummarizer, UserMemoryService, SseEmitterManager, TelemetryTracer, AiSelfEvaluator, CostTracker, TitleGenerator, StringRedisTemplate, AiUserAiConfigMapper, UserService, **MasterBrainFactory**, agentExecutor

### 4.3 六个公共入口

所有入口统一走 V2 路径，不再有 V1 分支：

```java
// 用户同步聊天
public ChatResponse chat(Long userId, String sessionId, String userMessage) {
    String aiCheck = checkUserAiAccess(userId);  // Ultra可禁用用户AI
    if (aiCheck != null) return ChatResponse.of(null, sessionId, aiCheck, "access_denied", 0);
    refreshOnlineHeartbeat(userId);
    return doChatSync(userId, sessionId, userMessage, "user");
}

// 用户流式聊天 (SSE)
public void chatStream(Long userId, String sessionId, String userMessage, SseEmitter emitter)

// 管理员同步/流式聊天
public ChatResponse adminChat(Long adminId, String sessionId, String userMessage)
public void adminChatStream(Long adminId, String sessionId, String userMessage, SseEmitter emitter)

// Ultra同步/流式聊天（不受限流和Token预算约束）
public ChatResponse ultraChat(Long adminId, String sessionId, String userMessage)
public void ultraChatStream(Long adminId, String sessionId, String userMessage, SseEmitter emitter)
```

### 4.4 同步核心流程 doChatSync()

```java
private ChatResponse doChatSync(Long userId, String sessionId, String userMessage, String role) {
    long startTime = System.currentTimeMillis();
    boolean isUltra = "ultra".equals(role);

    // Ultra不受限流和Token预算约束
    if (!isUltra) {
        if (!rateLimiter.tryAcquire(userId)) return ChatResponse.of(..., "rate_limit", 0);
        if (!tokenBudget.hasRemaining(userId)) return ChatResponse.of(..., "token_budget", 0);
    }
    String inputRejection = guardrailsFilter.validateInput(userMessage);
    if (inputRejection != null) return ChatResponse.of(..., "guardrails", 0);

    AiConversation conversation = conversationService.findOrCreate(sessionId, userId);

    // 待确认操作检查（PendingAction 两步确认流程）
    ChatResponse pendingResult = handlePendingActionSync(userId, sessionId, userMessage, conversation);
    if (pendingResult != null) return pendingResult;

    String agentCode = "langchain4j_" + role;
    AiRequestContext.set(userId, sessionId);
    SubBrainTools.resetCallCounter();   // ★ 重置子脑调用计数器
    try {
        // 调用 Brain（含主→辅降级链）
        BrainResult result = invokeBrainSync(role, userId, sessionId, userMessage);
        String aiContent = guardrailsFilter.sanitizeOutput(result.content);
        int latencyMs = (int) (System.currentTimeMillis() - startTime);

        AiMessage assistantMsg = saveAndPostProcess(
                conversation, sessionId, userId, userMessage, aiContent,
                result.model, agentCode, latencyMs,
                result.inputTokens, result.outputTokens);  // 真实token计数

        return ChatResponse.of(assistantMsg.getId(), sessionId, aiContent, agentCode, latencyMs);
    } catch (Exception e) {
        // 错误处理...
    } finally {
        SubBrainTools.clearCallCounter();  // ★ 清理子脑调用计数器
        AiRequestContext.clear();
    }
}
```

### 4.5 Brain调用（含降级链）invokeBrainSync()

```java
private BrainResult invokeBrainSync(String role, Long userId, String sessionId, String userMessage) {
    String skills = masterBrainFactory.buildSkillsPrompt();
    String primaryModel = llmProperties.getPrimaryConfig().getModel();

    try {
        Result<String> result = invokeBrainPrimary(role, userId, sessionId, userMessage, skills);
        // 从 Result 提取真实 token 使用量
        int inputTokens = result.tokenUsage().inputTokenCount();
        int outputTokens = result.tokenUsage().outputTokenCount();
        return new BrainResult(result.content(), primaryModel, inputTokens, outputTokens);
    } catch (Exception primaryEx) {
        // 主模型失败，自动降级到辅助模型
        Result<String> fallbackResult = invokeBrainFallback(role, userId, sessionId, userMessage, skills);
        String auxModel = llmProperties.getAuxiliaryConfig().getModel() + "(fallback)";
        return new BrainResult(fallbackResult.content(), auxModel, ...);
    }
}

private Result<String> invokeBrainPrimary(String role, Long userId, String sessionId,
                                           String userMessage, String skills) {
    switch (role) {
        case "user": {
            var brain = masterBrainFactory.buildUserBrain(userId, sessionId);
            String profile = masterBrainFactory.buildUserProfile(userId);
            return brain.chat(userMessage, skills, profile);  // → Result<String>
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

### 4.6 后处理 saveAndPostProcess()

```java
private AiMessage saveAndPostProcess(AiConversation conversation, String sessionId,
        Long userId, String userMessage, String aiContent,
        String usedModel, String agentCode, int latencyMs,
        int inputTokens, int outputTokens) {  // ★ 真实token，非估算

    // 1. 保存消息（用户 + AI回复）
    conversationService.saveMessage(..., "user", userMessage, ...);
    AiMessage assistantMsg = conversationService.saveMessage(
            ..., "assistant", aiContent, outputTokens, usedModel, agentCode, latencyMs);

    // 2. 对话记忆
    memoryManager.appendMessage(sessionId, "user", userMessage);
    memoryManager.appendMessage(sessionId, "assistant", aiContent);

    // 3. 首轮生成标题
    if (conversation.getMessageCount() == 0)
        titleGenerator.generateTitle(conversation.getId(), userMessage);

    // 4. 摘要 + 长期记忆提取 + 缓存失效
    memorySummarizer.summarizeIfNeeded(sessionId, conversation.getId());
    memoryExtractor.extractAndSave(userId, userMessage, aiContent);
    userMemoryService.invalidateCache(userId);

    // 5. Token预算消费（真实值）
    if (inputTokens > 0 || outputTokens > 0)
        tokenBudget.consume(userId, inputTokens + outputTokens);

    // 6. 遥测 + 嵌套Span追踪 (REQUEST → BRAIN → LLM)
    String traceId = telemetryTracer.recordAndReturnTraceId(...);
    telemetryTracer.recordSpan(traceId, requestSpanId, null, "REQUEST", ...);
    telemetryTracer.recordSpan(traceId, brainSpanId, requestSpanId, "BRAIN", ...);
    telemetryTracer.recordSpan(traceId, llmSpanId, brainSpanId, "LLM", ...);

    // 7. AI自评 + 成本记录
    selfEvaluator.evaluate(traceId, userMessage, aiContent);
    costTracker.recordCost(traceId, usedModel, inputTokens, outputTokens);
    saveLastAgentCode(sessionId, agentCode);

    return assistantMsg;
}
```

### 4.7 真流式聊天 doChatStream()

使用 LangChain4j TokenStream 实现真流式输出，支持 `<think>` 标签分流：

```java
private void doChatStream(Long userId, String sessionId, String userMessage,
                           SseEmitter emitter, String role) {
    // 限流 + 安全检查 + 熔断器检查

    CompletableFuture.runAsync(() -> {
        AiRequestContext.set(userId, sessionId);
        SubBrainTools.resetCallCounter();   // ★
        try {
            TokenStream tokenStream = buildTokenStream(role, userId, sessionId, userMessage, skills);

            tokenStream
                .onNext(token -> {
                    // <think> 标签跨chunk缓冲检测
                    if (insideThink) {
                        sseEmitterManager.sendThinking(emitter, chunk);  // 思维链
                    } else {
                        sseEmitterManager.sendChunk(emitter, chunk);      // 正常内容
                    }
                })
                .onComplete(response -> {
                    circuitBreaker.recordSuccess();
                    // 从 Response 提取真实 token 使用量
                    TokenUsage usage = response.tokenUsage();
                    int inputTokens = usage.inputTokenCount();
                    int outputTokens = usage.outputTokenCount();

                    saveAndPostProcess(conversation, ..., inputTokens, outputTokens);
                    sseEmitterManager.sendDone(emitter, assistantMsg.getId(), latencyMs, usedModel);
                })
                .onError(error -> {
                    circuitBreaker.recordFailure();
                    sseEmitterManager.sendError(emitter, "AI 服务暂时不可用");
                })
                .start();

        } finally {
            SubBrainTools.clearCallCounter();  // ★
            AiRequestContext.clear();
        }
    }, agentExecutor);
}
```

### 4.8 Ultra SubBrain 子脑架构详解

#### 工具注册（ToolRegistrationConfig）

```java
// User 角色：完整用户工具集
toolRegistry.register("user", contentTools);     // 非遗内容
toolRegistry.register("user", commerceTools);    // 文创商城
toolRegistry.register("user", userTools);        // 用户服务
toolRegistry.register("user", knowledgeTools);   // 知识库
toolRegistry.register("user", recommendTools);   // 推荐
toolRegistry.register("user", browseHistoryTools);

// Admin 角色：管理工具集
toolRegistry.register("admin", contentTools);
toolRegistry.register("admin", commerceTools);
toolRegistry.register("admin", knowledgeTools);
toolRegistry.register("admin", adminTools);

// Ultra 角色：编排模式 — 不直接持有 user/admin 工具
// 通过 SubBrainTools 委托子脑执行
toolRegistry.register("ultra", ultraTools);       // Ultra专属（跨用户/Skill/封禁）
toolRegistry.register("ultra", subBrainTools);    // ★子脑工具（callUserAI/callAdminAI）
```

#### SubBrain 接口

```java
public interface SubBrain {
    String getCode();            // 唯一标识: "user_ai" / "admin_ai"
    String getName();            // 显示名称
    String getDescription();     // 能力描述（注入Ultra LLM工具描述）
    SubBrainResult execute(String userQuery, Long targetUserId, String ultraSessionId);
}
```

#### SubBrainTools（Ultra LLM 可调用的 @Tool）

```java
@Tool("调用用户端AI助手...")
public String callUserAI(String input) {
    // 1. 调用预算检查（ThreadLocal计数器，上限5次/请求）
    // 2. 解析输入：'查询内容' 或 '查询内容|目标用户名或ID'
    // 3. 执行 UserSubBrain.execute()
    // 4. SubBrainResultWrapper.wrap() 包装为结构化XML
    return SubBrainResultWrapper.wrap(brainCode, result, callCount, MAX_CALLS);
}

@Tool("调用管理端AI助手...")
public String callAdminAI(String input) { ... }
```

#### UserSubBrain 执行流程

```java
public SubBrainResult execute(String userQuery, Long targetUserId, String ultraSessionId) {
    Long originalUserId = AiRequestContext.getUserId();
    String originalSessionId = AiRequestContext.getSessionId();
    Long effectiveUserId = (targetUserId != null) ? targetUserId : originalUserId;
    String subSessionId = ultraSessionId + ":sub:user_ai";

    try {
        AiRequestContext.set(effectiveUserId, subSessionId);  // 切换上下文

        UserMasterBrain brain = masterBrainFactory.buildUserBrain(effectiveUserId, subSessionId);
        Result<String> result = brain.chat(userQuery, skills, profile);  // 完整推理

        // PendingAction 传递：subSession → ultraSession
        PendingAction subPending = actionExecutor.getPendingAction(subSessionId);
        if (subPending != null) {
            actionExecutor.savePendingAction(ultraSessionId, subPending);
            actionExecutor.clearPendingAction(subSessionId);
            return SubBrainResult.withPendingAction(result.content(), subPending.getDescription());
        }
        return SubBrainResult.of(result.content());
    } finally {
        AiRequestContext.set(originalUserId, originalSessionId);  // 恢复上下文
    }
}
```

#### SubBrainResultWrapper 输出格式

```xml
<sub_brain_result status="ACTION_PROPOSED" brain="user_ai" call_number="1/5">
  <summary>已将「苏绣手帕」加入用户张三的购物车，等待确认</summary>
  <data>...子脑LLM原始回复（截断至1500字符）...</data>
  <pending_action>为用户张三添加商品「苏绣手帕」到购物车</pending_action>
  <hint>
    ACTION: relay_confirmation_to_user
    REASON: user_ai 子脑已提出写操作确认请求，需要转述给用户等待确认
    DO_NOT: 不要自动确认操作，不要跳过确认流程，不要继续调用其他子脑
  </hint>
</sub_brain_result>
```

### 4.9 配置类详解

#### AsyncConfig

```java
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean("aiAsyncExecutor")
    public Executor aiAsyncExecutor() {
        // core=4, max=10, queue=100, CallerRunsPolicy, 优雅关闭(30s)
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

    @Bean @Qualifier("auxiliaryChatModel")
    public ChatLanguageModel auxiliaryChatModel(LlmProperties props) {
        // 辅助模型（用于降级）
    }

    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel(LlmProperties props) {
        return OpenAiStreamingChatModel.builder()
                // 同样参数，使用流式模型
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

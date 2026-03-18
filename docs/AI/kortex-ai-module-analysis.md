# Kortex AI 模块完整技术文档

## 文档概述

本文档详细描述 Kortex AI Cloud 项目中 AI 模块的完整架构、核心组件、实现细节和配置说明。文档目标是为开发者提供足够的信息，以便能够完整复刻出本系统。

**项目基于**: Spring Cloud 2025.0.0 + Java 21 + LangChain4j 1.11.0

---

## 目录

1. [项目架构概览](#1-项目架构概览)
2. [模块结构详解](#2-模块结构详解)
3. [LLM 基础设施配置](#3-llm-基础设施配置)
4. [Agent 智能体系统](#4-agent-智能体系统)
5. [文档智能处理模块](#5-文档智能处理模块)
6. [Matter 事项智能分析](#6-matter-事项智能分析)
7. [ASR 语音识别模块](#7-asr-语音识别模块)
8. [Python 远程工具系统](#8-python-远程工具系统)
9. [监控与追踪系统](#9-监控与追踪系统)
10. [SSE 流式响应系统](#10-sse-流式响应系统)
11. [工作流引擎](#11-工作流引擎)
12. [部署与配置](#12-部署与配置)
13. [快速复刻指南](#13-快速复刻指南)

---

## 1. 项目架构概览

### 1.1 技术栈

| 技术组件 | 版本 | 用途 |
|---------|------|------|
| Java | 21 | 运行时环境 |
| Spring Boot | 3.5.6 | 应用框架 |
| Spring Cloud | 2025.0.0 | 微服务框架 |
| LangChain4j | 1.11.0 | AI 应用开发框架 |
| LangGraph4j | 1.7.2 | AI 工作流引擎 |
| Nacos | - | 配置中心与服务发现 |
| Redis | - | 缓存与消息存储 |
| Langfuse | - | AI 可观测性平台 |

### 1.2 模块依赖关系

```
kortex-ai-cloud (Root)
├── kortex-modules/kortex-ai                    # AI 业务服务（端口 9299）
│   ├── 依赖 kortex-common-ai                    # 公共 AI 库
│   ├── 依赖 kortex-api-*                       # API 接口
│   └── 使用 LangChain4j 1.11.0                 # 核心框架
│
└── kortex-common/kortex-common-ai              # AI 公共库
    ├── 依赖 kortex-common-core                  # 基础库
    └── 使用 LangChain4j 1.8.0 + LangGraph4j 1.7.2
```

---

## 2. 模块结构详解

### 2.1 kortex-ai 模块目录结构

```
kortex-modules/kortex-ai/src/main/java/com/kortex/ai/
├── features/                                    # 功能模块
│   ├── asr/                                     # 语音识别 (4 files)
│   │   ├── config/
│   │   │   ├── AsrConfiguration.java
│   │   │   └── AsrProperties.java
│   │   ├── controller/
│   │   │   └── AsrController.java
│   │   └── service/
│   │       └── AsrProxyService.java
│   │
│   ├── agent/                                   # Agent 智能体 (12 files)
│   │   ├── runtime/
│   │   │   ├── config/
│   │   │   │   └── AgentAiPromptProperties.java
│   │   │   └── DynamicAgentFactory.java
│   │   ├── orchestrator/
│   │   │   ├── config/
│   │   │   │   └── OrchestratorAiPromptProperties.java
│   │   │   ├── OrchestratorService.java        # 核心编排器
│   │   │   ├── SubAgentTool.java               # 子Agent工具
│   │   │   └── system/
│   │   │       ├── SystemSubAgentToolProvider.java
│   │   │       ├── GeneralToolsSubAgent.java
│   │   │       └── DocumentSystemSubAgent.java
│   │   └── controller/
│   │       └── AgentChatController.java
│   │
│   ├── document/                                # 文档智能处理 (18 files)
│   │   ├── controller/
│   │   │   └── DocumentAiController.java
│   │   ├── domain/model/
│   │   │   └── AiDocumentInfo.java
│   │   ├��─ infrastructure/
│   │   │   └── DocumentGatewayImpl.java
│   │   ├── model/
│   │   │   ├── FieldExtractRequest.java
│   │   │   ├── FieldExtractResult.java
│   │   │   └── FieldSemanticRequest.java
│   │   ├── service/
│   │   │   ├── DocumentFieldExtractService.java
│   │   │   ├── LlmVariableFilterService.java
│   │   │   └── DocumentTextFilter.java
│   │   ├── tool/
│   │   │   ├── DocumentContentTool.java
│   │   │   ├── DocumentSearchTool.java
│   │   │   └── DocumentSearchSubAgentTool.java
│   │   └── util/
│   │       └── LlmJsonUtils.java
│   │
│   └── matter/                                  # 事项智能分析 (8 files)
│       ├── config/
│       │   └── MatterMasterBrainPromptProperties.java
│       ├── domain/
│       │   ├── ability/
│       │   │   └── MatterMasterBrain.java
│       │   └── service/
│       │       ├── MatterPromptBuilder.java
│       │       └── MatterMasterBrainPromptBuilder.java
│       ├── service/
│       │   └── MatterAiAnalysisServiceImpl.java
│       └── tool/
│           ├── MatterEmailAnalysisTool.java
│           ├── MatterDocumentAnalysisTool.java
│           └── MatterToolMessages.java
│
├── infrastructure/                              # 基础设施层
│   ├── llm/                                     # LLM 配置
│   │   ├── config/
│   │   │   ├── ChatModelAutoConfiguration.java
│   │   │   └── QwenVisionModelConfig.java
│   │
│   ├── monitor/                                 # 监控/观测
│   │   ├── config/
│   │   │   └── LangfuseAutoConfiguration.java
│   │   ├── langfuse/
│   │   │   ├── LangfusePromptClient.java
│   │   │   └── LangfuseIngestionClient.java
│   │   └── listener/
│   │       └── LangfuseTracingListener.java
│   │
│   ├── remote/                                  # 远程服务
│   │   └── python/
│   │       ├── RemotePythonDataService.java    # 1174行核心服务
│   │       └── DynamicPythonToolProvider.java   # 548行动态工具
│   │
│   ├── sse/                                     # Server-Sent Events
│   │   ├── AiStreamPresenter.java
│   │   ├── ActiveStreamRegistry.java
│   │   ├── ComponentPushHelper.java
│   │   └── OrchestrationEventPusher.java
│   │
│   └── scheduler/
│       └── ToolContextCleanupScheduler.java
│
└── KortexAiApplication.java                    # 启动类
```

### 2.2 kortex-common-ai 模块目录结构

```
kortex-common/kortex-common-ai/src/main/java/com/kortex/common/ai/
├── rerank/                                      # 重排序服务
│   ├── RerankService.java
│   ├── RerankServiceImpl.java
│   └── RerankResult.java
│
├── workflow/                                    # AI 工作流引擎 ⭐
│   ├── engine/
│   │   ├── AiServiceProvider.java
│   │   └── AdaptiveWorkflowEngine.java         # 1255行核心引擎
│   │
│   ├── node/                                    # 工作流节点
│   │   ├── ToolCallNode.java
│   │   ├── ResultFilterNode.java
│   │   ├── ReflectionNode.java
│   │   ├── DecisionNode.java
│   │   └── AnswerGenerationNode.java
│   │
│   ├── state/                                   # 状态管理
│   │   ├── AiWorkflowState.java
│   │   ├── NodeExecution.java
│   │   ├── NodeStatus.java
│   │   ├── ToolCall.java
│   │   ├── IntentAnalysis.java
│   │   ├── Decision.java
│   │   ├── DecisionAction.java
│   │   ├── ExecutionPath.java
│   │   └── TaskAnalysisResult.java
│   │
│   ├── router/                                  # 路由决策
│   │   ├── TaskAnalyzer.java
│   │   └── ModelRouter.java
│   │
│   └── prompt/                                  # Prompt 管理
│       ├── SummaryPromptBuilder.java
│       └── ToolResultPromptGenerator.java
│
├── service/                                     # AI 服务
│   ├── AiChatService.java
│   └── impl/
│       └── AliyunQwenAiService.java
│
├── model/                                        # 数据模型
│   └── VllmEmbeddingModel.java
│
└── tracker/                                      # 事件追踪
    └── event/
        └── WorkflowEvent.java
```

---

## 3. LLM 基础设施配置

### 3.1 核心配置类

#### ChatModelAutoConfiguration.java

**路径**: `kortex-modules/kortex-ai/src/main/java/com/kortex/ai/infrastructure/llm/config/ChatModelAutoConfiguration.java`

```java
@Configuration(proxyBeanMethods = false)
public class ChatModelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConfigurationProperties(prefix = "langchain4j.community.dashscope.chat-model")
    public QwenModelProperties qwenModelProperties() {
        return new QwenModelProperties();
    }

    @Bean
    @ConditionalOnProperty(prefix = "langchain4j.community.dashscope.chat-model", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ChatModel kortexQwenChatModel(QwenModelProperties properties) {
        DashScopeChatModel.Builder builder = DashScopeChatModel.builder()
            .apiKey(properties.getApiKey())
            .modelName(properties.getModelName())
            .temperature(properties.getTemperature())
            .maxTokens(properties.getMaxTokens());
        return builder.build();
    }

    @Bean
    public StreamingChatModel kortexQwenStreamingChatModel(QwenModelProperties properties) {
        DashScopeStreamingChatModel.Builder builder = DashScopeStreamingChatModel.builder()
            .apiKey(properties.getApiKey())
            .modelName(properties.getModelName())
            .temperature(properties.getTemperature())
            .maxTokens(properties.getMaxTokens());
        return builder.build();
    }
}
```

**配置属性类**:

```java
@Data
@ConfigurationProperties(prefix = "langchain4j.community.dashscope.chat-model")
public class QwenModelProperties {
    private String apiKey;
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private String modelName = "qwen-max";
    private Double temperature = 0.7;
    private Integer maxTokens = 8192;
    private String extractModelName = "qwen-turbo";
}
```

### 3.2 配置参数说明

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `apiKey` | - | 阿里云 DashScope API Key |
| `baseUrl` | dashscope endpoint | API 基础地址 |
| `modelName` | qwen-max | 主模型名称 |
| `temperature` | 0.7 | 采样温度 |
| `maxTokens` | 8192 | 最大输出 token 数 |
| `extractModelName` | qwen-turbo | 快速提取模型 |

### 3.3 vLLM 本地部署支持

```java
@ConfigurationProperties(prefix = "langchain4j.vllm.chat-model")
public class VllmModelProperties {
    private String baseUrl = "http://192.168.31.123:8000/v1";
    private String modelName = "Qwen/Qwen3.5-27B-FP8";
    private String apiKey = "EMPTY";
    private Duration timeout = Duration.ofSeconds(180);
}
```

### 3.4 Nacos 配置示例

```yaml
langchain4j:
  community:
    dashscope:
      chat-model:
        enabled: true
        api-key: sk-xxxxxxxxxxxxxxxx
        model-name: qwen-max
        temperature: 0.7
        max-tokens: 8192

langchain4j:
  vllm:
    chat-model:
      enabled: false
      base-url: http://192.168.31.123:8000/v1
      model-name: Qwen/Qwen3.5-27B-FP8
```

---

## 4. Agent 智能体系统

### 4.1 核心架构

Kortex AI 采用**多层次 Agent 架构**:

```
┌─────────────────────────────────────────────────────────┐
│                      用户请求                            │
└─────────────────────────┬───────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│            OrchestratorService (编排器)                  │
│  - 接收用户查询                                          │
│  - 构建 Master Brain                                     │
│  - 协调子 Agent                                          │
└─────────────────────��───┬───────────────────────────────┘
                          │
           ┌──────────────┼──────────────┐
           ▼              ▼              ▼
┌─────────────────┐ ┌─────────────┐ ┌─────────────────┐
│ DynamicSubAgent │ │SystemSubAgent│ │ 工具调用       │
│ (用户配置Agent) │ │(内置Agent)  │ │ (文档/邮件等)   │
└─────────────────┘ ���─────────────┘ └─────────────────┘
```

### 4.2 OrchestratorService - 核心编排器

**路径**: `kortex-modules/kortex-ai/src/main/java/com/kortex/ai/features/agent/orchestrator/OrchestratorService.java`

#### 类结构

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestratorService {

    private final DynamicSubAgentToolProvider dynamicSubAgentToolProvider;
    private final SystemSubAgentToolProvider systemSubAgentToolProvider;
    private final ChatModel chatModel;
    private final StreamingChatModel streamingChatModel;
    private final RedisChatMemoryStore redisChatMemoryStore;
    private final ActiveStreamRegistry activeStreamRegistry;
    private final LangfusePromptClient langfusePromptClient;

    public interface MasterBrain extends AiServices {
        TokenStream chatStream(@MemoryId String memoryId, @UserMessage String userMessage);
    }
}
```

#### 核心方法

**1. orchestrate() - 同步编排**

```java
public String orchestrate(String sessionId, String userQuery) {
    // 1. 构建 memoryId: userId:sessionId:orchestrator
    String memoryId = buildMemoryId(userId, sessionId, "orchestrator");

    // 2. 创建工具上下文
    ToolContext toolContext = createToolContext(userId, tenantId, deptId, roleIds);

    // 3. 构建 Master Brain
    MasterBrain brain = buildMasterBrain(memoryId, toolContext);

    // 4. 执行对话
    String response = brain.chat(userQuery);
    return response;
}
```

**2. orchestrateStream() - 流式编排**

```java
public void orchestrateStream(String sessionId, String userQuery, 
        Long userId, String clientId) {

    // 1. 注册活跃流
    activeStreamRegistry.register(userId, clientId);

    // 2. 构建 Master Brain
    MasterBrain brain = buildMasterBrain(memoryId, toolContext);

    // 3. 流式处理并推送
    TokenStream stream = brain.chatStream(memoryId, userQuery);
    aiStreamPresenter.handleStreamResponse(userId, clientId, sessionId, 
        "orchestration", stream, cleanupAction);
}
```

**3. buildMasterBrain() - 构建主脑实例**

```java
private MasterBrain buildMasterBrain(String memoryId, ToolContext toolContext) {
    return Builder.builder(MasterBrain.class)
        .chatModel(chatModel)
        .chatMemoryProvider(memoryId ->
            ChatMemoryBuilder.builder()
                .chatMemoryStore(redisChatMemoryStore)
                .maxTokens(4000)
                .build())
        .systemMessageProvider(request -> getSystemMessage(request, toolContext))
        .toolProvider(CompositeToolProvider.builder()
            .toolProviders(dynamicSubAgentToolProvider, systemSubAgentToolProvider)
            .build())
        .tools(documentContentTool)
        .maxSequentialToolsInvocations(10)
        .build();
}
```

### 4.3 DynamicAgentFactory - 动态 Agent 工厂

**路径**: `kortex-modules/kortex-ai/src/main/java/com/kortex/ai/features/agent/runtime/DynamicAgentFactory.java`

#### 类结构

```java
@Component
@Slf4j
public class DynamicAgentFactory {

    private final Map<String, AgentRuntime> runtimeCache = new ConcurrentHashMap<>();

    private final ChatModel chatModel;
    private final RedisChatMemoryStore chatMemoryStore;
    private final RemotePythonDataService pythonDataService;
    private final RemoteAgentService remoteAgentService;
}
```

#### 核心方法

**getOrCreateRuntime() - 获取或创建运行时**

```java
public AgentRuntime getOrCreateRuntime(String agentCode) {
    // 1. 检查缓存
    AgentRuntime cached = runtimeCache.get(agentCode);
    if (cached != null) {
        // 2. 版本检查
        Long latestVersion = remoteAgentService.getVersion(agentCode);
        if (cached.getVersion().equals(latestVersion)) {
            return cached;
        }
        // 版本过期，清除缓存
        runtimeCache.remove(agentCode);
    }

    // 3. 创建新实例
    AgentRuntime runtime = createAgentRuntime(agentCode);
    runtimeCache.put(agentCode, runtime);
    return runtime;
}
```

**routeToAgent() - 智能路由**

```java
public String routeToAgent(String userQuery) {
    List<Agent> agents = remoteAgentService.listEnabled();

    int maxScore = 0;
    String bestAgent = null;

    for (Agent agent : agents) {
        List<String> keywords = parseRoutingKeywords(agent.getRoutingKeywords());
        int score = countMatches(userQuery, keywords);
        if (score > maxScore) {
            maxScore = score;
            bestAgent = agent.getCode();
        }
    }

    return bestAgent;
}
```

### 4.4 SubAgentTool - 子 Agent 工具执行器

**路径**: `kortex-modules/kortex-ai/src/main/java/com/kortex/ai/features/agent/orchestrator/SubAgentTool.java`

#### 类结构

```java
@Slf4j
public class SubAgentTool implements ToolExecutor {

    private final AgentRuntime agentRuntime;
    private final boolean enableResultWrapping;
    private final int maxContextChars;

    @Override
    public String execute(ToolExecutionRequest request, Object memoryId) {
        // 1. ContextClassLoader 修正
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(this.getClass().getClassLoader());

        try {
            // 2. 参数解析
            String userQuery = request.arguments().getString("user_query");
            String context = request.arguments().hasString("context") 
                ? request.arguments().getString("context") : null;
            Boolean showResultToUser = request.arguments().hasBoolean("show_result_to_user")
                ? request.arguments().getBoolean("show_result_to_user") : false;

            // 3. 构建 SessionId
            String subAgentMemoryId = buildMemoryId(userId, sessionId, 
                "sub-agent", agentCode);

            // 4. 上下文传播
            ToolContext context = createToolContext();
            context.put("currentAgent", agentInfo);

            // 5. 执行并返回结果
            return agentRuntime.chat(subAgentMemoryId, userQuery, context);
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }
    }
}
```

### 4.5 SystemSubAgentToolProvider - 系统子 Agent 提供者

**路径**: `kortex-modules/kortex-ai/src/main/java/com/kortex/ai/features/agent/orchestrator/system/SystemSubAgentToolProvider.java`

#### 工具规格定义

```java
ToolSpecification.builder()
    .name("system_sub_agent")
    .description("调用系统子Agent执行特定任务")
    .parameters(
        JsonSchemaProperty.builder()
            .type(OBJECT)
            .properties(
                Map.of(
                    "user_query", JsonSchemaProperty.builder()
                        .type(STRING).description("任务指令").build(),
                    "context", JsonSchemaProperty.builder()
                        .type(STRING).description("参考资料").build(),
                    "show_result_to_user", JsonSchemaProperty.builder()
                        .type(BOOLEAN).description("是否展示给用户").build()
                )
            )
            .required("user_query")
            .build()
    )
    .build()
```

---

## 5. 文档智能处理模块

### 5.1 DocumentFieldExtractService - 文档字段提取

**路径**: `kortex-modules/kortex-ai/src/main/java/com/kortex/ai/features/document/service/DocumentFieldExtractService.java`

#### 路由分发逻辑

```
文档类型判断:
├── office_* / image / pdf → 上传文件通道 (smartRecognize)
├── document_xlsx → 文库Excel��道 (getExcelMetadata)
└── document_html → 文库富文本通道 (Tiptap JSON解析)
```

#### 核心方法

**extractFields() - 主入口**

```java
public List<Map<String, Object>> extractFields(
    String documentId, String fileUrl, String documentType,
    String content, String mode, Boolean deepThinking,
    Long userId, String tenantId) {

    // 模式选择
    if ("ocr_extract".equals(mode) || "ai_cloud".equals(mode)) {
        // 字段提取模式
        return extractByChannel(documentId, fileUrl, documentType, content);
    } else if (deepThinking) {
        // 深度思考模式 - LLM语义增强
        return extractFieldsBySemantic(documentId, fileUrl, documentType, content);
    }
}
```

**extractFieldsBySemantic() - LLM 语义增强**

```java
private List<Map<String, Object>> extractFieldsBySemantic(...) {
    // 1. 噪声过滤 - 移除页眉页脚
    String cleaned = documentTextFilter.filterNoise(content);

    // 2. 分片处理
    List<String> chunks = splitIntoChunks(cleaned, 2000);

    // 3. LLM 识别
    List<FieldExtractResult> results = new ArrayList<>();
    for (String chunk : chunks) {
        FieldSemanticRequest request = buildSemanticRequest(chunk, fieldDefinitions);
        String llmResponse = chatModel.chat(buildPrompt(request));
        results.addAll(parseLlmResponse(llmResponse));
    }

    // 4. 合并结果
    return mergeResults(results);
}
```

### 5.2 DocumentContentTool - 文档内容工具

**路径**: `kortex-modules/kortex-ai/src/main/java/com/kortex/ai/features/document/tool/DocumentContentTool.java`

#### 工具方法

| 工具名 | 功能 | 参数 |
|--------|------|------|
| `getDocumentContent` | 获取文档内容 | documentId, offset, maxLength |
| `getDocumentInfo` | 获取文档详情 | documentId |
| `listLibraries` | 获取文库列表 | (无) |
| `listDocuments` | 浏览文档列表 | libraryId, onlyMine, daysAgo, type, page |

#### 实现特点

- 每次查询必须重新调用，禁止依赖缓存结果
- SSE推送文档列表给前端
- 默认20条/页分页

### 5.3 字段提取规则

支持三种提取模式:

1. **冒号分割**: `label: value`
2. **数字+单位**: `合同价格200元`、`标的100万`
3. **占位符**: `{{xxx}}`、`${xxx}`、`【xxx】`

---

## 6. Matter 事项智能分析

### 6.1 MatterMasterBrain - 事项主脑接口

**路径**: `kortex-modules/kortex-ai/src/main/java/com/kortex/ai/features/matter/domain/ability/MatterMasterBrain.java`

```java
public interface MatterMasterBrain {
    TokenStream chatStream(@MemoryId String memoryId, @UserMessage String userMessage);
}
```

### 6.2 MatterAiAnalysisServiceImpl - 事项 AI 分析服务

**路径**: `kortex-modules/kortex-ai/src/main/java/com/kortex/ai/features/matter/service/MatterAiAnalysisServiceImpl.java`

#### 核心方法

**analyzeStreamMatter() - 流式分析**

```java
public void analyzeStreamMatter(MatterAiAnalysisRequest request, 
        Long userId, String tenantId, Long deptId, String clientId) {

    // 1. 获取案件信息
    AiMatterInfo matterInfo = matterGateway.getMatterInfo(request.getMatterId());

    // 2. 构建 Master Brain
    MatterMasterBrain masterBrain = buildMasterBrain(matterInfo, 
        request.getOutputLanguage());

    // 3. 注入工具
    Tools tools = Tools.builder()
        .add(new MatterDocumentAnalysisTool(matterInfo))
        .add(new MatterEmailAnalysisTool(matterInfo))
        .build();

    // 4. 流式返回
    TokenStream stream = masterBrain.chatStream(memoryId, prompt);
    aiStreamPresenter.handleStreamResponse(..., stream, ...);
}
```

**buildMasterBrain() - 构建主脑实例**

```java
private MatterMasterBrain buildMasterBrain(AiMatterInfo matterInfo, 
        String outputLanguage) {
    return Builder.builder(MatterMasterBrain.class)
        .chatModel(chatModel)
        .chatMemoryProvider(memoryId -> createChatMemory(memoryId))
        .systemMessageProvider(request -> buildSystemMessage(matterInfo, outputLanguage))
        .tools(documentAnalysisTool, emailAnalysisTool)
        .build();
}
```

### 6.3 Matter 工具

**MatterDocumentAnalysisTool**:

```java
@Tool("分析案件相关文档内容")
public String analyzeDocument(String documentId, String analysisType) {
    // 1. 获取文档内容
    String content = documentGateway.getContent(documentId);
    // 2. LLM 分析
    String analysis = llm.analyze(content, analysisType);
    return analysis;
}
```

**MatterEmailAnalysisTool**:

```java
@Tool("分析案件相关邮件内容")
public String analyzeEmail(String emailId, String analysisFocus) {
    // 1. 获取邮件内容
    EmailInfo email = emailGateway.getEmail(emailId);
    // 2. LLM 分析
    String analysis = llm.analyze(email.getContent(), analysisFocus);
    return analysis;
}
```

---

## 7. ASR 语音识别模块

### 7.1 架构设计

```
前端 → AsrController → AsrProxyService → 内部ASR服务
                                    ↓
                         SenseVoiceSmall + pyannote
```

### 7.2 配置类

**AsrProperties**:

```java
@Data
@RefreshScope
@ConfigurationProperties(prefix = "ai.asr")
public class AsrProperties {
    private boolean enabled = false;
    private String baseUrl = "http://localhost:7998";
    private int timeout = 300000;
    private long maxFileSize = 50 * 1024 * 1024;
}
```

### 7.3 核心方法

**AsrController**:

```java
@PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public R<JSONObject> transcribe(@RequestPart("file") MultipartFile file) {
    return R.ok(asrProxyService.submitTranscription(file));
}

@GetMapping("/transcribe/{jobId}")
public R<JSONObject> getResult(@PathVariable String jobId) {
    return R.ok(asrProxyService.getTranscriptionResult(jobId));
}
```

### 7.4 Nacos 配置

```yaml
ai:
  asr:
    enabled: true
    base-url: http://localhost:7998
    timeout: 300000
    max-file-size: 52428800  # 50MB
```

---

## 8. Python 远程工具系统

### 8.1 RemotePythonDataService

**路径**: `kortex-modules/kortex-ai/src/main/java/com/kortex/ai/infrastructure/remote/python/RemotePythonDataService.java`

这是 **1174 行** 的核心服务类，提供多种工具方法调用 Python 远程服务。

#### 配置

```java
@Value("${ai.python-agent.url:http://localhost:9399}")
private String pythonServiceUrl;

@Value("${ai.python-agent.timeout.knowledge-search:60000}")
private int knowledgeSearchTimeout;
```

#### 主要方法分类

**知识库工具**:

```java
// Agent 知识库搜索（支持中文数字变体、多关键词 AND）
public JSONObject knowledgeSearch(String query, String libraryId, int topK);

// 列出知识库文件
public JSONObject listKnowledgeBase();

// 递归列出知识库树结构
public JSONObject listKnowledgeTree(String libraryId);

// 读取知识库文件内容
public JSONObject readKnowledgeFile(String fileId, Integer maxLength);
```

**Excel 数据分析**:

```java
// 读取 Excel 基本信息
public JSONObject readExcel(String fileId);

// 分析列统计信息
public JSONObject analyzeColumn(String fileId, String sheetName, String columnName);

// 异常检测（IQR/Zscore）
public JSONObject detectAnomalies(String fileId, String sheetName, 
    String columnName, String method);

// 计算相关性
public JSONObject calculateCorrelation(String fileId, String sheetName, 
    String[] columns);

// 创建透视表
public JSONObject pivotTable(String fileId, String sheetName, 
    String[] rows, String[] columns, String[] values);
```

**可视化**:

```java
// 生成 Vega-Lite 图表规范
public JSONObject generateVegaLiteChart(String fileId, String sheetName, 
    String chartType, String[] xColumns, String[] yColumns);

// 智能图表推荐
public JSONObject smartChart(String fileId, String sheetName);
```

**代码执行**:

```java
// 在持久化 Jupyter kernel 中执行 Python 代码
public JSONObject runCode(String sessionId, String code, int timeout) {
    String endpoint = pythonServiceUrl + "/tools/run_code";
    
    // 在持久化 Jupyter kernel 中执行
    // 变量在同一 session 内跨调用保持
    // matplotlib 图表自动捕获为 base64 PNG
}
```

### 8.2 DynamicPythonToolProvider

**路径**: `kortex-modules/kortex-ai/src/main/java/com/kortex/ai/infrastructure/remote/python/DynamicPythonToolProvider.java`

这是 **548 行** 的动态工具提供者，实现 LangChain4j 的 `ToolProvider` 接口。

#### 核心特性

1. **自动工具发现**: 从 Python 服务获取 `/tools/schema?format=openai` 动态加载工具定义

2. **参数转换**:
   - snake_case → camelCase (Java 端)
   - camelCase → snake_case (Python 调用)

3. **文档 ID 注入**: 统一通过 `documentId` 获取文件 URL，自动注入 `file_url` 和 `file_name`

#### 实现

```java
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ai.python-agent.dynamic-tools", name = "enabled", havingValue = "true")
public class DynamicPythonToolProvider implements ToolProvider {

    private final Map<String, ToolSpecification> toolSpecifications = new ConcurrentHashMap<>();
    private final Map<String, String> toolEndpoints = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refreshToolDefinitions();
    }

    @Scheduled(fixedRate = 300000)  // 5分钟刷新一次
    public void refreshToolDefinitions() {
        String schema = httpClient.get(pythonServiceUrl + "/tools/schema?format=openai");
        // 解析并缓存工具定义
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        // 返回所有工具的 ToolSpecification 和 ToolExecutor
    }
}
```

### 8.3 Nacos 配置

```yaml
ai:
  python-agent:
    url: http://localhost:9399
    dynamic-tools:
      enabled: true
      timeout: 60000
    timeout:
      default: 30000
      knowledge-search: 60000
      knowledge-tree-search: 120000
      read-knowledge-file: 60000
```

---

## 9. 监控与追踪系统

### 9.1 Langfuse 集成架构

```
AI 调用 → LangfuseTracingListener → LangfuseIngestionClient → Langfuse Cloud
                              ↓
                        LangfusePromptClient (多租户 Prompt)
```

### 9.2 LangfuseTracingListener

**路径**: `kortex-modules/kortex-ai/src/main/java/com/kortex/ai/infrastructure/monitor/listener/LangfuseTracingListener.java`

实现 LangChain4j 的 `AiServiceListener` 接口，追踪 AI 服务调用。

#### Trace 模型结构

```
Langfuse Session  ←  前端 sessionId
  └── Trace       ←  一次 orchestrateStream 调用
        ├── Generation  ←  Master Brain LLM 调用
        ├── Span        ←  Sub-Agent 调度
        │     ├── Generation  ←  Sub-Agent LLM 调用
        │     └── Span        ←  Sub-Agent 工具执行
        └── Span        ←  Master Brain 直接工具执行
```

#### 回调方法

| 方法 | 时机 | 处理逻辑 |
|------|------|----------|
| `onStarted` | AI服务调用开始 | 创建Trace+Generation或Span+子Generation |
| `onResponseReceived` | 收到AI响应 | 更新generation、记录token用量 |
| `onToolExecuted` | 工具执行完毕 | 创建工具Span |
| `onCompleted` | AI服务完成 | 发送trace-create、清理状态 |
| `onError` | AI服务异常 | 记录错误信息 |

#### 核心特性

1. **多级回退机制**: event.result → 缓存generation output → chat memory读取
2. **Session历史**: 构建带会话上下文的trace input
3. **Sub-Agent追踪**: 自动从memoryId恢复agentCode
4. **SSE结果记录**: show_result_to_user时记录推送给前端的完整内容

### 9.3 Langfuse 配置

```yaml
langfuse:
  enabled: true
  secret-key: sk-xxxxxxxxxxxxxxxx
  public-key: pk-xxxxxxxxxxxxxxxx
  base-url: https://cloud.langfuse.com
```

---

## 10. SSE 流式响应系统

### 10.1 AiStreamPresenter

**路径**: `kortex-modules/kortex-ai/src/main/java/com/kortex/ai/infrastructure/sse/AiStreamPresenter.java`

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class AiStreamPresenter {

    private final ActiveStreamRegistry activeStreamRegistry;

    public void handleStreamResponse(Long userId, String clientId, String sessionId,
            String action, TokenStream stream, Runnable cleanupAction) {
        
        // 发送开始信号
        sendSseMessage(userId, clientId, action, "start", "", sessionId);

        stream.onPartialResponse(chunk -> {
                    sendSseMessage(userId, clientId, action, "chunk", chunk, sessionId);
                })
                .onCompleteResponse(response -> {
                    sendSseMessage(userId, clientId, action, "complete", 
                        response.aiResponse().text(), sessionId);
                    cleanupAction.run();
                })
                .onError(error -> {
                    sendSseMessage(userId, clientId, action, "error", 
                        error.getMessage(), sessionId);
                    cleanupAction.run();
                })
                .start();
    }
}
```

#### SSE 消息格式

```json
{
    "action": "orchestration",
    "type": "chunk",
    "content": "这是AI生成的响应内容...",
    "timestamp": 1700000000000
}
```

#### 消息类型

- `start`: 开始响应
- `chunk`: 内容片段
- `complete`: 完成响应
- `error`: 错误信息

### 10.2 ActiveStreamRegistry

**路径**: `kortex-modules/kortex-ai/src/main/java/com/kortex/ai/infrastructure/sse/ActiveStreamRegistry.java`

```java
@Slf4j
@Component
public class ActiveStreamRegistry {

    private final ConcurrentHashMap<String, AtomicBoolean> cancelledFlags = new ConcurrentHashMap<>();

    // Key: "userId:clientId"
    public void register(Long userId, String clientId) {
        String key = userId + ":" + clientId;
        cancelledFlags.put(key, new AtomicBoolean(false));
    }

    public void cancel(Long userId, String clientId) {
        String key = userId + ":" + clientId;
        AtomicBoolean flag = cancelledFlags.get(key);
        if (flag != null) {
            flag.set(true);
        }
    }

    public boolean isCancelled(Long userId, String clientId) {
        String key = userId + ":" + clientId;
        AtomicBoolean flag = cancelledFlags.get(key);
        return flag != null && flag.get();
    }

    public void unregister(Long userId, String clientId) {
        String key = userId + ":" + clientId;
        cancelledFlags.remove(key);
    }
}
```

---

## 11. 工作流引擎

### 11.1 AdaptiveWorkflowEngine

**路径**: `kortex-common/kortex-common-ai/src/main/java/com/kortex/common/ai/workflow/engine/AdaptiveWorkflowEngine.java`

这是 **1255 行** 的核心引擎类，实现 AI 自主决策的循环执行机制。

#### 类结构

```java
@Slf4j
@Component
public class AdaptiveWorkflowEngine {

    private final DecisionNode decisionNode;
    private final ToolCallNode toolCallNode;
    private final ToolExecutor toolExecutor;
    private final ResultFilterNode resultFilterNode;
    private final AnswerGenerationNode answerGenerationNode;
    private final ReflectionNode reflectionNode;
    private final UnifiedMemoryService unifiedMemoryService;
    private final StateTracker stateTracker;
}
```

#### 执行流程

```java
public void execute(String userQuery, AiWorkflowState state) {
    while (!state.reachedMaxIterations()) {
        // 1. AI 决策下一步行动
        Decision decision = executeDecisionNode(state);
        
        // 2. 根据决策执行
        switch (decision.getNextAction()) {
            case CALL_TOOL:
                // 执行工具调用
                ToolCallResult result = executeToolCallNode(state, decision);
                // 检查工具结果质量
                if (resultFilterNode.shouldGenerateAnswer(result)) {
                    executeAnswerGenerationNode(state);
                    break;
                }
                continue;
                
            case DIRECT_ANSWER:
                // 直接生成答案
                executeAnswerGenerationNode(state);
                break;
                
            case REPLAN:
                // 重新规划
                executeReflectionNode(state);
                continue;
                
            case FINISH:
                // 完成
                break;
        }
        break;
    }
}
```

#### 决策动作类型

```java
public enum DecisionAction {
    CALL_TOOL,     // 调用工具获取数据
    DIRECT_ANSWER, // 直接生成答案
    REPLAN,        // 重新规划任务
    FINISH         // 完成任务
}
```

#### 核心特性

1. **循环检测**:
   - 工具调用循环检测
   - DIRECT_ANSWER + 反思循环检测
   - REPLAN 循环检测

2. **智能终止**:
   - 工具结果质量良好时直接生成答案
   - 连续成功工具调用判定数据充足

3. **流式支持**:
   - `executeWithStream()`: 流式版本
   - 实时推送 chunk 到前端

### 11.2 工作流节点

| 节点 | 功能 |
|------|------|
| `DecisionNode` | AI 决策下一步行动 |
| `ToolCallNode` | 构建工具调用请求 |
| `ResultFilterNode` | 检查工具结果质量 |
| `AnswerGenerationNode` | 生成最终答案 |
| `ReflectionNode` | 反思并重新规划 |

---

## 12. 部署与配置

### 12.1 端口分配

| 服务 | 端口 |
|------|------|
| kortex-ai | 9299 |
| kortex-gateway | 8080 |
| kortex-auth | 9200 |
| Nacos | 8848 |

### 12.2 依赖服务

| 服务 | 用途 |
|------|------|
| Nacos | 配置中心与服务发现 |
| Redis | 缓存与消息存储 |
| MySQL | 持久化存储 |
| Python Agent | 知识库/Excel/Python 代码执行 (9399端口) |
| ASR Service | 语音识别 (7998端口) |
| vLLM | 本地 LLM 部署 (可选, 8000端口) |

### 12.3 Nacos 配置模板

```yaml
spring:
  cloud:
    nacos:
      server-addr: localhost:8848
      username: nacos
      password: nacos

# AI 配置
ai:
  python-agent:
    url: http://localhost:9399
    dynamic-tools:
      enabled: true
  
  asr:
    enabled: false
    base-url: http://localhost:7998

# LLM 配置
langchain4j:
  community:
    dashscope:
      chat-model:
        enabled: true
        api-key: sk-xxxxxxxxxxxxxxxx
        model-name: qwen-max

# Langfuse 监控
langfuse:
  enabled: true
  secret-key: sk-xxxxxxxxxxxxxxxx
```

### 12.4 Python 服务部署

```bash
# 启动 Python Agent 服务 (9399端口)
cd python-agent
pip install -r requirements.txt
python app.py --port 9399
```

### 12.5 ASR 服务部署

```bash
# 启动 ASR 服务 (7998端口)
cd asr-service
pip install -r requirements.txt
python server.py --port 7998
```

---

## 13. 快速复刻指南

### 13.1 环境准备

1. **Java 21 环境**
2. **Maven 3.8+**
3. **Nacos 服务** (配置中心)
4. **Redis 服务**
5. **MySQL 服务**

### 13.2 启动步骤

1. **克隆项目**
   ```bash
   git clone <repository-url>
   cd kortex-ai-cloud
   ```

2. **编译项目**
   ```bash
   mvn clean install -DskipTests
   ```

3. **配置 Nacos**
   - 创建 `kortex-ai.yml` 配置文件
   - 填入 API Key 等配置

4. **启动服务**
   ```bash
   # 启动 kortex-ai 模块
   cd kortex-modules/kortex-ai
   mvn spring-boot:run
   ```

### 13.3 核心依赖版本

```xml
<!-- pom.xml 核心依赖 -->
<properties>
    <java.version>21</java.version>
    <spring-boot.version>3.5.6</spring-boot.version>
    <spring-cloud.version>2025.0.0</spring-cloud.version>
    <langchain4j.version>1.11.0</langchain4j.version>
    <langgraph4j.version>1.7.2</langgraph4j.version>
</properties>

<dependencies>
    <!-- LangChain4j 核心 -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j</artifactId>
        <version>${langchain4j.version}</version>
    </dependency>

    <!-- 阿里云 DashScope -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-community-dashscope-spring-boot-starter</artifactId>
        <version>${langchain4j.version}-beta19</version>
    </dependency>

    <!-- LangGraph 工作流 -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langgraph4j-core</artifactId>
        <version>${langgraph4j.version}</version>
    </dependency>

    <!-- Apache Tika 文档解析 -->
    <dependency>
        <groupId>org.apache.tika</groupId>
        <artifactId>tika-core</artifactId>
        <version>2.9.2</version>
    </dependency>
</dependencies>
```

### 13.4 最小化启动配置

要启动一个最小化的 AI 服务，你需要:

1. **kortex-ai 模块**
   - 配置 LLM (DashScope API Key)
   - 配置 Redis 连接

2. **可选组件**:
   - Python Agent (知识库/Excel功能)
   - ASR Service (语音识别功能)

---

## 附录

### A. 核心类一览表

| 模块 | 类名 | 行数 | 功能 |
|------|------|------|------|
| Agent | OrchestratorService | ~200 | 核心编排器 |
| Agent | DynamicAgentFactory | ~150 | 动态Agent工厂 |
| Agent | SubAgentTool | ~100 | 子Agent执行器 |
| Python | RemotePythonDataService | 1174 | Python工具服务 |
| Python | DynamicPythonToolProvider | 548 | 动态工具加载 |
| Workflow | AdaptiveWorkflowEngine | 1255 | 工作流引擎 |
| Monitor | LangfuseTracingListener | ~300 | 追踪监听器 |
| SSE | AiStreamPresenter | ~200 | 流式响应 |

### B. API 端点

| 端点 | 方法 | 功能 |
|------|------|------|
| `/ai/orchestrate` | POST | 同步AI对话 |
| `/ai/orchestrate/stream` | GET | 流式AI对话 |
| `/ai/asr/transcribe` | POST | 语音转文字 |
| `/ai/document/extract` | POST | 文档字段提取 |
| `/ai/matter/analyze` | POST | 事项分析 |

---

**文档版本**: 1.0
**创建时间**: 2026-03-18
**作者**: Kortex AI Team
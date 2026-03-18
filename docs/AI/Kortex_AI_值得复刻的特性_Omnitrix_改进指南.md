# Kortex AI 值得复刻的特性 - Omnitrix AI 改进指南

## 文档概述

本文档详细分析 Kortex AI 中值得 Omnitrix AI 借鉴和复刻的特性，基于代码实测和架构对比，提出具体的改进建议和实施方案。

**目标**：让 Omnitrix AI 在保持自身特色的基础上，吸收 Kortex AI 的优秀设计

---

## 目录

1. [架构层面：纯编排模式](#1-架构层面纯编排模式)
2. [工具系统：ToolProvider 动态发现](#2-工具系统toolprovider-动态发现)
3. [子 Agent 系统：SystemSubAgent 分层设计](#3-子-agent-系统systemsubagent-分层设计)
4. [监控系统：Langfuse 完整链路追踪](#4-监控系统langfuse-完整链路追踪)
5. [工作流引擎：AdaptiveWorkflowEngine](#5-工作流引擎adaptiveworkflowengine)
6. [Python 远程工具系统](#6-python-远程工具系统)
7. [内存管理：统一分布式存储](#7-内存管理统一分布式存储)
8. [SSE 流式响应优化](#8-sse-流式响应优化)
9. [实施优先级建议](#9-实施优先级建议)

---

## 1. 架构层面：纯编排模式

### 1.1 Kortex 的做法

Kortex AI 采用**纯编排模式**：Master Brain 不直接调用业务工具，所有能力通过 SubAgent 代理。

```java
// Kortex: Master Brain 只看到 SubAgent，看不到具体工具
.toolProvider(CompositeToolProvider.builder()
    .toolProviders(dynamicSubAgentToolProvider, systemSubAgentToolProvider)
    .build())
.tools(documentContentTool)  // 仅少量直接工具
```

### 1.2 Omnitrix 当前做法

Omnitrix AI 采用**混合模式**：
- UltraMasterBrain 既直接调用 UltraTools
- 又通过 SubBrain 调用 UserAI/AdminAI

### 1.3 复刻建议

**已在实施**：新增 UltraSubBrain，将 UltraTools 打包为 SubAgent

```
当前：
UltraMasterBrain → UltraTools (直接) + SubBrainTools

目标：
UltraMasterBrain → UserSubBrain + AdminSubBrain + UltraSubBrain (全部)
```

### 1.4 实施路径

| 阶段 | 任务 | 状态 |
|------|------|------|
| Phase 1 | 新增 UltraSubBrain 入口 | 进行中 |
| Phase 2 | 将 UltraTools 迁移到 UltraSubBrain | 待开始 |
| Phase 3 | 移除 UltraTools 直接注册 | 待开始 |

---

## 2. 工具系统：ToolProvider 动态发现

### 2.1 Kortex 的做法

Kortex 使用 `ToolProvider` 接口实现**动态工具发现**：

```java
@Component
@ConditionalOnProperty(prefix = "ai.python-agent.dynamic-tools", name = "enabled", havingValue = "true")
public class DynamicPythonToolProvider implements ToolProvider {
    
    private final Map<String, ToolSpecification> toolSpecifications = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        refreshToolDefinitions();
    }
    
    @Scheduled(fixedRate = 300000)  // 5分钟刷新一次
    public void refreshToolDefinitions() {
        // 从远程服务获取工具定义
        String schema = httpClient.get(pythonServiceUrl + "/tools/schema?format=openai");
        // 解析并缓存
    }
    
    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        // 动态返回工具规格和执行器
    }
}
```

### 2.2 Omnitrix 当前做法

Omnitrix 使用静态注册方式：

```java
@Component
public class ToolRegistry {
    private final Map<String, List<Object>> roleTools = new LinkedHashMap<>();
    
    public void register(String role, Object toolProvider) {
        roleTools.computeIfAbsent(role, k -> new ArrayList<>()).add(toolProvider);
    }
}
```

### 2.3 复刻建议

**推荐程度**：⭐⭐⭐☆☆（中等优先级）

**原因**：
- 当前静态注册已满足需求
- 动态发现适合工具频繁变化的场景

**可选改进**：如果未来需要支持插件化工具系统，可参考实现 `ToolProvider` 接口

---

## 3. 子 Agent 系统：SystemSubAgent 分层设计

### 3.1 Kortex 的做法

Kortex 将 SubAgent 分为两类：

| 类型 | 说明 | 示例 |
|------|------|------|
| **DynamicSubAgent** | 用户配置的 Agent，从数据库加载 | 用户自定义的工作流 Agent |
| **SystemSubAgent** | 内置系统级 Agent | GeneralToolsSubAgent、DocumentSystemSubAgent |

```java
// SystemSubAgentToolProvider.java
ToolSpecification.builder()
    .name("system_sub_agent")
    .description("调用系统子Agent执行特定任务")
    .parameters(JsonSchemaProperty.builder()
        .type(OBJECT)
        .properties(Map.of(
            "user_query", JsonSchemaProperty.builder()
                .type(STRING).description("任务指令").build(),
            "context", JsonSchemaProperty.builder()
                .type(STRING).description("参考资料").build(),
            "show_result_to_user", JsonSchemaProperty.builder()
                .type(BOOLEAN).description("是否展示给用户").build()
        ))
        .required("user_query")
        .build())
    .build()
```

### 3.2 Omnitrix 当前做法

Omnitrix 已有 SubBrain 抽象：

```java
public interface SubBrain {
    String getCode();
    String getName();
    String getDescription();
    SubBrainResult execute(String userQuery, Long targetUserId, String ultraSessionId);
}
```

### 3.3 复刻建议

**推荐程度**：⭐⭐⭐⭐⭐（高优先级）

**已实现**：
- ✅ SubBrain 接口
- ✅ UserSubBrain
- ✅ AdminSubBrain  
- ⏳ UltraSubBrain（本次新增）

**建议增强**：

1. **增强 ToolSpecification 描述**
   
   当前 SubBrain 的 `getDescription()` 直接作为 `@Tool` 注解的 description，可以进一步优化为结构化描述：

   ```java
   // 建议格式
   @Tool("调用用户端AI助手，执行用户侧操作：商城购物（搜索商品、加购物车、下单）、" +
         "内容浏览（搜索非遗项目/传承人/活动、点赞/收藏/评论）、" +
         "个人中心（查地址/资质/通知）、知识问答、推荐等。" +
         "参数格式：'查询内容' 或 '查询内容|目标用户名或ID'（为其他用户操作时提供用户信息）")
   ```

2. **新增专业领域 SubAgent**
   
   参考 Kortex 的 DocumentSystemSubAgent，可以按功能域拆分：

   ```
   UltraSubBrain
   ├── UserManageSubBrain（用户管理）
   ├── SecuritySubBrain（安全审计）
   ├── AnalyticsSubBrain（数据分析）
   └── SkillManageSubBrain（Skill管理）
   ```

---

## 4. 监控系统：Langfuse 完整链路追踪

### 4.1 Kortex 的做法

Kortex 集成 **Langfuse** 实现完整的 AI 可观测性：

```java
// LangfuseTracingListener.java
// Trace 模型结构
Langfuse Session  ←  前端 sessionId
  └── Trace       ←  一次 orchestrateStream 调用
        ├── Generation  ←  Master Brain LLM 调用
        ├── Span        ←  Sub-Agent 调度
        │     ├── Generation  ←  Sub-Agent LLM 调用
        │     └── Span        ←  Sub-Agent 工具执行
        └── Span        ←  Master Brain 直接工具执行
```

**关键特性**：
- 完整的多级 Span 嵌套
- Token 使用量统计
- Session 历史追踪
- Sub-Agent 级别的详细追踪

### 4.2 Omnitrix 当前做法

Omnitrix 有自定义的 `TelemetryTracer`：

```java
// 嵌套 Span: REQUEST → BRAIN → LLM
String traceId = telemetryTracer.recordAndReturnTraceId(...);
telemetryTracer.recordSpan(traceId, requestSpanId, null,
        "REQUEST", "request", latencyMs, "success", null);
telemetryTracer.recordSpan(traceId, brainSpanId, requestSpanId,
        "BRAIN", agentCode, latencyMs, "success", ...);
telemetryTracer.recordSpan(traceId, llmSpanId, brainSpanId,
        "LLM", usedModel, latencyMs, "success", ...);
```

### 4.3 复刻建议

**推荐程度**：⭐⭐⭐⭐☆（较高优先级）

**当前差距**：
- ❌ 缺少 SubBrain 调用级别的追踪
- ❌ 缺少工具执行级别的 Span
- ❌ 缺少多轮对话的 Session 聚合

**建议改进**：

```java
// 改进后的 Span 结构
// REQUEST → ULTRA_BRAIN → ULTRA_LLM
//                            ├── → USER_SUB_BRAIN → USER_LLM → UserTools
//                            ├── → ADMIN_SUB_BRAIN → ADMIN_LLM → AdminTools
//                            └── → ULTRA_SUB_BRAIN → ULTRA_LLM → UltraTools
```

**实施步骤**：
1. 在 `SubBrainTools` 中添加追踪埋点
2. 在各 SubBrain 执行入口添加 Span
3. 关联父 Span ID 实现完整链路

---

## 5. 工作流引擎：AdaptiveWorkflowEngine

### 5.1 Kortex 的做法

Kortex 有独立的 **AdaptiveWorkflowEngine**（1255 行）：

```java
public class AdaptiveWorkflowEngine {
    
    public void execute(String userQuery, AiWorkflowState state) {
        while (!state.reachedMaxIterations()) {
            // 1. AI 决策下一步行动
            Decision decision = executeDecisionNode(state);
            
            // 2. 根据决策执行
            switch (decision.getNextAction()) {
                case CALL_TOOL:
                    ToolCallResult result = executeToolCallNode(state, decision);
                    if (resultFilterNode.shouldGenerateAnswer(result)) {
                        executeAnswerGenerationNode(state);
                    }
                    continue;
                case DIRECT_ANSWER:
                    executeAnswerGenerationNode(state);
                    break;
                case REPLAN:
                    executeReflectionNode(state);
                    continue;
                case FINISH:
                    break;
            }
            break;
        }
    }
}
```

### 5.2 决策动作类型

```java
public enum DecisionAction {
    CALL_TOOL,     // 调用工具获取数据
    DIRECT_ANSWER, // 直接生成答案
    REPLAN,        // 重新规划任务
    FINISH         // 完成任务
}
```

### 5.3 Omnitrix 当前做法

Omnitrix 目前**没有独立的工作流引擎**，Master Brain 直接通过 LangChain4j 的 Function Calling 执行工具。

### 5.4 复刻建议

**推荐程度**：⭐⭐☆☆☆（低优先级）

**原因**：
- 当前 LangChain4j 的 Function Calling 已足够满足需求
- AdaptiveWorkflowEngine 适合复杂的多步骤任务场景

**何时需要**：
- 当需要 AI 自主规划复杂任务步骤时
- 当需要细粒度的任务状态控制时
- 当需要支持任务回滚和重试时

---

## 6. Python 远程工具系统

### 6.1 Kortex 的做法

Kortex 有完整的 Python 远程工具系统：

| 组件 | 功能 |
|------|------|
| RemotePythonDataService | 调用 Python 服务获取数据 |
| DynamicPythonToolProvider | 动态发现 Python 工具 |

**工具类型**：
- 知识库搜索
- Excel 数据分析
- 可视化图表生成
- Python 代码执行（Jupyter Kernel）

### 6.2 Omnitrix 当前做法

Omnitrix **没有独立的 Python 工具系统**，所有工具都是 Java 实现。

### 6.3 复刻建议

**推荐程度**：⭐⭐⭐☆☆（中等优先级）

**适用场景**：
- 需要复杂数据处理（Excel 分析）
- 需要动态生成可视化图表
- 需要执行 Python 代码（沙箱执行）

**实施方式**：

```java
// 新增 PythonToolProvider
@Component
@ConditionalOnProperty(prefix = "ai.python-tools", name = "enabled", havingValue = "true")
public class PythonToolProvider implements ToolProvider {
    
    private final RemotePythonDataService pythonService;
    
    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        // 从 Python 服务获取工具定义
        // 返回 ToolSpecification + ToolExecutor
    }
}
```

---

## 7. 内存管理：统一分布式存储

### 7.1 Kortex 的做法

Kortex 使用 **RedisChatMemoryStore** 统一管理对话内存：

```java
.chatMemoryProvider(memoryId -> 
    ChatMemoryBuilder.builder()
        .chatMemoryStore(redisChatMemoryStore)
        .maxTokens(4000)
        .build())
```

**优势**：
- 跨服务实例共享会话
- 支持分布式部署
- 内存预算精确控制（按 token 而非消息数）

### 7.2 Omnitrix 当前做法

Omnitrix 使用本地 `MessageWindowChatMemory` + 预加载：

```java
ChatMemory memory = MessageWindowChatMemory.builder()
        .id(sessionId)
        .maxMessages(20)  // 按消息数而非 token
        .build();
```

### 7.3 复刻建议

**推荐程度**：⭐⭐⭐☆☆（中等优先级）

**改进点**：
1. 切换到 RedisChatMemoryStore（按 token 预算）
2. 统一会话存储后，可以支持分布式部署

**可选实施**：

```java
// 方案 A：直接使用 RedisChatMemoryStore
@Bean
public ChatMemoryProvider chatMemoryProvider(RedisChatMemoryStore store) {
    return memoryId -> ChatMemoryBuilder.builder()
        .chatMemoryStore(store)
        .maxTokens(4000)  // 更精确的预算控制
        .build();
}

// 方案 B：保留当前实现，按 token 预算优化
ChatMemory memory = MessageWindowChatMemory.builder()
        .id(sessionId)
        .maxTokens(4000)  // LangChain4j 支持
        .build();
```

---

## 8. SSE 流式响应优化

### 8.1 Kortex 的做法

Kortex 的 `AiStreamPresenter` 处理 SSE：

```java
public void handleStreamResponse(Long userId, String clientId, String sessionId,
        String action, TokenStream stream, Runnable cleanupAction) {
    
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
```

### 8.2 Omnitrix 当前做法

Omnitrix 已有类似的 SSE 实现，增加了 thinking 标签处理：

```java
// 支持 <thinking> 标签的流式输出
stream.onPartialResponse(chunk -> {
    if (chunk.contains("<think>")) {
        // 处理思考过程
    }
})
```

### 8.3 复刻建议

**推荐程度**：⭐⭐⭐⭐☆（较高优先级）

Omnitrix 已有较好的 SSE 实现，可进一步优化：

1. **SSE 消息格式标准化**
2. **断线重连支持**
3. **背压处理**（流控）

---

## 9. 实施优先级建议

### 9.1 高优先级（立即行动）

| 特性 | 当前状态 | 建议 | 预计工作量 |
|------|----------|------|------------|
| UltraSubBrain | 缺失 | 新增 UltraSubBrain 入口 | 1-2 天 |
| SubBrain 追踪 | 缺失 | 在 SubBrain 调用处添加 Span | 0.5 天 |

### 9.2 中优先级（近期规划）

| 特性 | 当前状态 | 建议 | 预计工作量 |
|------|----------|------|------------|
| 内存管理 | 本地内存 | 切换到 RedisChatMemoryStore | 1-2 天 |
| SSE 优化 | 基础实现 | 增强断线重连和流控 | 1 天 |

### 9.3 低优先级（长期规划）

| 特性 | 当前状态 | 建议 | 预计工作量 |
|------|----------|------|------------|
| Python 工具 | 缺失 | 新增 PythonToolProvider | 3-5 天 |
| 工作流引擎 | 缺失 | 新增 AdaptiveWorkflowEngine | 5-10 天 |
| Langfuse 集成 | 自定义追踪 | 集成 Langfuse | 2-3 天 |

---

## 附录

### A. 代码对照表

| 功能 | Kortex 文件 | Omnitrix 对应文件 |
|------|-------------|-------------------|
| 编排器 | OrchestratorService | OrchestratorService |
| Master Brain | buildMasterBrain() | MasterBrainFactory |
| 子 Agent | SubAgentTool | SubBrainTools |
| 工具注册 | ToolProvider | ToolRegistry |
| 追踪 | LangfuseTracingListener | TelemetryTracer |
| SSE | AiStreamPresenter | SseEmitterManager |

### B. 相关文档

- `Omnitrix_AI_vs_Kortex_AI_详细对比报告.md`
- `kortex-ai-module-analysis.md`

---

**文档版本**：1.0  
**创建时间**：2026-03-18  
**作者**：AI 架构分析

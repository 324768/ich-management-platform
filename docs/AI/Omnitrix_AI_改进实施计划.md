# Omnitrix AI 改进实施计划

## 文档概述

本文档基于 `Omnitrix_AI_vs_Kortex_AI_详细对比报告.md`，制定完整的改进实施计划。

**排除项**：UltraSubBrain 拆分建议（用户明确排除）

**当前状态**：UltraSubBrain 已实现 ✓

---

## 实施计划总览

| 序号 | 改进项 | 优先级 | 预计工作量 | 状态 |
|------|--------|--------|------------|------|
| 1 | SubBrain 调用链路追踪增强 | P0 | 0.5天 | 待开始 |
| 2 | 内存管理优化：Token 预算控制 | P1 | 1天 | 待开始 |
| 3 | SSE 流式响应增强 | P1 | 1天 | 待开始 |
| 4 | AdaptiveWorkflowEngine | P2 | 10天 | 待开始 |
| 5 ~~ToolProvider 动态工具发现（可选）~~ | ~~P2~~ | ~~3天~~ | ~~暂不实现~~ |
| 6 ~~Python 远程工具系统~~ | ~~P2~~ | ~~5天~~ | ~~暂不实现~~ |
| 7 ~~Langfuse 集成~~ | ~~P3~~ | ~~3天~~ | ~~暂不实现~~ |

---

## 1. SubBrain 调用链路追踪增强

### 1.1 目标

增强 TelemetryTracer，实现完整的调用链路追踪，对齐 Kortex AI 的 Langfuse 追踪结构。

### 1.2 当前差距

```
当前 Span 结构：
REQUEST → ULTRA_BRAIN → ULTRA_LLM

目标 Span 结构：
REQUEST → ULTRA_BRAIN → ULTRA_LLM
                  ├── → USER_SUB_BRAIN → USER_LLM → UserTools
                  ├── → ADMIN_SUB_BRAIN → ADMIN_LLM → AdminTools
                  └── → ULTRA_SUB_BRAIN → ULTRA_LLM → UltraTools
```

### 1.3 实施步骤

#### Step 1.1: 修改 SubBrainTools，添加追踪埋点

```java
// 文件：SubBrainTools.java
@Tool("调用用户端AI助手...")
public String callUserQuery(String input) {
    // 添加追踪开始
    String traceId = TraceContext.getCurrentTraceId();
    String spanId = traceId + "-user-" + System.currentTimeMillis();
    telemetryTracer.recordSpan(traceId, spanId, parentSpanId, 
        "SUB_BRAIN", "user_ai", 0, "running", null);
    
    try {
        // 现有逻辑
        SubBrainResult result = userSubBrain.execute(...);
        
        // 记录成功
        telemetryTracer.recordSpan(traceId, spanId, parentSpanId,
            "SUB_BRAIN", "user_ai", duration, "success", metadata);
        return result;
    } catch (Exception e) {
        // 记录失败
        telemetryTracer.recordSpan(traceId, spanId, parentSpanId,
            "SUB_BRAIN", "user_ai", duration, "error", errorMsg);
        throw e;
    }
}
```

#### Step 1.2: 修改各 SubBrain 实现，添加执行追踪

```java
// 文件：UserSubBrain.java, AdminSubBrain.java, UltraSubBrain.java
// 在 execute() 方法开始和结束时添加追踪
```

### 1.4 验证方式

- 调用 Ultra 查询，观察日志/追踪平台
- 确认 Span 链路完整嵌套

---

## 2. 内存管理优化：Token 预算控制

### 2.1 目标

将对话内存从"按消息数"切换为"按 Token 预算"控制，更精确管理上下文。

### 2.2 当前实现

```java
// MasterBrainFactory.java
ChatMemory memory = MessageWindowChatMemory.builder()
        .id(sessionId)
        .maxMessages(20)  // 按消息数
        .build();
```

### 2.3 目标实现

```java
// 方案 A：使用 RedisChatMemoryStore（推荐）
.chatMemoryProvider(memoryId -> 
    ChatMemoryBuilder.builder()
        .chatMemoryStore(redisChatMemoryStore)
        .maxTokens(4000)  // 按 Token 数
        .build())

// 方案 B：保留本地内存，改为按 Token 预算
ChatMemory memory = MessageWindowChatMemory.builder()
        .id(sessionId)
        .maxTokens(4000)  // LangChain4j 支持
        .build();
```

### 2.4 实施步骤

#### Step 2.1: 检查现有 RedisChatMemoryStore 实现

```java
// 文件：infrastructure/memory/RedisChatMemoryStore.java
// 确认是否已实现
```

#### Step 2.2: 修改 MasterBrainFactory

```java
// 文件：MasterBrainFactory.java
private ChatMemory buildMemory(String sessionId) {
    // 改为按 Token 预算
    return MessageWindowChatMemory.builder()
            .id(sessionId)
            .maxTokens(4000)  // 替换 maxMessages(20)
            .build();
}
```

### 2.5 验证方式

- 对话超过 20 轮后，检查旧消息是否被正确压缩
- Token 计数准确

---

## 3. SSE 流式响应增强

### 3.1 目标

增强 SSE 响应，支持断线重连和流控。

### 3.2 当前功能

- 流式输出
- thinking 标签处理
- 错误处理

### 3.3 增强功能

1. **SSE 消息格式标准化**
   
   ```json
   {
     "action": "chat",
     "type": "chunk|start|complete|error",
     "content": "...",
     "timestamp": 1700000000000,
     "traceId": "xxx"
   }
   ```

2. **断线重连支持**
   
   - 客户端重连时传递 lastMessageId
   - 服务端从上次中断位置继续

3. **背压处理**
   
   - 流控：客户端处理速度跟不上时，暂停推送
   - 超时处理：长时间空闲断开连接

### 3.4 实施步骤

#### Step 3.1: 标准化 SSE 消息格式

```java
// 文件：SseEmitterManager.java
public void sendChunk(SseEmitter emitter, String content) {
    SseEvent event = SseEvent.builder()
            .type("chunk")
            .content(content)
            .traceId(TraceContext.getCurrentTraceId())
            .timestamp(System.currentTimeMillis())
            .build();
    emitter.send(event);
}
```

#### Step 3.2: 添加重连支持

```java
// Controller 层
@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter stream(@RequestParam String sessionId,
                         @RequestParam(required = false) String lastEventId) {
    if (lastEventId != null) {
        // 从上次中断位置继续
        return resumeStream(sessionId, lastEventId);
    }
    return createStream(sessionId);
}
```

### 3.5 验证方式

- 模拟断线，观察重连是否正常工作
- 检查消息格式是否符合规范

---

## 4. ToolProvider 动态工具发现（可选）

### 4.1 目标

支持运行时动态发现和加载工具，参考 Kortex 的 DynamicPythonToolProvider。

### 4.2 适用场景

- 插件化工具系统
- 工具频繁变化
- 需要从远程服务加载工具

### 4.3 当前状态

Omnitrix 使用静态注册（ToolRegistry），已满足当前需求。

### 4.4 实施建议（可选）

如果未来需要，可实现 `ToolProvider` 接口：

```java
@Component
@ConditionalOnProperty(prefix = "ai.dynamic-tools", name = "enabled", havingValue = "true")
public class DynamicToolProvider implements ToolProvider {
    
    private final Map<String, ToolSpecification> toolSpecs = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        refreshToolDefinitions();
    }
    
    @Scheduled(fixedRate = 300000)  // 5分钟刷新
    public void refreshToolDefinitions() {
        // 从配置源加载工具定义
    }
    
    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        // 返回匹配的 ToolSpecification + ToolExecutor
    }
}
```

---

## 5. Python 远程工具系统

### 5.1 目标

支持 Python 侧的工具服务，如 Excel 分析、图表生成、代码执行等。

### 5.2 Kortex 实现参考

| 组件 | 功能 |
|------|------|
| RemotePythonDataService | 调用 Python 服务 |
| DynamicPythonToolProvider | 动态发现工具 |

### 5.3 工具类型

- 知识库搜索
- Excel 数据分析
- 可视化图表生成
- Python 代码执行

### 5.4 实施步骤

#### Step 5.1: 创建 Python 工具服务接口

```java
@Component
@ConditionalOnProperty(prefix = "ai.python-tools", name = "enabled", havingValue = "true")
public class RemotePythonToolService {
    
    @Value("${ai.python-tools.url}")
    private String pythonServiceUrl;
    
    // 知识库搜索
    public JSONObject knowledgeSearch(String query, String libraryId, int topK) { ... }
    
    // Excel 分析
    public JSONObject analyzeExcel(String fileId, String sheetName) { ... }
    
    // 图表生成
    public JSONObject generateChart(String fileId, String chartType) { ... }
}
```

#### Step 5.2: 创建 Python 工具提供者（可选）

```java
@Component
@ConditionalOnProperty(prefix = "ai.python-tools.dynamic", name = "enabled", havingValue = "true")
public class PythonToolProvider implements ToolProvider {
    // 动态发现 Python 工具
}
```

---

## 6. Langfuse 集成（可选）

### 6.1 目标

集成 Langfuse 实现企业级 AI 可观测性。

### 6.2 当前状态

Omnitrix 有自定义的 TelemetryTracer。

### 6.3 Langfuse 优势

- 开箱即用的 AI 追踪平台
- 丰富的可视化仪表板
- Token 成本分析
- 多版本对比

### 6.4 实施步骤

#### Step 6.1: 添加依赖

```xml
<dependency>
    <groupId>io.langchain4j</groupId>
    <artifactId>langchain4j-langfuse</artifactId>
    <version>${langchain4j.version}</version>
</dependency>
```

#### Step 6.2: 配置 Langfuse

```yaml
langfuse:
  enabled: true
  secret-key: sk-xxx
  public-key: pk-xxx
  base-url: https://cloud.langfuse.com
```

#### Step 6.3: 添加监听器

```java
@Bean
public LangfuseTracingListener langfuseTracingListener(LangfuseClient langfuseClient) {
    return new LangfuseTracingListener(langfuseClient);
}
```

---

## 7. AdaptiveWorkflowEngine

### 7.1 目标

实现 AI 自主决策的循环执行引擎，支持复杂多步骤任务的智能规划和执行。

### 7.2 Kortex 实现分析

**核心组件**（1255行）：

| 组件 | 功能 |
|------|------|
| AdaptiveWorkflowEngine | 核心引擎 |
| DecisionNode | AI 决策下一步行动 |
| ToolCallNode | 构建工具调用请求 |
| ResultFilterNode | 检查工具结果质量 |
| AnswerGenerationNode | 生成最终答案 |
| ReflectionNode | 反思并重新规划 |

**决策动作类型**：

```java
public enum DecisionAction {
    CALL_TOOL,     // 调用工具获取数据
    DIRECT_ANSWER, // 直接生成答案
    REPLAN,        // 重新规划任务
    FINISH         // 完成任务
}
```

### 7.3 当前差距

Omnitrix 当前使用 LangChain4j 的 Function Calling：
- 缺乏显式的**结果质量评估**
- 缺乏**反思重规划**机制
- 无法智能终止（直到达到最大轮次）

### 7.4 实施步骤

#### Step 7.1: 创建工作流状态管理

```java
// 文件：infrastructure/workflow/AiWorkflowState.java
@Data
public class AiWorkflowState {
    private String sessionId;
    private String userQuery;
    private int currentIteration;
    private int maxIterations;
    
    private List<ToolCallRecord> toolCalls;
    private List<String> reflections;
    private String currentAnswer;
    private DecisionAction lastAction;
    private boolean terminated;
}
```

#### Step 7.2: 创建决策节点

```java
// 文件：infrastructure/workflow/DecisionNode.java
@Component
public class DecisionNode {
    public Decision decide(AiWorkflowState state) {
        // 构建决策 Prompt
        // 调用 LLM 决策下一步行动
        // 解析 DecisionAction
    }
}
```

#### Step 7.3: 创建结果过滤节点

```java
// 文件：infrastructure/workflow/ResultFilterNode.java
@Component
public class ResultFilterNode {
    public boolean shouldGenerateAnswer(ToolCallResult result, AiWorkflowState state) {
        // 检查工具调用是否成功
        // 检查结果是否为空
        // 连续成功调用检查
        // 关键词满足度检查
    }
}
```

#### Step 7.4: 创建反思节点

```java
// 文件：infrastructure/workflow/ReflectionNode.java
@Component
public class ReflectionNode {
    public ReflectionResult reflect(AiWorkflowState state) {
        // 分析当前问题
        // 检测循环模式
        // 生成新计划
    }
}
```

#### Step 7.5: 创建核心引擎

```java
// 文件：infrastructure/workflow/AdaptiveWorkflowEngine.java
@Slf4j
@Component
public class AdaptiveWorkflowEngine {
    public String execute(String userQuery, List<Object> tools, int maxIterations) {
        while (!state.isTerminated() && !state.reachedMaxIterations()) {
            // 1. AI 决策下一步行动
            Decision decision = decisionNode.decide(state);
            
            // 2. 执行决策
            switch (decision.getAction()) {
                case CALL_TOOL:
                    // 执行工具
                    break;
                case DIRECT_ANSWER:
                    // 生成答案
                    break;
                case REPLAN:
                    // 反思重规划
                    break;
                case FINISH:
                    // 完成任务
                    break;
            }
        }
    }
}
```

### 7.5 使用场景建议

| 场景 | 推荐模式 |
|------|----------|
| 简单问答 | Function Calling |
| 多步骤复杂任务 | Workflow |
| 数据分析 | Workflow |

### 7.6 文件结构

```
infrastructure/workflow/
├── AdaptiveWorkflowEngine.java
├── AiWorkflowState.java
├── DecisionNode.java
├── ToolCallNode.java
├── ResultFilterNode.java
├── AnswerGenerationNode.java
└── ReflectionNode.java
```

---

## 实施优先级建议

### 立即行动（P0-P1）

1. **SubBrain 调用链路追踪增强**
   - 价值：高
   - 工作量：0.5天
   - 效果：可视化调试，问题定位

2. **内存管理优化**
   - 价值：中
   - 工作量：1天
   - 效果：更精确的上下文控制

3. **SSE 流式响应增强**
   - 价值：中
   - 工作量：1天
   - 效果：更好的用户体验

### 近期规划（P2）

4. **Python 远程工具系统**
   - 价值：中（取决于业务需求）
   - 工作量：5天
   - 效果：扩展 AI 能力边界

### 长期规划（P3）

5. **Langfuse 集成**
6. **AdaptiveWorkflowEngine**

---

## 验收标准

每个改进项完成后，需要满足：

| 改进项 | 验收标准 |
|--------|----------|
| 追踪增强 | Span 链路完整嵌套，可追溯完整调用链 |
| 内存优化 | Token 预算生效，历史消息正确压缩 |
| SSE 增强 | 断线重连正常，消息格式统一 |
| 动态工具 | 工具热加载生效 |
| Python 工具 | Excel/图表/代码执行功能正常 |

---

## 相关文档

- `Omnitrix_AI_vs_Kortex_AI_详细对比报告.md`
- `Kortex_AI_值得复刻的特性_Omnitrix_改进指南.md`

---

**文档版本**：1.0  
**创建时间**：2026-03-18  
**作者**：AI 架构分析

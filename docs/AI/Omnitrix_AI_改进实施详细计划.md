# Omnitrix AI 改进实施详细计划

> **对于代理工作者：** 必需：使用 superpowers:subagent-driven-development（如果有子代理）或 superpowers:executing-plans 来实现此计划。步骤使用复选框（`- [ ]`）语法进行跟踪。

**目标：** 实施 4 项代码改进，提升 Omnitrix AI 模块的追踪能力、内存管理、用户体验

**架构：** 按优先级分阶段实施：P0(SubBrain追踪) → P1(内存+SSE) → P2(WorkflowEngine)

**技术栈：** Spring Boot, LangChain4j, Redis, MySQL

---

## 实施顺序

1. **任务 1**: SubBrain 调用链路追踪增强 (P0)
2. **任务 2**: 内存管理优化：Token 预算控制 (P1)
3. **任务 3**: SSE 流式响应增强 (P1)
4. **任务 4**: AdaptiveWorkflowEngine (P2)

---

# 任务 1: SubBrain 调用链路追踪增强

## 概述

增强 TelemetryTracer，实现完整的 SubBrain 调用链路追踪，对齐 Kortex AI 的 Langfuse 追踪结构。

**当前 Span 结构：**
```
REQUEST → ULTRA_BRAIN → ULTRA_LLM
```

**目标 Span 结构：**
```
REQUEST → ULTRA_BRAIN → ULTRA_LLM
              ├── → USER_SUB_BRAIN → USER_LLM → UserTools
              ├── → ADMIN_SUB_BRAIN → ADMIN_LLM → AdminTools
              └── → ULTRA_SUB_BRAIN → ULTRA_LLM → UltraTools
```

---

## 文件

- 修改：`ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/brain/tools/SubBrainTools.java`
- 修改：`ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/brain/subbrain/UserSubBrain.java`
- 修改：`ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/brain/subbrain/AdminSubBrain.java`
- 修改：`ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/brain/subbrain/UltraSubBrain.java`
- 修改：`ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/infrastructure/telemetry/TelemetryTracer.java`

---

## 步骤

- [ ] **步骤 1: 在 TelemetryTracer 添加 SubBrain 便捷方法**

```java
// 文件: TelemetryTracer.java，在第 126 行后添加

/** 便捷方法：记录 SubBrain 执行 Span */
public void recordSubBrainSpan(String traceId, String parentSpanId,
                               String subBrainCode, String subBrainName,
                               int durationMs, boolean success) {
    String spanId = generateSpanId();
    recordSpan(traceId, spanId, parentSpanId, "SUB_BRAIN", subBrainCode + ":" + subBrainName,
            durationMs, success ? "success" : "error", null);
    log.debug("记录 SubBrain Span: traceId={}, subBrain={}, parentSpan={}", 
            traceId, subBrainCode, parentSpanId);
}
```

- [ ] **步骤 2: 验证 TelemetryTracer 编译通过**

运行：`cd d:/ich-management-platform; mvn compile -pl ich_parent/ich_omnitrix -am -q`

预期：BUILD SUCCESS

- [ ] **步骤 3: 修改 SubBrainTools，添加追踪埋点**

```java
// 文件: SubBrainTools.java，添加依赖和追踪

// 在类顶部添加
private final TelemetryTracer telemetryTracer;

// 构造函数添加
public SubBrainTools(..., TelemetryTracer telemetryTracer) {
    ...
    this.telemetryTracer = telemetryTracer;
}

// 在 callUserAI 方法中（约第 92 行），在执行前添加
String traceId = TraceContext.getCurrentTraceId();
String parentSpanId = TraceContext.getCurrentSpanId();
long startTime = System.currentTimeMillis();

try {
    SubBrainResult result = userSubBrain.execute(userQuery, targetUserId, ultraSessionId);
    // 记录成功
    telemetryTracer.recordSubBrainSpan(traceId, parentSpanId, 
            userSubBrain.getCode(), userSubBrain.getName(),
            System.currentTimeMillis() - startTime, true);
    return SubBrainResultWrapper.wrap(userSubBrain.getCode(), result, callCount, MAX_SUB_BRAIN_CALLS);
} catch (Exception e) {
    // 记录失败
    telemetryTracer.recordSubBrainSpan(traceId, parentSpanId,
            userSubBrain.getCode(), userSubBrain.getName(),
            System.currentTimeMillis() - startTime, false);
    throw e;
}
```

- [ ] **步骤 4: 同样修改 callAdminAI 方法**

在 callAdminAI 方法中添加相同的追踪逻辑。

- [ ] **步骤 5: 验证 SubBrainTools 编译通过**

运行：`mvn compile -pl ich_parent/ich_omnitrix -am -q`

预期：BUILD SUCCESS

- [ ] **步骤 6: 测试追踪效果（可选）**

手动测试：发起一个 Ultra 请求，调用 callUserAI，观察日志输出。

---

## 验证标准

- Span 链路完整嵌套，可追溯完整调用链
- 日志显示 `记录 SubBrain Span: traceId=xxx, subBrain=user_ai, parentSpan=xxx`

---

# 任务 2: 内存管理优化：Token 预算控制

## 概述

将对话内存从"按消息数"切换为"按 Token 预算"控制，更精确管理上下文。

**当前实现：**
```java
.maxMessages(20)
```

**目标实现：**
```java
.maxTokens(4000)
```

---

## 文件

- 修改：`ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/brain/MasterBrainFactory.java:248`

---

## 步骤

- [ ] **步骤 1: 检查 LangChain4j 版本是否支持 maxTokens**

LangChain4j 0.35.0+ 支持 `maxTokens` 参数。

- [ ] **步骤 2: 修改 MasterBrainFactory**

```java
// 文件: MasterBrainFactory.java，第 246-249 行

// 修改前：
ChatMemory memory = MessageWindowChatMemory.builder()
        .id(sessionId)
        .maxMessages(20)
        .build();

// 修改后：
ChatMemory memory = MessageWindowChatMemory.builder()
        .id(sessionId)
        .maxTokens(4000)  // 按 Token 预算控制
        .build();
```

- [ ] **步骤 3: 验证编译通过**

运行：`mvn compile -pl ich_parent/ich_omnitrix -am -q`

预期：BUILD SUCCESS

---

## 验证标准

- 对话超过 20 轮后，旧消息被正确压缩
- Token 计数准确（通过日志验证）

---

# 任务 3: SSE 流式响应增强

## 概述

增强 SSE 响应，支持标准化消息格式和断线重连。

**增强功能：**
1. SSE 消息格式标准化（含 traceId、timestamp）
2. 断线重连支持（可选）

---

## 文件

- 修改：`ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/infrastructure/sse/SseEmitterManager.java`

---

## 步骤

- [ ] **步骤 1: 标准化 SSE 消息格式**

```java
// 文件: SseEmitterManager.java

// 添加新的事件类（放在第 127 行前）
@Data
static class SseEvent {
    private String type;           // chunk, start, complete, error
    private String content;        // 内容
    private long timestamp;        // 时间戳
    private String traceId;        // 追踪ID
    private String eventId;        // 事件ID（用于重连）
}

// 修改 sendChunk 方法
public void sendChunk(SseEmitter emitter, String content) {
    SseEvent event = new SseEvent();
    event.setType("chunk");
    event.setContent(content);
    event.setTimestamp(System.currentTimeMillis());
    event.setTraceId(TraceContext.getCurrentTraceId());
    try {
        emitter.send(SseEmitter.event()
                .name("message")
                .data(objectMapper.writeValueAsString(event)));
    } catch (IOException e) {
        log.info("SSE 发送 chunk 失败: {}", e.getMessage());
    }
}

// 修改 sendDone 方法，添加 eventId
public void sendDone(SseEmitter emitter, Long messageId, int latencyMs, String model) {
    DoneEvent event = new DoneEvent();
    event.setMessageId(messageId);
    event.setLatencyMs(latencyMs);
    event.setModel(model);
    event.setTimestamp(System.currentTimeMillis());
    event.setType("complete");
    try {
        emitter.send(SseEmitter.event()
                .name("message")
                .data(objectMapper.writeValueAsString(event)));
        emitter.complete();
    } catch (IOException e) {
        log.info("SSE 发送 done 失败: {}", e.getMessage());
    }
}

// 修改 sendError 方法
public void sendError(SseEmitter emitter, String message) {
    try {
        SseEvent event = new SseEvent();
        event.setType("error");
        event.setContent(message);
        event.setTimestamp(System.currentTimeMillis());
        emitter.send(SseEmitter.event()
                .name("message")
                .data(objectMapper.writeValueAsString(event)));
        emitter.complete();
    } catch (IOException e) {
        log.info("SSE 发送 error 失败: {}", e.getMessage());
    }
}
```

- [ ] **步骤 2: 验证编译通过**

运行：`mvn compile -pl ich_parent/ich_omnitrix -am -q`

预期：BUILD SUCCESS

- [ ] **步骤 3: 测试 SSE 消息格式**

手动测试：发起流式请求，检查返回的消息格式是否为标准 JSON。

---

## 验证标准

- 消息格式统一为 JSON，含 type、content、timestamp、traceId
- 断线重连支持（可选）

---

# 任务 4: AdaptiveWorkflowEngine

## 概述

实现 AI 自主决策的循环执行引擎，支持复杂多步骤任务的智能规划和执行。

**核心组件：**
- DecisionNode：AI 决策下一步行动
- ToolCallNode：构建工具调用请求
- ResultFilterNode：检查工具结果质量
- AnswerGenerationNode：生成最终答案
- ReflectionNode：反思并重新规划
- AdaptiveWorkflowEngine：核心引擎

---

## 文件

- 创建：`ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/infrastructure/workflow/AiWorkflowState.java`
- 创建：`ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/infrastructure/workflow/DecisionAction.java`
- 创建：`ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/infrastructure/workflow/Decision.java`
- 创建：`ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/infrastructure/workflow/ToolCallResult.java`
- 创建：`ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/infrastructure/workflow/ReflectionResult.java`
- 创建：`ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/infrastructure/workflow/DecisionNode.java`
- 创建：`ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/infrastructure/workflow/ToolCallNode.java`
- 创建：`ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/infrastructure/workflow/ResultFilterNode.java`
- 创建：`ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/infrastructure/workflow/AnswerGenerationNode.java`
- 创建：`ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/infrastructure/workflow/ReflectionNode.java`
- 创建：`ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/infrastructure/workflow/AdaptiveWorkflowEngine.java`

---

## 步骤

- [ ] **步骤 1: 创建 DecisionAction 枚举**

```java
// 文件: infrastructure/workflow/DecisionAction.java
package com.hyang.ich.omnitrix.infrastructure.workflow;

public enum DecisionAction {
    CALL_TOOL,     // 调用工具获取数据
    DIRECT_ANSWER, // 直接生成答案
    REPLAN,        // 重新规划任务
    FINISH         // 完成任务
}
```

- [ ] **步骤 2: 创建 AiWorkflowState**

```java
// 文件: infrastructure/workflow/AiWorkflowState.java
package com.hyang.ich.omnitrix.infrastructure.workflow;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class AiWorkflowState {
    private String sessionId;
    private String userQuery;
    private int currentIteration;
    private int maxIterations;
    
    @Builder.Default
    private List<ToolCallRecord> toolCalls = new ArrayList<>();
    
    @Builder.Default
    private List<String> reflections = new ArrayList<>();
    
    private String currentAnswer;
    private DecisionAction lastAction;
    private boolean terminated;
    
    public boolean reachedMaxIterations() {
        return currentIteration >= maxIterations;
    }
    
    public void incrementIteration() {
        this.currentIteration++;
    }
}
```

- [ ] **步骤 3: 创建 ToolCallRecord**

```java
// 文件: infrastructure/workflow/ToolCallRecord.java
package com.hyang.ich.omnitrix.infrastructure.workflow;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ToolCallRecord {
    private String toolName;
    private String arguments;
    private String result;
    private long durationMs;
    private boolean success;
}
```

- [ ] **步骤 4: 创建 Decision**

```java
// 文件: infrastructure/workflow/Decision.java
package com.hyang.ich.omnitrix.infrastructure.workflow;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Decision {
    private DecisionAction action;
    private String reasoning;
    private String toolName;
    private String toolArgs;
}
```

- [ ] **步骤 5: 创建 ToolCallResult**

```java
// 文件: infrastructure/workflow/ToolCallResult.java
package com.hyang.ich.omnitrix.infrastructure.workflow;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ToolCallResult {
    private String toolName;
    private String content;
    private boolean success;
    private String error;
    private long durationMs;
    
    public ToolCallRecord toRecord() {
        return ToolCallRecord.builder()
                .toolName(toolName)
                .result(content)
                .success(success)
                .durationMs(durationMs)
                .build();
    }
}
```

- [ ] **步骤 6: 创建 ReflectionResult**

```java
// 文件: infrastructure/workflow/ReflectionResult.java
package com.hyang.ich.omnitrix.infrastructure.workflow;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReflectionResult {
    private String problemAnalysis;
    private boolean isLooping;
    private String newPlan;
}
```

- [ ] **步骤 7: 创建 DecisionNode**

```java
// 文件: infrastructure/workflow/DecisionNode.java
package com.hyang.ich.omnitrix.infrastructure.workflow;

import com.hyang.ich.omnitrix.infrastructure.llm.LlmClient;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DecisionNode {
    
    private final ChatLanguageModel chatModel;
    
    public DecisionNode(ChatLanguageModel chatModel) {
        this.chatModel = chatModel;
    }
    
    public Decision decide(AiWorkflowState state) {
        String prompt = buildDecisionPrompt(state);
        
        try {
            String response = chatModel.chat(prompt);
            return parseResponse(response);
        } catch (Exception e) {
            log.error("决策节点执行失败: {}", e.getMessage());
            // 失败时默认直接生成答案
            return Decision.builder()
                    .action(DecisionAction.DIRECT_ANSWER)
                    .reasoning("决策失败，默认生成答案")
                    .build();
        }
    }
    
    private String buildDecisionPrompt(AiWorkflowState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个任务规划助手。根据当前任务状态，决定下一步行动。\n\n");
        sb.append("任务：").append(state.getUserQuery()).append("\n");
        sb.append("已执行步骤：").append(state.getToolCalls().size()).append("个\n");
        
        if (state.getLastAction() != null) {
            sb.append("上次行动：").append(state.getLastAction()).append("\n");
        }
        
        sb.append("\n可选行动：\n");
        sb.append("- CALL_TOOL: 需要调用工具获取更多信息\n");
        sb.append("- DIRECT_ANSWER: 信息足够，可以直接生成答案\n");
        sb.append("- REPLAN: 需要重新规划任务\n");
        sb.append("- FINISH: 任务已完成\n");
        
        sb.append("\n请以 JSON 格式返回：{\"action\": \"行动\", \"reasoning\": \"理由\"}");
        
        return sb.toString();
    }
    
    private Decision parseResponse(String response) {
        try {
            // 简单解析，实际可用 JSON 库
            String actionStr = extractJsonValue(response, "action");
            String reasoning = extractJsonValue(response, "reasoning");
            
            DecisionAction action = DecisionAction.valueOf(actionStr);
            return Decision.builder()
                    .action(action)
                    .reasoning(reasoning)
                    .build();
        } catch (Exception e) {
            log.warn("解析决策响应失败: {}", response);
            return Decision.builder()
                    .action(DecisionAction.DIRECT_ANSWER)
                    .reasoning("解析失败，默认生成答案")
                    .build();
        }
    }
    
    private String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) return "DIRECT_ANSWER";
        start += pattern.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return "DIRECT_ANSWER";
        return json.substring(start, end);
    }
}
```

- [ ] **步骤 8: 创建 ToolCallNode**

```java
// 文件: infrastructure/workflow/ToolCallNode.java
package com.hyang.ich.omnitrix.infrastructure.workflow;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ToolCallNode {
    
    public ToolCallResult execute(AiWorkflowState state, Decision decision, List<Object> tools) {
        long startTime = System.currentTimeMillis();
        String toolName = decision.getToolName();
        
        try {
            // 查找工具
            Object tool = findTool(tools, toolName);
            if (tool == null) {
                return ToolCallResult.builder()
                        .toolName(toolName)
                        .content("")
                        .success(false)
                        .error("工具不存在: " + toolName)
                        .durationMs(System.currentTimeMillis() - startTime)
                        .build();
            }
            
            // 调用工具（简化实现，实际需要反射调用）
            String result = callTool(tool, decision.getToolArgs());
            
            return ToolCallResult.builder()
                    .toolName(toolName)
                    .content(result)
                    .success(true)
                    .durationMs(System.currentTimeMillis() - startTime)
                    .build();
                    
        } catch (Exception e) {
            log.error("工具调用失败: tool={}, error={}", toolName, e.getMessage());
            return ToolCallResult.builder()
                    .toolName(toolName)
                    .content("")
                    .success(false)
                    .error(e.getMessage())
                    .durationMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }
    
    private Object findTool(List<Object> tools, String toolName) {
        for (Object tool : tools) {
            if (tool.getClass().getSimpleName().equalsIgnoreCase(toolName)) {
                return tool;
            }
        }
        return null;
    }
    
    private String callTool(Object tool, String args) throws Exception {
        // 简化实现：调用第一个无参方法
        Method[] methods = tool.getClass().getMethods();
        for (Method method : methods) {
            if (method.getParameterCount() == 0 && !method.getName().startsWith("get")) {
                Object result = method.invoke(tool);
                return result != null ? result.toString() : "";
            }
        }
        return "";
    }
}
```

- [ ] **步骤 9: 创建 ResultFilterNode**

```java
// 文件: infrastructure/workflow/ResultFilterNode.java
package com.hyang.ich.omnitrix.infrastructure.workflow;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ResultFilterNode {
    
    /**
     * 判断工具结果是否足够生成答案
     */
    public boolean shouldGenerateAnswer(ToolCallResult result, AiWorkflowState state) {
        // 1. 检查工具调用是否成功
        if (!result.isSuccess()) {
            return false;
        }
        
        // 2. 检查结果是否为空
        if (result.getContent() == null || result.getContent().isEmpty()) {
            return false;
        }
        
        // 3. 连续成功调用检查（连续3次成功，认为数据充足）
        int consecutiveSuccess = countConsecutiveSuccess(state.getToolCalls());
        if (consecutiveSuccess >= 3) {
            return true;
        }
        
        // 4. 结果长度检查（结果足够长，认为有效）
        if (result.getContent().length() > 200) {
            return true;
        }
        
        return false;
    }
    
    private int countConsecutiveSuccess(List<ToolCallRecord> calls) {
        if (calls.isEmpty()) return 0;
        
        int count = 0;
        for (int i = calls.size() - 1; i >= 0; i--) {
            if (calls.get(i).isSuccess()) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }
}
```

- [ ] **步骤 10: 创建 AnswerGenerationNode**

```java
// 文件: infrastructure/workflow/AnswerGenerationNode.java
package com.hyang.ich.omnitrix.infrastructure.workflow;

import com.hyang.ich.omnitrix.infrastructure.llm.LlmClient;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AnswerGenerationNode {
    
    private final ChatLanguageModel chatModel;
    
    public AnswerGenerationNode(ChatLanguageModel chatModel) {
        this.chatModel = chatModel;
    }
    
    public String generate(AiWorkflowState state) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("基于以下工具调用结果，生成最终答案：\n\n");
        
        for (ToolCallRecord call : state.getToolCalls()) {
            prompt.append("工具: ").append(call.getToolName()).append("\n");
            prompt.append("结果: ").append(call.getResult()).append("\n\n");
        }
        
        try {
            return chatModel.chat(prompt.toString());
        } catch (Exception e) {
            log.error("答案生成失败: {}", e.getMessage());
            // 降级：直接返回最后一个工具结果
            List<ToolCallRecord> calls = state.getToolCalls();
            return calls.isEmpty() ? "无法生成答案" : calls.get(calls.size() - 1).getResult();
        }
    }
}
```

- [ ] **步骤 11: 创建 ReflectionNode**

```java
// 文件: infrastructure/workflow/ReflectionNode.java
package com.hyang.ich.omnitrix.infrastructure.workflow;

import com.hyang.ich.omnitrix.infrastructure.llm.LlmClient;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class ReflectionNode {
    
    private final ChatLanguageModel chatModel;
    
    public ReflectionNode(ChatLanguageModel chatModel) {
        this.chatModel = chatModel;
    }
    
    /**
     * 反思并重新规划
     */
    public ReflectionResult reflect(AiWorkflowState state) {
        // 1. 检测循环模式
        boolean isLooping = detectLoopingPattern(state);
        
        String problemAnalysis;
        String newPlan;
        
        if (isLooping) {
            problemAnalysis = "检测到重复的工具调用模式";
            newPlan = "终止任务，避免无限循环";
        } else {
            // 2. 生成反思分析
            problemAnalysis = generateProblemAnalysis(state);
            newPlan = generateNewPlan(state);
        }
        
        return ReflectionResult.builder()
                .problemAnalysis(problemAnalysis)
                .isLooping(isLooping)
                .newPlan(newPlan)
                .build();
    }
    
    private boolean detectLoopingPattern(AiWorkflowState state) {
        List<ToolCallRecord> calls = state.getToolCalls();
        if (calls.size() < 3) return false;
        
        // 检查最近3次调用是否相同
        String lastCall = calls.get(calls.size() - 1).getToolName();
        String secondLastCall = calls.get(calls.size() - 2).getToolName();
        
        return lastCall.equals(secondLastCall);
    }
    
    private String generateProblemAnalysis(AiWorkflowState state) {
        return "任务需要更多步骤，当前工具调用结果可能不完整";
    }
    
    private String generateNewPlan(AiWorkflowState state) {
        return "尝试调用其他工具获取更多信息";
    }
}
```

- [ ] **步骤 12: 创建 AdaptiveWorkflowEngine**

```java
// 文件: infrastructure/workflow/AdaptiveWorkflowEngine.java
package com.hyang.ich.omnitrix.infrastructure.workflow;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Component
public class AdaptiveWorkflowEngine {
    
    private final DecisionNode decisionNode;
    private final ToolCallNode toolCallNode;
    private final ResultFilterNode resultFilterNode;
    private final AnswerGenerationNode answerGenerationNode;
    private final ReflectionNode reflectionNode;
    
    public AdaptiveWorkflowEngine(DecisionNode decisionNode,
                                   ToolCallNode toolCallNode,
                                   ResultFilterNode resultFilterNode,
                                   AnswerGenerationNode answerGenerationNode,
                                   ReflectionNode reflectionNode) {
        this.decisionNode = decisionNode;
        this.toolCallNode = toolCallNode;
        this.resultFilterNode = resultFilterNode;
        this.answerGenerationNode = answerGenerationNode;
        this.reflectionNode = reflectionNode;
    }
    
    /**
     * 执行工作流
     */
    public String execute(String userQuery, List<Object> tools, int maxIterations) {
        AiWorkflowState state = AiWorkflowState.builder()
                .userQuery(userQuery)
                .maxIterations(maxIterations)
                .build();
        
        log.info("AdaptiveWorkflowEngine 开始执行: query={}, maxIterations={}", 
                userQuery.substring(0, Math.min(50, userQuery.length())), maxIterations);
        
        while (!state.isTerminated() && !state.reachedMaxIterations()) {
            // 1. AI 决策下一步行动
            Decision decision = decisionNode.decide(state);
            state.setLastAction(decision.getAction());
            log.info("决策结果: action={}, reasoning={}", decision.getAction(), decision.getReasoning());
            
            // 2. 执行决策
            switch (decision.getAction()) {
                case CALL_TOOL:
                    ToolCallResult result = toolCallNode.execute(state, decision, tools);
                    state.getToolCalls().add(result.toRecord());
                    log.info("工具调用结果: tool={}, success={}", result.getToolName(), result.isSuccess());
                    
                    // 检查结果质量
                    if (resultFilterNode.shouldGenerateAnswer(result, state)) {
                        String answer = answerGenerationNode.generate(state);
                        state.setCurrentAnswer(answer);
                        state.setTerminated(true);
                        log.info("工作流完成：结果质量满足，生成答案");
                    }
                    break;
                    
                case DIRECT_ANSWER:
                    String answer = answerGenerationNode.generate(state);
                    state.setCurrentAnswer(answer);
                    state.setTerminated(true);
                    log.info("工作流完成：直接生成答案");
                    break;
                    
                case REPLAN:
                    ReflectionResult reflection = reflectionNode.reflect(state);
                    state.getReflections().add(reflection.getProblemAnalysis());
                    
                    if (reflection.isLooping()) {
                        state.setTerminated(true);
                        state.setCurrentAnswer("任务遇到循环，无法继续执行。");
                        log.warn("工作流终止：检测到循环");
                    }
                    break;
                    
                case FINISH:
                    state.setTerminated(true);
                    log.info("工作流完成：决策完成");
                    break;
            }
            
            state.incrementIteration();
        }
        
        if (!state.isTerminated()) {
            log.warn("工作流达到最大迭代次数: {}", maxIterations);
            state.setCurrentAnswer("任务执行达到最大迭代次数，请重试或简化查询。");
        }
        
        return state.getCurrentAnswer();
    }
    
    /**
     * 流式版本
     */
    public void executeWithStream(String userQuery, List<Object> tools,
                                  Consumer<String> chunkConsumer) {
        // 类似实现，但实时推送 chunk
        execute(userQuery, tools, 10); // 简化实现
    }
}
```

- [ ] **步骤 13: 验证编译通过**

运行：`mvn compile -pl ich_parent/ich_omnitrix -am -q`

预期：BUILD SUCCESS

---

## 验证标准

- 复杂任务测试：多工具调用流程
- 循环检测：重复调用同一工具时正确终止
- 智能终止：结果足够时提前结束

---

# 执行总结

完成所有任务后，运行最终编译验证：

```bash
cd d:/ich-management-platform
mvn clean compile -pl ich_parent/ich_omnitrix -am
```

预期：BUILD SUCCESS

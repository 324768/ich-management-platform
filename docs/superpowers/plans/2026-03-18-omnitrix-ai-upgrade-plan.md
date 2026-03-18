# Omnitrix AI 技术改进实现计划

> **对于代理工作者：** 必需：按计划顺序执行每个任务。

**目标：** 升级 ClaudeStreamingModel 为真正的流式（使用 LangChain4j 原生 Anthropic 支持），完善 AdaptiveWorkflowEngine 为完整的 AI 自主决策工作流引擎。

**架构：**
- Claude 流式：使用 LangChain4j 原生的 `langchain4j-anthropic` 模块，避免自行实现 SSE 解析
- 工作流引擎：参考 Kortex 的 1255 行实现，扩展当前简化版，增加循环检测、流式支持、结果质量检查

**技术栈：** LangChain4j 0.35.0、Spring Boot、WebClient (Reactive)

---

## 任务 1: 升级 ClaudeStreamingModel

### 1.1 添加 LangChain4j Anthropic 依赖

**文件：**
- 修改：`ich_parent/ich_omnitrix/pom.xml`

- [ ] **步骤 1: 添加 Anthropic 支持依赖**

在 `<dependencies>` 中添加：

```xml
<!-- LangChain4j Anthropic Claude 支持 -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-anthropic</artifactId>
    <version>${langchain4j.version}</version>
</dependency>

<!-- Spring WebFlux（提供 WebClient） -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

- [ ] **步骤 2: 验证依赖可用**

运行：
```bash
cd ich_parent/ich_omnitrix
mvn dependency:tree | grep -E "anthropic|webflux"
```
预期：显示 langchain4j-anthropic 和 spring-boot-starter-webflux

---

### 1.2 创建 Anthropic 配置类

**文件：**
- 创建：`ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/infrastructure/llm/AnthropicChatModelConfig.java`

- [ ] **步骤 1: 创建配置类**

```java
package com.hyang.ich.omnitrix.infrastructure.llm;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Anthropic Claude 模型配置
 * 使用 LangChain4j 原生支持
 */
@Slf4j
@Configuration
public class AnthropicChatModelConfig {

    /**
     * 流式 Chat Model - 用于 SSE 流式响应
     */
    @Bean
    public AnthropicStreamingChatModel anthropicStreamingChatModel(LlmProperties properties) {
        LlmProperties.ClaudeConfig config = properties.getClaudeConfig();
        
        if (!config.isConfigured()) {
            log.warn("Claude API 未配置，AnthropicStreamingChatModel 将不可用");
            return null;
        }
        
        log.info("初始化 AnthropicStreamingChatModel: model={}", config.getModel());
        
        return AnthropicStreamingChatModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModel())
                .maxTokens(config.getMaxOutputTokens())
                .temperature(config.getTemperature())
                .timeout(java.time.Duration.ofSeconds(config.getTimeoutSeconds()))
                .build();
    }
    
    /**
     * 同步 Chat Model - 用于普通对话
     */
    @Bean
    public AnthropicChatModel anthropicChatModel(LlmProperties properties) {
        LlmProperties.ClaudeConfig config = properties.getClaudeConfig();
        
        if (!config.isConfigured()) {
            log.warn("Claude API 未配置，AnthropicChatModel 将不可用");
            return null;
        }
        
        return AnthropicChatModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModel())
                .maxTokens(config.getMaxOutputTokens())
                .temperature(config.getTemperature())
                .timeout(java.time.Duration.ofSeconds(config.getTimeoutSeconds()))
                .build();
    }
}
```

- [ ] **步骤 2: 编译验证**

运行：
```bash
cd ich_parent/ich_omnitrix
mvn compile -q
```
预期：编译成功，无错误

---

### 1.3 修改 MasterBrainFactory 使用新 Bean

**文件：**
- 修改：`ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/brain/MasterBrainFactory.java`

- [ ] **步骤 1: 添加新依赖注入**

找到构造函数，添加：

```java
private final AnthropicStreamingChatModel anthropicStreamingChatModel;
```

修改构造函数参数：

```java
public MasterBrainFactory(
        ChatLanguageModel chatLanguageModel,
        @org.springframework.beans.factory.annotation.Qualifier("auxiliaryChatModel") ChatLanguageModel auxiliaryChatModel,
        StreamingChatLanguageModel streamingChatLanguageModel,
        AnthropicStreamingChatModel anthropicStreamingChatModel,  // 新增
        ToolRegistry toolRegistry,
        SkillPromptConfig skillPromptConfig,
        SkillSelector skillSelector,
        UserMemoryService userMemoryService,
        ChatMemoryManager chatMemoryManager) {
    // ... 现有代码 ...
    this.anthropicStreamingChatModel = anthropicStreamingChatModel;
}
```

- [ ] **步骤 2: 修改 getStreamingModel 方法**

将：

```java
private StreamingChatLanguageModel getStreamingModel(String modelCode) {
    if (isClaudeModel(modelCode)) {
        log.debug("使用 Claude 流式模型: {}", modelCode);
        return claudeStreamingModel;
    }
    log.debug("使用默认 SiliconFlow 流式模型");
    return streamingChatLanguageModel;
}
```

改为：

```java
private StreamingChatLanguageModel getStreamingModel(String modelCode) {
    if (isClaudeModel(modelCode) && anthropicStreamingChatModel != null) {
        log.debug("使用 LangChain4j Anthropic 流式模型: {}", modelCode);
        return anthropicStreamingChatModel;
    }
    log.debug("使用默认 SiliconFlow 流式模型");
    return streamingChatLanguageModel;
}
```

- [ ] **步骤 3: 清理旧代码**

删除或注释掉旧的 ClaudeStreamingModel 相关字段引用（如果有）

- [ ] **步骤 4: 编译验证**

运行：
```bash
cd ich_parent/ich_omnitrix
mvn compile -q
```
预期：编译成功

---

### 1.4 删除旧 ClaudeStreamingModel 实现

**文件：**
- 删除：`ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/infrastructure/llm/ClaudeStreamingModel.java`

- [ ] **步骤 1: 确认新实现工作后删除旧文件**

确认编译通过后，删除旧文件。

---

## 任务 2: 完善 AdaptiveWorkflowEngine

### 2.1 扩展 AiWorkflowState 状态类

**文件：**
- 修改：`ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/infrastructure/workflow/AiWorkflowState.java`

- [ ] **步骤 1: 扩展状态类**

将现有内容替换为更完整的实现：

```java
package com.hyang.ich.omnitrix.infrastructure.workflow;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流执行状态
 * 参考 Kortex AiWorkflowState 设计
 */
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
    
    // ========== Kortex 扩展字段 ==========
    
    // 用户画像/上下文
    private String userProfile;
    
    // 对话历史摘要
    private String conversationSummary;
    
    // 意图分析结果
    private String intentAnalysis;
    
    // 工具调用循环检测
    @Builder.Default
    private Map<String, Integer> toolCallCounts = new HashMap<>();
    
    // REPLAN 循环检测
    @Builder.Default
    private List<String> replanHistory = new ArrayList<>();
    
    // 上一个工具调用结果
    private String lastToolResult;
    
    // 连续成功工具调用计数（用于判定数据充足）
    @Builder.Default
    private int consecutiveSuccessfulToolCalls = 0;
    
    // 当前是否在反思模式
    @Builder.Default
    private boolean inReflectionMode = false;
    
    // 错误信息
    private String errorMessage;
    
    // ========== 方法 ==========
    
    public boolean reachedMaxIterations() {
        return currentIteration >= maxIterations;
    }
    
    public void incrementIteration() {
        this.currentIteration++;
    }
    
    /**
     * 记录工具调用 - 用于循环检测
     */
    public void recordToolCall(String toolName) {
        toolCallCounts.merge(toolName, 1, Integer::sum);
    }
    
    /**
     * 检查是否陷入工具调用循环
     */
    public boolean isToolCallLooping() {
        // 同一个工具调用超过 3 次视为循环
        return toolCallCounts.values().stream().anyMatch(count -> count > 3);
    }
    
    /**
     * 记录反思 - 用于循环检测
     */
    public void recordReplan(String reflection) {
        replanHistory.add(reflection);
    }
    
    /**
     * 检查是否陷入反思循环
     */
    public boolean isReplanLooping() {
        // 连续超过 2 次 REPLAN 视为循环
        if (replanHistory.size() < 2) return false;
        return replanHistory.get(replanHistory.size() - 1)
                .equals(replanHistory.get(replanHistory.size() - 2));
    }
    
    /**
     * 重置连续成功计数
     */
    public void resetConsecutiveSuccess() {
        this.consecutiveSuccessfulToolCalls = 0;
    }
    
    /**
     * 增加连续成功计数
     */
    public void incrementConsecutiveSuccess() {
        this.consecutiveSuccessfulToolCalls++;
    }
    
    /**
     * 判断数据是否充足（连续成功工具调用 >= 2）
     */
    public boolean hasSufficientData() {
        return consecutiveSuccessfulToolCalls >= 2;
    }
}
```

- [ ] **步骤 2: 编译验证**

运行：
```bash
mvn compile -q -pl ich_parent/ich_omnitrix
```
预期：编译成功

---

### 2.2 完善 DecisionNode

**文件：**
- 修改：`ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/infrastructure/workflow/DecisionNode.java`

- [ ] **步骤 1: 实现完整的 LLM 驱动决策**

替换现有代码为：

```java
package com.hyang.ich.omnitrix.infrastructure.workflow;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.service.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 决策节点 - AI 自主决策下一步行动
 * 参考 Kortex DecisionNode 设计
 */
@Slf4j
@Component
public class DecisionNode {
    
    private final ChatLanguageModel chatModel;
    private final StreamingChatLanguageModel streamingChatModel;
    
    private static final String DECISION_PROMPT = """
            你是一个任务规划助手。根据当前任务状态，决定下一步行动。

            【任务】
            %s

            【已执行步骤】
            %s

            【上次行动】
            %s

            【已有信息】
            %s

            【可用行动】
            - CALL_TOOL: 需要调用工具获取更多信息（如查询数据库、搜索内容等）
            - DIRECT_ANSWER: 信息足够，可以直接生成答案
            - REPLAN: 任务复杂或遇到问题，需要重新规划
            - FINISH: 任务已完成

            【决策原则】
            1. 如果任务需要获取最新信息、查询数据、搜索内容 → CALL_TOOL
            2. 如果已有足够信息回答用户问题 → DIRECT_ANSWER
            3. 如果之前行动失败或遇到困难 → REPLAN
            4. 如果任务已完成 → FINISH

            请以 JSON 格式返回：
            {"action": "行动名称", "reasoning": "简短理由", "toolName": "工具名(仅CALL_TOOL需要)", "toolArgs": "参数(仅CALL_TOOL需要)"}
            """;

    public DecisionNode(ChatLanguageModel chatModel,
                        StreamingChatLanguageModel streamingChatModel) {
        this.chatModel = chatModel;
        this.streamingChatModel = streamingChatModel;
    }
    
    /**
     * 同步决策
     */
    public Decision decide(AiWorkflowState state) {
        String prompt = buildDecisionPrompt(state);
        
        try {
            Result<String> result = chatModel.chat(prompt);
            String content = result.content();
            
            Decision decision = parseResponse(content);
            log.info("决策结果: action={}, reasoning={}, toolName={}", 
                    decision.getAction(), decision.getReasoning(), decision.getToolName());
            
            return decision;
            
        } catch (Exception e) {
            log.error("决策节点执行失败: {}", e.getMessage());
            return Decision.builder()
                    .action(DecisionAction.DIRECT_ANSWER)
                    .reasoning("决策失败，默认生成答案: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * 构建决策 Prompt
     */
    private String buildDecisionPrompt(AiWorkflowState state) {
        StringBuilder sb = new StringBuilder();
        
        // 任务
        sb.append(String.format(DECISION_PROMPT,
                state.getUserQuery(),
                formatToolCalls(state.getToolCalls()),
                state.getLastAction() != null ? state.getLastAction().name() : "无",
                formatCurrentInfo(state)
        ));
        
        return sb.toString();
    }
    
    private String formatToolCalls(List<ToolCallRecord> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return "暂无";
        }
        
        StringBuilder sb = new StringBuilder();
        for (ToolCallRecord call : toolCalls) {
            sb.append(String.format("- %s: %s\n", call.getToolName(), 
                    call.getResult() != null ? call.getResult().substring(0, Math.min(100, call.getResult().length())) : ""));
        }
        return sb.toString();
    }
    
    private String formatCurrentInfo(AiWorkflowState state) {
        StringBuilder sb = new StringBuilder();
        
        if (state.getLastToolResult() != null) {
            sb.append("最新工具结果: ").append(state.getLastToolResult().substring(0, 
                    Math.min(200, state.getLastToolResult().length()))).append("\n");
        }
        
        if (state.isToolCallLooping()) {
            sb.append("⚠️ 检测到工具调用循环\n");
        }
        
        if (state.isReplanLooping()) {
            sb.append("⚠️ 检测到反思循环\n");
        }
        
        return sb.length() > 0 ? sb.toString() : "暂无";
    }
    
    /**
     * 解析 LLM 响应
     */
    private Decision parseResponse(String response) {
        try {
            // 简单 JSON 解析
            String actionStr = extractJsonValue(response, "action");
            String reasoning = extractJsonValue(response, "reasoning");
            String toolName = extractJsonValue(response, "toolName");
            String toolArgs = extractJsonValue(response, "toolArgs");
            
            DecisionAction action;
            try {
                action = DecisionAction.valueOf(actionStr);
            } catch (IllegalArgumentException e) {
                action = DecisionAction.DIRECT_ANSWER;
            }
            
            return Decision.builder()
                    .action(action)
                    .reasoning(reasoning)
                    .toolName(toolName)
                    .toolArgs(toolArgs)
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
        // 简单的 JSON 解析
        String pattern = "\"" + key + "\"";
        int start = json.indexOf(pattern);
        if (start == -1) return null;
        
        start = json.indexOf(":", start) + 1;
        // 跳过空格
        while (start < json.length() && json.charAt(start) == ' ') start++;
        
        if (start >= json.length()) return null;
        
        char quote = json.charAt(start);
        if (quote != '"' && quote != '{' && quote != '[') {
            // 数字或布尔值
            int end = start;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') {
                end++;
            }
            return json.substring(start, end).trim();
        }
        
        if (quote == '"') {
            start++;
            int end = start;
            while (end < json.length() && json.charAt(end) != '"') {
                if (json.charAt(end) == '\\') end++; // skip escaped
                end++;
            }
            return json.substring(start, end);
        }
        
        // 对象或数组
        int end = json.indexOf(",", start);
        if (end == -1) end = json.indexOf("}", start);
        if (end == -1) return null;
        
        return json.substring(start, end).trim();
    }
}
```

- [ ] **步骤 2: 编译验证**

运行：
```bash
mvn compile -q -pl ich_parent/ich_omnitrix
```

---

### 2.3 完善 ToolCallNode

**文件：**
- 修改：`ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/infrastructure/workflow/ToolCallNode.java`

- [ ] **步骤 1: 完善工具调用实现**

替换为：

```java
package com.hyang.ich.omnitrix.infrastructure.workflow;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 工具调用节点
 * 参考 Kortex ToolCallNode 设计
 */
@Slf4j
@Component
public class ToolCallNode {
    
    /**
     * 执行工具调用
     */
    public ToolCallResult execute(AiWorkflowState state, Decision decision, List<Object> tools) {
        long startTime = System.currentTimeMillis();
        String toolName = decision.getToolName();
        
        if (toolName == null || toolName.isEmpty()) {
            return ToolCallResult.builder()
                    .toolName("unknown")
                    .content("")
                    .success(false)
                    .error("决策未指定工具名称")
                    .durationMs((int) (System.currentTimeMillis() - startTime))
                    .build();
        }
        
        try {
            // 查找工具
            Object tool = findTool(tools, toolName);
            if (tool == null) {
                return ToolCallResult.builder()
                        .toolName(toolName)
                        .content("")
                        .success(false)
                        .error("工具不存在: " + toolName)
                        .durationMs((int) (System.currentTimeMillis() - startTime))
                        .build();
            }
            
            // 调用工具
            String args = decision.getToolArgs();
            String result = callTool(tool, toolName, args);
            
            // 记录到状态
            state.recordToolCall(toolName);
            state.setLastToolResult(result);
            
            log.info("工具调用成功: tool={}, resultLength={}, duration={}ms", 
                    toolName, result != null ? result.length() : 0, 
                    System.currentTimeMillis() - startTime);
            
            return ToolCallResult.builder()
                    .toolName(toolName)
                    .content(result)
                    .success(true)
                    .durationMs((int) (System.currentTimeMillis() - startTime))
                    .build();
                    
        } catch (Exception e) {
            log.error("工具调用失败: tool={}, error={}", toolName, e.getMessage());
            state.resetConsecutiveSuccess();
            
            return ToolCallResult.builder()
                    .toolName(toolName)
                    .content("")
                    .success(false)
                    .error(e.getMessage())
                    .durationMs((int) (System.currentTimeMillis() - startTime))
                    .build();
        }
    }
    
    /**
     * 查找工具
     */
    private Object findTool(List<Object> tools, String toolName) {
        if (tools == null || toolName == null) return null;
        
        for (Object tool : tools) {
            // 匹配类名或简单名
            if (tool.getClass().getSimpleName().equalsIgnoreCase(toolName) ||
                tool.getClass().getName().toLowerCase().contains(toolName.toLowerCase())) {
                return tool;
            }
            
            // 匹配 @Tool 注解的方法名
            for (Method method : tool.getClass().getMethods()) {
                if (method.isAnnotationPresent(dev.langchain4j.agent.tool.Tool.class)) {
                    dev.langchain4j.agent.tool.Tool annotation = 
                            method.getAnnotation(dev.langchain4j.agent.tool.Tool.class);
                    if (annotation.value().equalsIgnoreCase(toolName) ||
                        annotation.name().equalsIgnoreCase(toolName)) {
                        return tool;
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * 调用工具方法
     */
    private String callTool(Object tool, String toolName, String args) throws Exception {
        // 首先尝试找 @Tool 注解的方法
        for (Method method : tool.getClass().getMethods()) {
            if (method.isAnnotationPresent(dev.langchain4j.agent.tool.Tool.class)) {
                dev.langchain4j.agent.tool.Tool annotation = 
                        method.getAnnotation(dev.langchain4j.agent.tool.Tool.class);
                
                if (annotation.value().equalsIgnoreCase(toolName) ||
                    annotation.name().equalsIgnoreCase(toolName) ||
                    method.getName().equalsIgnoreCase(toolName)) {
                    
                    // 尝试调用
                    if (method.getParameterCount() == 0) {
                        Object result = method.invoke(tool);
                        return result != null ? result.toString() : "";
                    } else if (method.getParameterCount() == 1) {
                        Object result = method.invoke(tool, args != null ? args : "");
                        return result != null ? result.toString() : "";
                    }
                }
            }
        }
        
        // 回退：调用第一个无参方法
        for (Method method : tool.getClass().getMethods()) {
            if (!method.getName().startsWith("get") && 
                !method.getName().startsWith("set") &&
                method.getParameterCount() == 0) {
                Object result = method.invoke(tool);
                return result != null ? result.toString() : "";
            }
        }
        
        return "";
    }
}
```

- [ ] **步骤 2: 编译验证**

运行：
```bash
mvn compile -q -pl ich_parent/ich_omnitrix
```

---

### 2.4 完善 ResultFilterNode

**文件：**
- 修改：`ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/infrastructure/workflow/ResultFilterNode.java`

- [ ] **步骤 1: 完善结果质量检查**

```java
package com.hyang.ich.omnitrix.infrastructure.workflow;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 结果过滤器节点
 * 检查工具结果质量，决定是否生成答案
 * 参考 Kortex ResultFilterNode 设计
 */
@Slf4j
@Component
public class ResultFilterNode {
    
    // 质量阈值
    private static final int MIN_RESULT_LENGTH = 10;
    private static final double MIN_SUCCESS_RATE = 0.5;
    
    /**
     * 判断是否应该生成答案
     * 
     * @param result 工具调用结果
     * @param state 工作流状态
     * @return true 表示结果质量足够，可以生成答案
     */
    public boolean shouldGenerateAnswer(ToolCallResult result, AiWorkflowState state) {
        if (result == null) {
            log.debug("结果为空，不生成答案");
            return false;
        }
        
        // 工具调用失败
        if (!result.isSuccess()) {
            log.debug("工具调用失败，不生成答案: {}", result.getError());
            return false;
        }
        
        // 结果为空
        String content = result.getContent();
        if (content == null || content.isEmpty()) {
            log.debug("结果为空，不生成答案");
            return false;
        }
        
        // 结果太短
        if (content.length() < MIN_RESULT_LENGTH) {
            log.debug("结果太短(length={})，继续调用工具", content.length());
            return false;
        }
        
        // 检测到循环
        if (state.isToolCallLooping()) {
            log.warn("检测到工具调用循环，强制生成答案");
            return true;
        }
        
        // 连续成功调用 >= 2，认为数据充足
        if (state.hasSufficientData()) {
            log.info("连续成功工具调用 >= 2，数据充足，生成答案");
            return true;
        }
        
        // 成功一次，增加计数
        state.incrementConsecutiveSuccess();
        
        log.debug("工具调用成功但数据可能不足，继续: consecutiveSuccess={}", 
                state.getConsecutiveSuccess());
        
        return false;
    }
    
    /**
     * 检查结果是否表示"未找到"
     */
    public boolean isEmptyResult(String content) {
        if (content == null) return true;
        
        String lower = content.toLowerCase();
        return lower.contains("未找到") || 
               lower.contains("没有找到") ||
               lower.contains("不存在") ||
               lower.contains("null") ||
               lower.contains("empty") ||
               lower.length() < MIN_RESULT_LENGTH;
    }
}
```

---

### 2.5 完善 ReflectionNode

**文件：**
- 修改：`ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/infrastructure/workflow/ReflectionNode.java`

- [ ] **步骤 1: 完善反思节点实现**

```java
package com.hyang.ich.omnitrix.infrastructure.workflow;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 反思节点 - 任务失败或复杂时重新规划
 * 参考 Kortex ReflectionNode 设计
 */
@Slf4j
@Component
public class ReflectionNode {
    
    private final ChatLanguageModel chatModel;
    
    private static final String REFLECTION_PROMPT = """
            你是一个任务反思助手。分析当前任务执行情况，找出问题和解决方案。

            【原始任务】
            %s

            【已执行的工具调用】
            %s

            【上次行动】
            %s

            【问题分析】
            - 工具调用失败了吗？
            - 获得了结果但不够用？
            - 陷入循环了？

            【请分析并返回 JSON】
            {"problemAnalysis": "问题分析", "suggestedAction": "下一步建议", "looping": true/false}
            """;
    
    public ReflectionNode(ChatLanguageModel chatModel) {
        this.chatModel = chatModel;
    }
    
    /**
     * 执行反思
     */
    public ReflectionResult reflect(AiWorkflowState state) {
        String prompt = buildReflectionPrompt(state);
        
        try {
            Result<String> result = chatModel.chat(prompt);
            String content = result.content();
            
            ReflectionResult reflection = parseReflection(content);
            
            // 记录反思历史
            state.recordReplan(reflection.getProblemAnalysis());
            
            log.info("反思完成: looping={}, suggestedAction={}", 
                    reflection.isLooping(), reflection.getSuggestedAction());
            
            return reflection;
            
        } catch (Exception e) {
            log.error("反思执行失败: {}", e.getMessage());
            return ReflectionResult.builder()
                    .problemAnalysis("反思失败: " + e.getMessage())
                    .suggestedAction("FINISH")
                    .looping(false)
                    .build();
        }
    }
    
    private String buildReflectionPrompt(AiWorkflowState state) {
        return String.format(REFLECTION_PROMPT,
                state.getUserQuery(),
                formatToolCalls(state.getToolCalls()),
                state.getLastAction() != null ? state.getLastAction().name() : "无"
        );
    }
    
    private String formatToolCalls(java.util.List<ToolCallRecord> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return "暂无";
        }
        
        StringBuilder sb = new StringBuilder();
        for (ToolCallRecord call : toolCalls) {
            sb.append(String.format("- %s: %s (success=%s)\n", 
                    call.getToolName(), 
                    call.getResult() != null ? call.getResult().substring(0, 50) : "无结果",
                    call.isSuccess()));
        }
        return sb.toString();
    }
    
    private ReflectionResult parseReflection(String content) {
        try {
            String problem = extractJsonValue(content, "problemAnalysis");
            String suggested = extractJsonValue(content, "suggestedAction");
            String loopingStr = extractJsonValue(content, "looping");
            
            boolean looping = "true".equalsIgnoreCase(loopingStr);
            
            return ReflectionResult.builder()
                    .problemAnalysis(problem != null ? problem : "无")
                    .suggestedAction(suggested != null ? suggested : "FINISH")
                    .looping(looping)
                    .build();
                    
        } catch (Exception e) {
            log.warn("解析反思结果失败: {}", content);
            return ReflectionResult.builder()
                    .problemAnalysis("解析失败")
                    .suggestedAction("FINISH")
                    .looping(false)
                    .build();
        }
    }
    
    private String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\"";
        int start = json.indexOf(pattern);
        if (start == -1) return null;
        
        start = json.indexOf(":", start) + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;
        
        if (start >= json.length()) return null;
        
        char quote = json.charAt(start);
        if (quote == '"') {
            start++;
            int end = start;
            while (end < json.length() && json.charAt(end) != '"') {
                if (json.charAt(end) == '\\') end++;
                end++;
            }
            return json.substring(start, end);
        }
        
        int end = start;
        while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') {
            end++;
        }
        return json.substring(start, end).trim();
    }
}
```

---

### 2.6 完善 AdaptiveWorkflowEngine 主引擎

**文件：**
- 修改：`ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/infrastructure/workflow/AdaptiveWorkflowEngine.java`

- [ ] **步骤 1: 完善主引擎实现**

替换为完整实现：

```java
package com.hyang.ich.omnitrix.infrastructure.workflow;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * 自适应工作流引擎 - AI 自主决策的循环执行引擎
 * 参考 Kortex AdaptiveWorkflowEngine (1255行) 设计
 */
@Slf4j
@Component
public class AdaptiveWorkflowEngine {
    
    private final DecisionNode decisionNode;
    private final ToolCallNode toolCallNode;
    private final ResultFilterNode resultFilterNode;
    private final AnswerGenerationNode answerGenerationNode;
    private final ReflectionNode reflectionNode;
    
    // 默认最大迭代次数
    private static final int DEFAULT_MAX_ITERATIONS = 10;
    
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
     * 执行工作流（同步版本）
     */
    public String execute(String userQuery, List<Object> tools) {
        return execute(userQuery, tools, DEFAULT_MAX_ITERATIONS);
    }
    
    /**
     * 执行工作流（指定迭代次数）
     */
    public String execute(String userQuery, List<Object> tools, int maxIterations) {
        AiWorkflowState state = AiWorkflowState.builder()
                .userQuery(userQuery)
                .maxIterations(maxIterations)
                .build();
        
        log.info("AdaptiveWorkflowEngine 开始执行: query={}, maxIterations={}", 
                userQuery.substring(0, Math.min(50, userQuery.length())), maxIterations);
        
        // 主循环
        while (!state.isTerminated() && !state.reachedMaxIterations()) {
            // 1. AI 决策下一步行动
            Decision decision = decisionNode.decide(state);
            state.setLastAction(decision.getAction());
            
            log.info("决策: action={}, reasoning={}, toolName={}", 
                    decision.getAction(), decision.getReasoning(), decision.getToolName());
            
            // 2. 根据决策执行
            switch (decision.getAction()) {
                case CALL_TOOL:
                    handleToolCall(state, decision, tools);
                    break;
                    
                case DIRECT_ANSWER:
                    handleDirectAnswer(state);
                    break;
                    
                case REPLAN:
                    handleReplan(state);
                    break;
                    
                case FINISH:
                    state.setTerminated(true);
                    log.info("工作流完成：决策完成");
                    break;
                    
                default:
                    log.warn("未知决策: {}", decision.getAction());
                    handleDirectAnswer(state);
            }
            
            state.incrementIteration();
        }
        
        // 3. 检查终止条件
        if (!state.isTerminated()) {
            if (state.reachedMaxIterations()) {
                log.warn("工作流达到最大迭代次数: {}", maxIterations);
                state.setCurrentAnswer("任务执行达到最大迭代次数(" + maxIterations + ")，请重试或简化查询。");
            }
            if (state.getErrorMessage() != null) {
                state.setCurrentAnswer("任务执行出错: " + state.getErrorMessage());
            }
        }
        
        return state.getCurrentAnswer();
    }
    
    /**
     * 处理工具调用
     */
    private void handleToolCall(AiWorkflowState state, Decision decision, List<Object> tools) {
        // 执行工具调用
        ToolCallResult result = toolCallNode.execute(state, decision, tools);
        
        // 记录工具调用
        state.getToolCalls().add(result.toRecord());
        
        log.info("工具调用结果: tool={}, success={}, duration={}ms", 
                result.getToolName(), result.isSuccess(), result.getDurationMs());
        
        // 检查结果质量
        if (resultFilterNode.shouldGenerateAnswer(result, state)) {
            // 结果质量足够，生成答案
            String answer = answerGenerationNode.generate(state);
            state.setCurrentAnswer(answer);
            state.setTerminated(true);
            log.info("工作流完成：结果质量满足，生成答案");
        } else if (!result.isSuccess()) {
            // 工具调用失败，尝试反思
            state.resetConsecutiveSuccess();
            if (!state.isToolCallLooping()) {
                // 可以继续尝试其他工具
                log.info("工具调用失败，继续尝试");
            }
        }
        // 否则继续循环
    }
    
    /**
     * 处理直接生成答案
     */
    private void handleDirectAnswer(AiWorkflowState state) {
        String answer = answerGenerationNode.generate(state);
        state.setCurrentAnswer(answer);
        state.setTerminated(true);
        log.info("工作流完成：直接生成答案");
    }
    
    /**
     * 处理重新规划
     */
    private void handleReplan(AiWorkflowState state) {
        ReflectionResult reflection = reflectionNode.reflect(state);
        state.getReflections().add(reflection.getProblemAnalysis());
        
        log.info("反思结果: problem={}, looping={}", 
                reflection.getProblemAnalysis(), reflection.isLooping());
        
        if (reflection.isLooping() || state.isReplanLooping()) {
            // 陷入循环，终止
            state.setTerminated(true);
            state.setCurrentAnswer("任务遇到循环，无法继续执行。建议您重新描述问题。");
            log.warn("工作流终止：检测到反思循环");
        }
        // 否则继续循环
    }
    
    /**
     * 流式版本 - 带回调
     * 注意：当前实现是简化版，真正的流式需要更复杂的实现
     */
    public void executeWithStream(String userQuery, List<Object> tools, Consumer<String> chunkConsumer) {
        // 简化实现：先执行同步版本，然后逐字发送
        String result = execute(userQuery, tools);
        
        // 模拟流式发送
        if (chunkConsumer != null && result != null) {
            // 按句子发送（简化处理）
            String[] sentences = result.split("(?<=[。！？.!?])");
            for (String sentence : sentences) {
                if (!sentence.trim().isEmpty()) {
                    chunkConsumer.accept(sentence);
                    try {
                        Thread.sleep(50); // 模拟延迟
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }
    
    /**
     * 流式版本 - 带完整状态回调
     */
    public void executeWithStateStream(String userQuery, List<Object> tools, 
                                       Consumer<WorkflowEvent> eventConsumer) {
        AiWorkflowState state = AiWorkflowState.builder()
                .userQuery(userQuery)
                .maxIterations(DEFAULT_MAX_ITERATIONS)
                .build();
        
        // 发送开始事件
        if (eventConsumer != null) {
            eventConsumer.accept(new WorkflowEvent("START", userQuery, null, 0));
        }
        
        while (!state.isTerminated() && !state.reachedMaxIterations()) {
            long startTime = System.currentTimeMillis();
            
            // 决策
            Decision decision = decisionNode.decide(state);
            state.setLastAction(decision.getAction());
            
            if (eventConsumer != null) {
                eventConsumer.accept(new WorkflowEvent("DECISION", 
                        decision.getAction().name(), decision.getReasoning(), 
                        (int) (System.currentTimeMillis() - startTime)));
            }
            
            // 执行
            switch (decision.getAction()) {
                case CALL_TOOL:
                    handleToolCall(state, decision, tools);
                    if (eventConsumer != null) {
                        ToolCallRecord lastCall = state.getToolCalls()
                                .get(state.getToolCalls().size() - 1);
                        eventConsumer.accept(new WorkflowEvent("TOOL_RESULT",
                                lastCall.getToolName(), lastCall.getResult(),
                                lastCall.getDurationMs()));
                    }
                    break;
                    
                case DIRECT_ANSWER:
                    handleDirectAnswer(state);
                    if (eventConsumer != null) {
                        eventConsumer.accept(new WorkflowEvent("ANSWER", 
                                state.getCurrentAnswer(), null, 0));
                    }
                    break;
                    
                case REPLAN:
                    handleReplan(state);
                    break;
                    
                case FINISH:
                    state.setTerminated(true);
                    break;
            }
            
            state.incrementIteration();
        }
        
        if (eventConsumer != null) {
            eventConsumer.accept(new WorkflowEvent("COMPLETE", 
                    state.getCurrentAnswer(), null, 0));
        }
    }
    
    /**
     * 工作流事件
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class WorkflowEvent {
        private String eventType;  // START, DECISION, TOOL_RESULT, ANSWER, COMPLETE
        private String content;
        private String metadata;
        private int durationMs;
    }
}
```

- [ ] **步骤 2: 编译验证**

运行：
```bash
mvn compile -q -pl ich_parent/ich_omnitrix
```
预期：编译成功

---

### 2.7 更新相关 DTO

**文件：**
- 修改：`ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/infrastructure/workflow/Decision.java`

- [ ] **步骤 1: 添加 toolName 和 toolArgs 字段**

```java
package com.hyang.ich.omnitrix.infrastructure.workflow;

import lombok.Builder;
import lombok.Data;

/**
 * 决策结果
 */
@Data
@Builder
public class Decision {
    private DecisionAction action;
    private String reasoning;
    
    // 新增字段
    private String toolName;      // CALL_TOOL 时指定工具
    private String toolArgs;      // 工具参数
}
```

- [ ] **步骤 2: 编译验证**

---

## 任务 3: 验证测试

- [ ] **步骤 1: 运行编译**

```bash
cd ich_parent/ich_omnitrix
mvn compile -q
```

- [ ] **步骤 2: 启动应用测试**

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

- [ ] **步骤 3: 测试 API**

调用流式接口验证 SSE 是否正常工作。

---

## 总结

完成以上任务后，你将拥有：
1. **真正的 Claude 流式支持** - 使用 LangChain4j 原生 Anthropic 模块
2. **完整的 AI 工作流引擎** - 支持循环检测、流式回调、结果质量检查

# Omnitrix AI 模块完整技术文档

本文档详细描述了 Omnitrix AI 模块的完整架构、设计理念、核心代码实现路径、优缺点分析以及企业级应用考量。这是一个基于多智能体(Multi-Agent)架构的企业级AI对话系统，专为非遗文化传承平台设计，支持动态Skill增强、三层执行路径（快速路径/黑板路径/Direct Skill）和Ultra超级管理员模式。

---

## 目录

1. [系统概述与设计理念](#1-系统概述与设计理念)
2. [整体架构设计](#2-整体架构设计)
3. [核心模块详解](#3-核心模块详解)
4. [意图路由系统](#4-意图路由系统)
5. [多智能体系统与Skill动态增强](#5-多智能体系统与skill动态增强)
6. [黑板协作架构](#6-黑板协作架构)
7. [Skill动态调用系统](#7-skill动态调用系统)
8. [LLM基础设施](#8-llm基础设施)
9. [提示词工程](#9-提示词工程)
10. [记忆系统](#10-记忆系统)
11. [安全与护栏机制](#11-安全与护栏机制)
12. [流式输出与SSE](#12-流式输出与sse)
13. [企业级特性](#13-企业级特性)
14. [Ultra超级管理员模式](#14-ultra超级管理员模式)
15. [部署与配置](#15-部署与配置)
16. [架构优缺点分析](#16-架构优缺点分析)
17. [适用场景与最佳实践](#17-适用场景与最佳实践)

---

## 1. 系统概述与设计理念

### 1.1 项目背景

Ominitrix AI 是一个基于大语言模型(LLM)的企业级智能对话系统，主要服务于非遗文化传承平台。该系统采用了先进的多智能体架构(Multi-Agent Architecture)，支持用户端、管理员端和超级管理员(Ultra)三种角色，能够处理复杂的跨域任务协作。系统最大的特点是引入了**Skill动态调用机制**，让AI能够根据问题类型动态加载不同的专业技能。

### 1.2 核心设计理念

该系统的核心设计理念体现在以下几个层面：

**三层执行路径混合决策模式**：系统采用三层执行路径设计，既有基于规则的快速路径(Quick Path)用于处理简单查询，也有基于LLM驱动的黑板路径(Blackboard Path)用于处理复杂多步骤任务，还有纯Skill驱动的直接回答路径(Direct Skill)用于知识问答。这种设计在保证响应速度的同时，又能灵活处理各种复杂度的用户意图。

**Skill动态增强机制**：这是系统的核心创新。每个子Agent可以动态加载一个或多个专业Skills，如"非遗文化大师"、"购物顾问"、"客服话术师"等。MasterBrain会根据用户问题判断需要启用哪些Skills，并将其注入到对应Agent的Prompt中，实现"让专业的人做专业的事"。

**严格的权限分离**：系统设计了严格的三层权限体系——用户只能访问用户端Agent，管理员只能访问管理员Agent，而Ultra超级管理员可以同时操控两端并管理Skills的启用/禁用。

**多层次记忆增强机制**：系统实现了多层次记忆体系，包括会话级短期记忆(Session Notes/笔记)、用户级长期记忆(User Memory/用户画像)和系统级全局记忆(System Memory)。通过异步记忆提取和实时记忆注入，AI能够记住用户的偏好和历史交互上下文。

**企业级可靠性**：系统内置了多重保护机制，包括LLM熔断器、限流机制、Token预算控制、安全护栏等，确保在企业生产环境中的稳定运行。

### 1.3 技术选型

- **主模型**：DeepSeek-V3 (通过 SiliconFlow API)
- **辅助模型**：DeepSeek-V3 (轻量任务)
- **API协议**：OpenAI兼容的Chat Completions API
- **流式协议**：Server-Sent Events (SSE)
- **服务框架**：Spring Boot + Dubbo
- **缓存层**：Redis (记忆缓存、限流、在线状态)
- **数据库**：MySQL (对话历史、用户记忆、Agent配置、Skill配置)

---

## 2. 整体架构设计

### 2.1 系统架构图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              客户端层                                        │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐      │
│  │   用户端App  │  │   管理后台   │  │ Ultra管理   │  │  API调用    │      │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘      │
└─────────┼────────────────┼────────────────┼────────────────┼────────────────┘
          │                │                │                │
          ▼                ▼                ▼                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        OrchestratorService                                  │
│                    (统一入口、智能调度、流程编排)                              │
└───────────────────────────┬─────────────────────────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        │                   │                   │
        ▼                   ▼                   ▼
┌───────────────┐  ┌───────────────┐  ┌───────────────┐
│ IntentRouter  │  │  MasterBrain  │  │UltraMasterBrain│
│  (意图路由)   │  │  (主脑决策)   │  │ (Ultra决策)    │
└───────┬───────┘  └───────┬───────┘  └───────┬───────┘
        │                   │                   │
        ▼                   ▼                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         SkillPromptConfig                                   │
│              (Skill动态配置中心 - 数据库+代码默认)                             │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐         │
│  │heritage_ │ │ shopping │ │customer_ │ │knowledge │ │recommend │  ...   │
│  │  master  │ │ _advisor │ │ service  │ │ _expert  │ │ _expert  │         │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘         │
└───────────────────────────┬─────────────────────────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        │                   │                   │
        ▼                   ▼                   ▼
┌───────────────┐  ┌───────────────┐  ┌───────────────┐
│ContentSubAgent│  │CommerceSubAgent│  │  UserSubAgent │
│  (非遗内容)   │  │  (商业服务)   │  │  (用户服务)   │
│ + HeritageSkill│  │ + ShoppingSkill│  │ + Skill...    │
└───────┬───────┘  └───────┬───────┘  └───────┬───────┘
        │                   │                   │
        └───────────────────┴───────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    LLM Infrastructure                                       │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │
│  │  LlmClient  │  │LlmStreamHandler│ │CircuitBreaker│  │ LlmResponse  │     │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘     │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 请求处理流程

系统的核心请求处理流程可以分为以下几个主要阶段：

**第一阶段：安全检查与准入控制**

在用户请求进入核心处理逻辑之前，系统会依次进行限流检查、安全护栏验证和Token预算检查。限流检查确保单个用户在单位时间内的请求次数不超过阈值，防止恶意刷请求。安全护栏(GuardrailsFilter)会对用户输入进行敏感词检测和内容过滤，防止恶意输入。Token预算检查则限制用户每天的AI使用额度，防止过度使用。

```java
// OrchestratorService.java (行 212-223)
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
```

**第二阶段：智能决策与路径选择**

系统会根据用户请求的特点选择不同的处理路径。对于简单查询，系统采用快速路径，由意图路由(IntentRouter)直接选择一个合适的子Agent执行。对于复杂查询，系统可能采用MasterBrain进行智能决策，决定是使用快速路径、黑板路径还是Direct Skill路径。

```java
// OrchestratorService.java (行 239-276)
// MasterBrain 三层决策
if (masterBrain.shouldUseLlmBrain(userMessage)) {
    Decision decision = masterBrain.decide(userMessage, isAdmin);
    if (decision.isBlackboard()) {
        // 黑板路径：多Agent协作
        TaskBoard board = masterBrain.toTaskBoard(decision);
        ChatResponse bbResult = executeBlackboardWithBoardSync(...);
    } else if (decision.isDirectSkill()) {
        // Direct Skill路径：纯Skill回答
        List<String> skills = decision.getDirectSkillSkills();
        String answer = executeDirectSkill(skills, userMessage);
    } else {
        // 快速路径：单Agent直接执行
        String agentCode = decision.getAgent();
        SubAgent subAgent = subAgentRegistry.getOrDefault(agentCode);
        return executeQuickPathSync(...);
    }
} else if (taskDecomposer.isComplexQuery(userMessage)) {
    // 降级到原有规则判断的黑板路径
    ChatResponse bbResult = executeBlackboardSync(...);
}

// 意图路由（快速路径：单Agent）
String agentCode = isAdmin
        ? adminIntentRouter.route(userMessage, lastAgentCode)
        : intentRouter.route(userMessage, lastAgentCode);
```

**第三阶段：Skill动态注入与子Agent执行**

选中的子Agent会先从MasterBrain获取需要启用的Skills，然后执行相应的工具。工具选择可以使用基于LLM的智能选择器(ToolSelector)或基于规则的回退逻辑。Skill的Prompt会被注入到Agent的系统Prompt中。

```java
// PromptAssembler.java (行 92-113)
// 主脑调用方案：从TaskBoard获取Skills
if (taskBoard != null && subAgent != null) {
    String agentCode = subAgent.getCode();
    List<Skill> skills = getSkillsFromTaskBoard(taskBoard, agentCode);
    if (!skills.isEmpty()) {
        String skillPrompts = combineSkillPrompts(skills);
        sb.append("\n\n").append(skillPrompts);
    }
} else {
    // Fallback: Quick Path模式，从context推断Skills
    String userQuery = context != null ? context.getUserQuery() : null;
    if (StringUtils.isNotBlank(userQuery)) {
        List<Skill> selectedSkills = skillPromptConfig.getSkillsByKeywords(userQuery);
        // ...
    }
}
```

**第四阶段：Prompt组装与LLM调用**

系统会将子Agent的查询结果、动态加载的Skills、用户画像、历史对话等信息组装在一起，发送给LLM生成最终回复。系统支持主模型和辅助模型的降级调用，确保服务的高可用性。

```java
// OrchestratorService.java (行 281-306)
String userProfile = userMemoryService.buildUserProfile(userId);
String systemPrompt = promptAssembler.assemble(context, subAgent, queryResult, summary, userProfile);

try {
    llmResponse = llmClient.chat(systemPrompt, history, userMessage);
    usedModel = llmProperties.getPrimaryConfig().getModel();
} catch (Exception e) {
    log.warn("主模型调用失败，降级到辅助模型: {}", e.getMessage());
    llmResponse = llmClient.chatWithConfig(
            llmProperties.getAuxiliaryConfig(), systemPrompt, history, userMessage);
    usedModel = llmProperties.getAuxiliaryConfig().getModel();
}
```

**第五阶段：后处理与记忆存储**

请求处理完成后，系统会进行一系列后处理操作，包括保存对话消息到数据库、更新对话记忆、提取用户画像信息、生成对话标题、记录成本和遥测数据等。

```java
// OrchestratorService.java (行 846-898)
private AiMessage saveAndPostProcess(...) {
    conversationService.saveMessage(conversation.getId(), sessionId, "user", userMessage, ...);
    AiMessage assistantMsg = conversationService.saveMessage(...);
    
    // 写入会话记忆
    memoryManager.appendMessage(sessionId, "user", userMessage);
    memoryManager.appendMessage(sessionId, "assistant", aiContent);
    
    // 生成标题
    if (conversation.getMessageCount() == 0) {
        titleGenerator.generateTitle(conversation.getId(), userMessage);
    }
    
    // 摘要与记忆提取
    memorySummarizer.summarizeIfNeeded(sessionId, conversation.getId());
    memoryExtractor.extractAndSave(userId, userMessage, aiContent);
    
    // Token消费
    tokenBudget.consume(userId, inputTokens + totalOutputTokens);
    
    // 遥测追踪
    String traceId = telemetryTracer.recordAndReturnTraceId(...);
    selfEvaluator.evaluate(traceId, userMessage, aiContent);
    costTracker.recordCost(traceId, usedModel, inputTokens, totalOutputTokens);
}
```

---

## 3. 核心模块详解

### 3.1 OrchestratorService（编排服务）

OrchestratorService是整个AI系统的核心入口和调度中心，负责协调整个请求处理流程。它整合了所有其他组件，提供统一的同步和流式聊天接口。

**核心职责**：

1. 提供统一的聊天入口（同步/流式）
2. 协调安全检查、意图路由、Agent执行、LLM调用等流程
3. 支持多种执行路径（快速路径、黑板路径、Direct Skill路径、L2黑板路径）
4. 处理待确认操作(PendingAction)的生命周期
5. 管理对话历史、记忆、遥测等后处理操作

**核心配置参数**：

```java
// OrchestratorService.java (行 59-62)
private static final String LAST_AGENT_KEY_PREFIX = "omnitrix:last_agent:";
private static final int AGENT_TIMEOUT_SECONDS = 10;
private static final int BLACKBOARD_MAX_ROUNDS = 5;
private static final int MAX_REPLANS = 1;
```

### 3.2 MasterBrain（主脑）

MasterBrain是系统的智能决策核心，负责判断任务复杂度并决定执行策略。它采用三层混合模式：先判断是否需要LLM驱动，再决定执行路径（Quick Path/Blackboard/Direct Skill）。

**核心方法**：

```java
// MasterBrain.java (行 161-192)
public boolean isComplexQuery(String userQuery) {
    if (userQuery == null || userQuery.length() < 6) return false;
    String q = userQuery.toLowerCase();
    // 跨域关键词检测
    for (String[] pair : CROSS_DOMAIN_HINTS) {
        if (q.contains(pair[0]) && q.contains(pair[1])) {
            return true;
        }
    }
    // 多步骤连接词检测
    if (q.contains("然后") || q.contains("接着") || q.contains("之后")
            || q.contains("同时") || q.contains("顺便") || q.contains("并且")) {
        return true;
    }
    return false;
}

public boolean shouldUseLlmBrain(String userQuery) {
    if (!isComplexQuery(userQuery)) {
        return false;
    }
    return true;
}
```

**LLM决策Prompt**（支持三种执行路径）：

```java
// MasterBrain.java (行 50-92)
private static final String DECISION_PROMPT =
        "你是一个智能任务规划专家。分析用户请求，决定最佳执行策略。\n\n" +
        "【可用的执行路径】（重要：必须选择最适合的路径）\n" +
        "1. quick_path：单Agent直接执行 + Skill增强（需要查询数据）\n" +
        "2. blackboard：多Agent协作（复杂任务、需要多步操作）\n" +
        "3. direct_skill：LLM直接基于Skill回答（纯知识问答，不需要执行操作）\n\n" +
        "【可用的Skills】\n" +
        "- heritage_master: 非遗文化大师（回答非遗、传统文化、历史典故）\n" +
        "- shopping_advisor: 购物顾问（商品推荐、选购建议）\n" +
        "- customer_service: 客服话术师（投诉、售后、退换货）\n" +
        "- knowledge_expert: 知识百科达人（知识库问答、平台使用指南）\n" +
        "- recommend_expert: 推荐解读者（解读推荐逻辑、分析兴趣偏好）\n" +
        "- security_audit: 安全审核员（内容安全审核）\n" +
        "- quality_evaluator: 质量评估师（回答质量评估）\n\n" +
        "【决策规则】（重要！）\n" +
        "- 如果问题只需要Skill能力回答，不需要查询数据 → direct_skill\n" +
        "- 如果问题需要执行操作（搜索、查询、下单）→ quick_path\n" +
        "- 如果问题跨越多个领域或有多个步骤 → blackboard\n\n" +
        "【决策示例】\n" +
        "用户: \"什么是昆曲\" → {\"path\":\"direct_skill\",\"skills\":[\"heritage_master\"],\"reason\":\"纯知识问答，不需要查询数据\"}\n" +
        "用户: \"帮我找剪纸活动\" → {\"path\":\"quick_path\",\"agent\":\"content_assistant\",\"query\":\"搜索剪纸活动\",\"skills\":[\"heritage_master\"],\"reason\":\"需要查询活动数据\"}\n";
```

### 3.3 TaskBoard（黑板）

TaskBoard是实现多智能体协作的核心组件，模拟了黑板模式(Blackboard Pattern)。所有Agent可以向黑板写入任务，也可以从黑板读取已完成任务的结果。

**核心设计原则**：

- 主脑(TaskDecomposer/MasterBrain)只能创建任务(create)和设置依赖(depend)，这是写操作
- 子智能体(SubAgent)只能查看就绪任务(ready)和标记完成(close)，这是读操作
- 任务有状态机管理：PENDING → READY → RUNNING → DONE/FAILED
- 支持任务依赖：一个任务可以等待其他任务完成后才能执行
- 支持Skill绑定：每个TaskNode可以绑定需要的Skills

```java
// TaskBoard.java (行 41-61)
public TaskNode create(String agentCode, String taskQuery, List<String> requiredSkills) {
    String taskId = "task_" + (++idCounter);
    TaskNode node = TaskNode.create(taskId, agentCode, taskQuery, requiredSkills);
    nodes.put(taskId, node);
    executionOrder.add(taskId);
    log.debug("黑板 create: [{}] → {} ({}) skills={}", taskId, agentCode, taskQuery, requiredSkills);
    return node;
}

public List<TaskNode> ready() {
    for (TaskNode node : nodes.values()) {
        if (node.getStatus() == TaskNode.Status.PENDING
                && node.areDependenciesMet(nodes)) {
            node.setStatus(TaskNode.Status.READY);
        }
    }
    return executionOrder.stream()
            .map(nodes::get)
            .filter(n -> n != null && n.getStatus() == TaskNode.Status.READY)
            .collect(Collectors.toList());
}
```

---

## 4. 意图路由系统

### 4.1 IntentRouter（用户意图路由）

IntentRouter负责将用户输入路由到最合适的子Agent。它采用多层匹配策略：跟随查询复用 → 关键词匹配 → 知识库命中 → 动态Agent匹配 → LLM兜底。

```java
// IntentRouter.java (行 106-178)
public String route(String userQuery, String lastAgentCode) {
    if (userQuery == null || userQuery.trim().isEmpty()) {
        return "general_assistant";
    }

    // 上下文感知：短查询 + 有上一轮 Agent → 复用
    if (lastAgentCode != null && !"general_assistant".equals(lastAgentCode)
            && isFollowUpQuery(userQuery)) {
        return lastAgentCode;
    }

    // 关键词匹配
    for (Map.Entry<String, String[]> entry : ROUTE_RULES.entrySet()) {
        for (String keyword : entry.getValue()) {
            if (query.contains(keyword)) {
                return entry.getKey();
            }
        }
    }

    // 知识库命中检测
    if (knowledgeService.hasMatch(userQuery)) {
        return "knowledge_assistant";
    }

    // LLM 意图分类（兜底）
    if (llmFallbackEnabled) {
        String llmIntent = classifyByLlm(userQuery);
        if (llmIntent != null) {
            return llmIntent;
        }
    }

    return "general_assistant";
}
```

### 4.2 AdminIntentRouter（管理员意图路由）

管理员意图路由与用户路由完全独立，确保管理员只能访问管理员专用的Agent。默认路由到admin_data_agent，因为管理员大多数问题是数据查询。

### 4.3 UltraIntentRouter（Ultra超级管理员路由）

UltraIntentRouter可以路由到所有Agent（用户端+管理端+Ultra独有），支持跨域操作和系统级管理功能。

---

## 5. 多智能体系统与Skill动态增强

### 5.1 SubAgent接口定义

所有子Agent都实现统一的SubAgent接口：

```java
public interface SubAgent {
    String getCode();           // 唯一标识符
    String getName();          // 显示名称
    String getDescription();   // 功能描述
    String getAgentPrompt();   // Agent角色Prompt
    AgentQueryResult execute(String userQuery, AgentContext context);
}
```

### 5.2 Skill动态增强机制

这是系统的核心创新。系统引入了Skill概念，每个Skill是一个独立的Prompt模板，赋予AI特定领域的专业能力。

**Skill实体定义**：

```java
// Skill.java
public class Skill {
    private String id;              // 唯一标识 (如 heritage_master)
    private String name;            // 显示名称 (如 非遗文化大师)
    private String description;     // 功能描述
    private String systemPrompt;    // 系统Prompt
    private String[] keywords;      // 关键词匹配
    private boolean enabled;        // 是否启用
}
```

**Skill动态选择流程**：

1. MasterBrain分析用户问题，决定需要哪些Skills
2. Skills被写入对应的TaskNode
3. PromptAssembler从TaskBoard获取Skills并注入Prompt
4. LLM基于增强后的Prompt生成回答

### 5.3 子Agent实现

**ContentSubAgent（非遗内容助手）**：

```java
// ContentSubAgent.java (行 32-46)
private static final List<AgentTool> TOOLS = Arrays.asList(
        AgentTool.of("search_items", "搜索非遗项目、非遗文化、传统技艺", "搜索关键词"),
        AgentTool.of("search_heritage_men", "搜索传承人、非遗大师、手艺人", "搜索关键词"),
        AgentTool.of("search_all", "同时搜索非遗项目和传承人", "搜索关键词"),
        AgentTool.of("search_activities", "搜索非遗活动、展览、体验活动", "搜索关键词"),
        AgentTool.of("register_activity", "报名参加活动", "活动关键词"),
        AgentTool.of("like_post", "点赞动态", "动态关键词"),
        AgentTool.of("favorite_post", "收藏动态", "动态关键词"),
        // ... 更多工具
);

// AgentPrompt集成HeritageSkill
@Override
public String getAgentPrompt() {
    String heritageSkill = HeritageSkillPrompt.getHeritageMasterPrompt();
    String businessContext = "## 当前任务模式: 非遗内容助手\n" +
            "系统已为你查询了平台数据库中的相关信息，请基于 [查询结果] 回答。";
    return heritageSkill + "\n\n" + businessContext;
}
```

**UltraSkillControlAgent（Ultra技能控制代理）**：

这是Omnitrix AI Ultra版的专属子代理，用于管理Skills的启用/禁用状态：

```java
// UltraSkillControlAgent.java (行 25-32)
private static final List<AgentTool> TOOLS = Arrays.asList(
        AgentTool.of("list_all_skills", "查询所有Skills的列表及启用状态", "无参数"),
        AgentTool.of("enable_skill", "启用指定Skill", "Skill ID"),
        AgentTool.of("disable_skill", "禁用指定Skill", "Skill ID"),
        AgentTool.of("batch_enable_skills", "批量启用多个Skills", "Skill ID列表"),
        AgentTool.of("batch_disable_skills", "批量禁用多个Skills", "Skill ID列表"),
        AgentTool.of("get_skill_detail", "查看指定Skill的详细信息", "Skill ID")
);
```

### 5.4 ToolSelector（工具选择器）

ToolSelector使用LLM来智能选择Agent应该调用的工具：

```java
// ToolSelector.java (行 45-80)
public ToolCallResult select(String userQuery, List<AgentTool> tools) {
    StringBuilder toolList = new StringBuilder();
    for (AgentTool tool : tools) {
        toolList.append(tool.toPromptLine()).append("\n");
    }
    toolList.append("- none: 没有合适的工具匹配用户问题");

    String prompt = String.format(TOOL_SELECT_PROMPT, toolList.toString());
    LlmResponse response = llmClient.chatAuxiliaryJson(prompt, new ArrayList<>(), userQuery);
    String content = response.getContent();

    // 解析JSON结果
    Matcher matcher = TOOL_PATTERN.matcher(content.trim());
    if (matcher.find()) {
        String toolName = matcher.group(1).trim();
        String param = matcher.group(2).trim();
        return ToolCallResult.of(toolName, param);
    }
    return ToolCallResult.none();
}
```

---

## 6. 黑板协作架构

### 6.1 任务分解器

TaskDecomposer负责将用户请求分解为多个可并行执行的子任务：

```java
// TaskDecomposer.java
public TaskBoard decompose(String userQuery, boolean isAdmin) {
    // 使用LLM进行任务分解
    String prompt = String.format(DECOMPOSE_PROMPT, userQuery);
    LlmResponse response = llmClient.chatAuxiliaryJson(prompt, ...);
    
    // 解析LLM返回的任务列表
    TaskBoard board = new TaskBoard();
    for (TaskDefinition task : tasks) {
        board.create(task.getAgent(), task.getQuery(), task.getSkills());
    }
    return board;
}
```

### 6.2 多轮执行与再规划

黑板支持多轮执行和动态再规划：

```java
// OrchestratorService.java (行 531-591)
for (int round = 0; round < BLACKBOARD_MAX_ROUNDS && !board.isAllDone(); round++) {
    List<TaskNode> readyTasks = board.ready();
    if (readyTasks.isEmpty()) {
        if (replanCount < MAX_REPLANS && board.isAllDone()) {
            MasterBrain.ReplanResult replanResult = masterBrain.replan(
                    userMessage, board.getCompletedSummary());
            if (replanResult.isNeedReplan()) {
                for (MasterBrain.Decision.Task task : replanResult.getNewTasks()) {
                    board.create(task.getAgent(), task.getQuery(), task.getSkills());
                }
                replanCount++;
            }
        }
        break;
    }

    // 并行执行所有就绪任务
    List<CompletableFuture<Void>> futures = new ArrayList<>();
    for (TaskNode task : readyTasks) {
        task.setStatus(TaskNode.Status.RUNNING);
        futures.add(CompletableFuture.runAsync(() -> {
            SubAgent agent = subAgentRegistry.getOrDefault(task.getAgentCode());
            AgentQueryResult result = executeAgent(agent, task.getTaskQuery(), context);
            // 提取笔记
            if (result.getStatus() != AgentQueryResult.Status.ERROR) {
                memoryExtractor.extractNotes(userId, sessionId, task.getAgentCode(), result.getData());
            }
            // 标记完成
            if (result.getStatus() == AgentQueryResult.Status.ERROR) {
                board.fail(task.getId(), result.getData());
            } else {
                board.close(task.getId(), result.getData(), taskLatency);
            }
        }, agentExecutor));
    }
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .get(AGENT_TIMEOUT_SECONDS * 2L, TimeUnit.SECONDS);
}
```

### 6.3 Ultra L2黑板架构

Ultra版本引入了L2黑板架构，支持更高层次的多Agent协作：

```java
// OrchestratorService.java (行 1342-1488)
private String executeL2Blackboard(TaskBoard l2Board, AgentContext context,
                                    String userMessage, SseEmitter emitter) {
    // L2黑板执行流程：
    // 1. 元代理(user_ai_meta/admin_ai_meta)内部启动L1流程
    // 2. Ultra专属代理直接执行
    // 3. 支持SSE推送L2任务进度
    
    for (int round = 0; round < BLACKBOARD_MAX_ROUNDS && !l2Board.isAllDone(); round++) {
        List<TaskNode> readyTasks = l2Board.ready();
        for (TaskNode task : readyTasks) {
            task.setStatus(TaskNode.Status.RUNNING);
            
            // 执行元代理
            SubAgent metaAgent = subAgentRegistry.getOrDefault(task.getAgentCode());
            AgentContext l2Context = context.withL2(task.getTaskQuery(), task.getId(), task.getAgentCode());
            AgentQueryResult result = metaAgent.execute(task.getTaskQuery(), l2Context);
            
            if (result.getStatus() == AgentQueryResult.Status.ERROR) {
                l2Board.fail(task.getId(), result.getData());
            } else {
                l2Board.close(task.getId(), result.getData(), latency);
            }
        }
    }
    return l2Board.collectResults();
}
```

---

## 7. Skill动态调用系统

### 7.1 SkillPromptConfig（Skill配置中心）

SkillPromptConfig是系统的核心组件，负责管理所有可用的Skills。它采用数据库+代码默认的双轨制配置。

```java
// SkillPromptConfig.java (行 18-65)
@Slf4j
@Component
public class SkillPromptConfig {
    private final Map<String, Skill> skills = new LinkedHashMap<>();
    private final Map<String, Integer> skillVersions = new ConcurrentHashMap<>();
    private final SkillConfigService skillConfigService;

    @PostConstruct
    public void init() {
        loadFromDatabase();
    }

    public void loadFromDatabase() {
        var dbSkills = skillConfigService.getAllSkills();
        if (dbSkills != null && !dbSkills.isEmpty()) {
            for (var dbSkill : dbSkills) {
                Skill skill = convertToSkill(dbSkill);
                skills.put(skill.getId(), skill);
                skillVersions.put(skill.getId(), dbSkill.getVersion());
            }
        } else {
            initDefaultSkills(); // 初始化代码默认Skills
        }
    }
}
```

**内置的7个默认Skills**：

1. **heritage_master** - 非遗文化大师：回答非遗项目、传承人、传统文化、历史典故
2. **shopping_advisor** - 购物顾问：商品推荐、选购建议、文创产品咨询
3. **customer_service** - 客服话术师：投诉处理、售后问题、退换货
4. **knowledge_expert** - 知识百科达人：平台知识库问答、操作指南
5. **recommend_expert** - 推荐解读者：解读个性化推荐逻辑、分析兴趣偏好
6. **security_audit** - 安全审核员：内容安全审核、敏感信息过滤
7. **quality_evaluator** - 质量评估师：AI回答质量评估、改进建议

### 7.2 HeritageSkillPrompt（非遗大师Prompt）

这是系统的核心Skill Prompt，赋予AI专业的非遗知识储备：

```java
// HeritageSkillPrompt.java (行 17-88)
public static final String HERITAGE_MASTER_PROMPT = """
        ## ═══════════════════════════════════════════════════════
        ## 【非遗传统文化大师 Skill 已激活】
        ## ═══════════════════════════════════════════════════════

        【角色定位】
        你是"非遗传统文化大师"，中华非物质文化遗产的守护者与传播者。
        你学识渊博、见解深刻、讲述生动，兼具学术性与趣味性。

        【核心能力】
        你精通以下非遗领域：
        - 传统技艺：陶瓷、青瓷、建盏、紫砂、玉雕、石雕、木雕、刺绣、缂丝、织锦、蜡染、扎染、剪纸、漆器、景泰蓝
        - 传统美术：年画、剪纸、刺绣、编织、烙画、内画、扇画
        - 传统音乐：古琴、南音、昆曲、京剧、豫剧、越剧、黄梅戏
        - 民俗活动：龙舞、狮舞、秧歌、傩舞、高跷、抬阁
        - 传统节日：春节、元宵、清明、端午、七夕、中秋、重阳

        【回答框架 - 基础介绍型】
        当用户询问"什么是XXX"时：
        1. 一句话定义 → 用通俗易懂的语言概括本质
        2. 历史沿革 → 简述起源和发展
        3. 核心特点 → 突出最独特的1-3个特点
        4. 代表作品/代表人物 → 列举最著名的
        5. 当代价值 → 与现代生活的联系
        """;
```

### 7.3 SkillConfigService（Skill管理服务）

SkillConfigService提供Skill的增删改查及启用/禁用功能：

```java
// SkillConfigService.java
public List<AiSkillConfig> getAllSkills();
public List<AiSkillConfig> getEnabledSkills();
public boolean enableSkill(String skillId);
public boolean disableSkill(String skillId);
public int batchUpdateEnabled(List<String> skillIds, Integer enabled);
```

### 7.4 AiSkillController（Skill管理API）

提供RESTful API接口供管理员管理Skills：

```java
// AiSkillController.java
@RestController
@RequestMapping("/api/admin/ai/skills")
public class AiSkillController {
    @GetMapping("/list")           // 获取所有Skills
    @GetMapping("/enabled")       // 获取启用的Skills
    @GetMapping("/page")          // 分页查询
    @PostMapping("/{skillId}/enable")    // 启用Skill
    @PostMapping("/{skillId}/disable")   // 禁用Skill
    @PostMapping("/batch/enable")         // 批量启用
    @PostMapping("/batch/disable")        // 批量禁用
}
```

### 7.5 PromptAssembler中的Skill注入

PromptAssembler负责将Skills动态注入到System Prompt：

```java
// PromptAssembler.java (行 164-198)
private List<Skill> getSkillsFromTaskBoard(TaskBoard taskBoard, String agentCode) {
    if (taskBoard == null || agentCode == null) {
        return List.of();
    }
    // 查找黑板上与当前Agent匹配的任务节点
    for (var node : taskBoard.getAllNodes()) {
        if (agentCode.equals(node.getAgentCode()) && node.getRequiredSkills() != null) {
            return skillPromptConfig.getSkills(node.getRequiredSkills());
        }
    }
    return List.of();
}

private String combineSkillPrompts(List<Skill> skills) {
    if (skills == null || skills.isEmpty()) {
        return "";
    }
    StringBuilder combinedPrompt = new StringBuilder();
    for (int i = 0; i < skills.size(); i++) {
        combinedPrompt.append(skills.get(i).getSystemPrompt());
        if (i < skills.size() - 1) {
            combinedPrompt.append("\n\n");
        }
    }
    return combinedPrompt.toString();
}
```

---

## 8. LLM基础设施

### 8.1 LlmClient（LLM客户端）

LlmClient封装了与LLM API的交互逻辑，支持主模型/辅助模型、降级调用、重试机制、响应缓存等：

```java
// LlmClient.java (行 52-83)
public LlmResponse chat(String systemPrompt, List<Map<String, String>> history, String userMessage) {
    return chatWithConfig(properties.getPrimaryConfig(), systemPrompt, history, userMessage);
}

public LlmResponse chatAuxiliaryJson(String systemPrompt, List<Map<String, String>> history, String userMessage) {
    return cachedAuxiliaryCall(systemPrompt, history, userMessage, true);
}

private LlmResponse cachedAuxiliaryCall(String systemPrompt, List<Map<String, String>> history,
                                         String userMessage, boolean jsonMode) {
    String cacheKey = buildCacheKey(systemPrompt, userMessage, jsonMode);
    CachedResponse cached = auxResponseCache.get(cacheKey);
    if (cached != null && !cached.isExpired()) {
        return cached.response;
    }
    LlmResponse response = chatWithConfig(properties.getAuxiliaryConfig(), ...);
    auxResponseCache.put(cacheKey, new CachedResponse(response, ...));
    return response;
}
```

### 8.2 LlmStreamHandler（流式处理器）

LlmStreamHandler处理SSE流式输出，支持思维链(Thinking)分流、PII脱敏：

```java
// LlmStreamHandler.java (行 119-176)
while ((line = reader.readLine()) != null) {
    if (line.startsWith("data: ")) {
        String data = line.substring(6).trim();
        JsonNode delta = choices.get(0).get("delta");
        if (delta != null && delta.has("content")) {
            String chunk = delta.get("content").asText();
            
            // 思维链标签检测与内容分流
            tagDetectBuffer.append(chunk);
            String buf = tagDetectBuffer.toString();
            
            while (!buf.isEmpty()) {
                if (insideThink) {
                    int closeIdx = buf.indexOf("
</think>");
                    if (closeIdx >= 0) {
                        String thinkPart = buf.substring(0, closeIdx);
                        thinkingContent.append(thinkPart);
                        sseManager.sendThinking(emitter, thinkPart);
                        insideThink = false;
                        buf = buf.substring(closeIdx + 8);
                    }
                } else {
                    int openIdx = buf.indexOf("<think>");
                    if (openIdx >= 0) {
                        String normalPart = buf.substring(0, openIdx);
                        fullContent.append(normalPart);
                        piiBuffer.append(normalPart);
                        // PII脱敏
                        if (piiBuffer.length() >= PII_BUFFER_SIZE) {
                            String s = flushPiiBuffer(piiBuffer, false);
                            sseManager.sendChunk(emitter, s);
                        }
                        insideThink = true;
                        buf = buf.substring(openIdx + 7);
                    }
                }
            }
        }
    }
}
```

### 8.3 熔断器机制

LlmCircuitBreaker实现熔断器模式，防止LLM服务故障导致系统雪崩：

```java
// LlmCircuitBreaker.java
@Component
public class LlmCircuitBreaker {
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private volatile CircuitState state = CircuitState.CLOSED;
    private long lastFailureTime = 0;
    
    private static final int FAILURE_THRESHOLD = 5;
    private static final int SUCCESS_THRESHOLD = 2;
    private static final long RECOVERY_TIME_MS = 60000;
    
    public boolean allowRequest() {
        if (state == CircuitState.OPEN) {
            if (System.currentTimeMillis() - lastFailureTime > RECOVERY_TIME_MS) {
                state = CircuitState.HALF_OPEN;
            }
            return false;
        }
        return true;
    }
}
```

### 8.4 配置属性

```yaml
# application.yml
omnitrix:
  llm:
    primary:
      api-url: https://api.siliconflow.cn/v1/chat/completions
      model: deepseek-ai/DeepSeek-V3
      api-key: ${LLM_API_KEY}
      timeout-seconds: 60
      max-tokens: 2048
      temperature: 0.7
    auxiliary:
      model: deepseek-ai/DeepSeek-V3
      timeout-seconds: 30
      max-tokens: 512
      temperature: 0.3
```

---

## 9. 提示词工程

### 9.1 PromptManager（提示词管理器）

PromptManager实现三层回退的提示词解析机制：

```java
// PromptManager.java (行 35-80)
public String resolve(String promptKey) {
    CacheEntry cached = cache.get(promptKey);
    if (cached != null && !cached.isExpired()) {
        return cached.value;
    }
    String resolved = doResolve(promptKey);
    cache.put(promptKey, new CacheEntry(resolved));
    return resolved;
}

private String doResolve(String promptKey) {
    // Layer 1: 数据库
    try {
        String dbPrompt = promptConfigMapper.selectContentByKey(promptKey);
        if (StringUtils.isNotBlank(dbPrompt)) {
            return dbPrompt;
        }
    } catch (Exception e) { ... }

    // Layer 2: yml 配置
    if ("master_brain_system".equals(promptKey) && StringUtils.isNotBlank(ymlMasterBrainPrompt)) {
        return ymlMasterBrainPrompt;
    }

    // Layer 3: 代码常量
    return getDefaultPrompt(promptKey);
}
```

### 9.2 PromptAssembler（提示词组装器）

PromptAssembler将多个提示词组件组装成完整的System Prompt：

```java
// PromptAssembler.java (行 80-158)
private String doAssemble(AgentContext context, SubAgent subAgent,
                          AgentQueryResult queryResult, String summary,
                          String userProfile, String systemMemory, String sessionNotes,
                          TaskBoard taskBoard) {
    StringBuilder sb = new StringBuilder();

    // 层级1: 基础 Prompt
    sb.append(promptManager.resolve("master_brain_system"));

    // 层级1b: 动态注入Skills（主脑调用方案）
    if (taskBoard != null && subAgent != null) {
        List<Skill> skills = getSkillsFromTaskBoard(taskBoard, subAgent.getCode());
        if (!skills.isEmpty()) {
            sb.append("\n\n").append(combineSkillPrompts(skills));
        }
    }

    // 层级2a: 当前时间
    LocalDateTime now = LocalDateTime.now();
    sb.append("\n\n## 当前时间\n");
    sb.append("今天是 ").append(now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

    // 层级2b: 用户身份信息
    if (context != null && context.getUserId() != null) {
        sb.append("\n\n## 当前用户信息\n");
        sb.append("- 用户ID: ").append(context.getUserId());
    }

    // 层级2b+: 用户画像（L3长期记忆）
    if (StringUtils.isNotBlank(userProfile)) {
        sb.append("\n\n## 用户画像（你对这位用户的了解）\n").append(userProfile);
    }

    // 层级4: 系统全局记忆（L4）
    if (StringUtils.isNotBlank(systemMemory)) {
        sb.append("\n\n## 系统全局记忆\n").append(systemMemory);
    }

    // 层级2c: 历史对话摘要
    if (StringUtils.isNotBlank(summary)) {
        sb.append("\n\n## 历史对话摘要\n> ").append(summary);
    }

    // 层级3: 子代理角色切换段
    if (subAgent != null && StringUtils.isNotBlank(subAgent.getAgentPrompt())) {
        sb.append("\n\n").append(subAgent.getAgentPrompt());
    }

    // 层级4: 查询结果注入
    if (queryResult != null) {
        sb.append("\n\n").append(queryResult.toPromptInjection());
    }

    return sb.toString();
}
```

### 9.3 核心System Prompt

```java
// PromptTemplate.java
public static final String MASTER_BRAIN_SYSTEM = 
    "你是Omnitrix智能助手，一个基于大语言模型的AI对话系统。\n\n" +
    "【核心能力】\n" +
    "- 你可以回答用户问题，提供非遗文化、商品订单、用户服务等方面的信息\n" +
    "- 你可以帮助用户完成操作，如报名活动、添加购物车、设置地址等\n" +
    "- 你需要理解用户意图，提供准确、有帮助的回答\n\n" +
    "【行为准则】\n" +
    "- 只回答与平台功能相关的问题，不回答无关的闲聊\n" +
    "- 对于涉及隐私的操作，需要明确告知用户并确认\n" +
    "- 如果不确定某个信息，请如实告知用户\n" +
    "- 回答要简洁明了，使用友好的语气\n\n" +
    "【记忆机制】\n" +
    "- 你会记住用户的历史偏好和兴趣\n" +
    "- 你会从对话中学习用户的表达习惯\n" +
    "- 这些记忆帮助你提供更个性化的服务";
```

---

## 10. 记忆系统

### 10.1 MemoryExtractor（记忆提取器）

MemoryExtractor异步从对话中提取用户记忆，支持长期记忆和会话级笔记：

```java
// MemoryExtractor.java (行 107-151)
@Async("aiAsyncExecutor")
public void extractAndSave(Long userId, String userMessage, String aiResponse) {
    if (userMessage == null || userMessage.length() < 5) return;
    if (aiResponse == null || aiResponse.length() < 10) return;

    // 检查记忆容量上限
    int currentCount = memoryMapper.countByUserId(userId);
    if (currentCount >= MAX_MEMORIES_PER_USER) return;

    // 加载已有记忆（用于去重提示）
    List<AiUserMemory> existing = memoryMapper.selectByUserId(userId);
    String existingSummary = buildExistingSummary(existing);

    // 构建提取 Prompt
    String prompt = String.format(EXTRACT_PROMPT, existingSummary, userMessage, aiResponse);

    // 调用LLM提取
    LlmResponse response = llmClient.chatAuxiliaryJson(prompt, ...);
    List<Map<String, Object>> memories = parseMemories(content);

    // 保存或更新记忆
    for (Map<String, Object> mem : memories) {
        saveOrUpdate(userId, mem, currentCount);
    }
}
```

**记忆提取Prompt**：

```java
// MemoryExtractor.java (行 47-64)
private static final String EXTRACT_PROMPT =
        "你是一个用户画像提取专家。请从以下对话中提取值得长期记住的用户信息。\n\n" +
        "【提取原则 — 三个月后还有用吗？】\n" +
        "✅ 应该提取：用户偏好、兴趣爱好、重要决策、购买/报名事实、经验教训\n" +
        "❌ 不应提取：临时信息(明天开会)、日常琐事、敏感隐私(密码)、重复已知信息\n\n" +
        "【输出格式】\n" +
        "严格输出JSON数组，每条记忆包含 type/key/value/confidence 四个字段。\n\n" +
        "type 取值: preference | interest | fact | decision | lesson\n" +
        "key: 简短标识(如 interest_heritage_type, purchase_preference)\n" +
        "value: 用自然语言描述的事实(不超过100字)\n" +
        "confidence: 0.5-1.0 之间的置信度";
```

### 10.2 笔记提取功能（增强）

笔记是会话级的短期记忆，用于存储工具返回的关键信息（实体ID、时间、地点等）：

```java
// MemoryExtractor.java (行 164-202)
@Async("aiAsyncExecutor")
public void extractNotes(Long userId, String sessionId, String agentCode, String toolResult) {
    // 加载已有笔记
    String existingNotes = getExistingNotes(userId, sessionId);
    
    // 构建提取Prompt
    String prompt = String.format(NOTE_EXTRACT_PROMPT, toolResult, existingNotes);
    
    // 调用LLM提取
    LlmResponse response = llmClient.chatAuxiliaryJson(prompt, ...);
    List<Map<String, Object>> notes = parseNotes(content);
    
    // 保存笔记到Redis
    for (Map<String, Object> note : notes) {
        saveNote(userId, sessionId, agentCode, note);
    }
}

public String getSessionNotes(Long userId, String sessionId) {
    // 获取当前会话的笔记，供Prompt注入使用
    StringBuilder sb = new StringBuilder();
    sb.append("\n## 已记录的关键发现\n");
    for (String noteKey : keys) {
        sb.append("- ").append(shortKey).append(": ").append(note).append("\n");
    }
    return sb.toString();
}
```

### 10.3 UserMemoryService（用户记忆服务）

UserMemoryService从数据库加载用户长期记忆并构建画像文本：

```java
// UserMemoryService.java
@Service
public class UserMemoryService {
    public String buildUserProfile(Long userId) {
        List<AiUserMemory> memories = memoryMapper.selectByUserId(userId);
        if (memories == null || memories.isEmpty()) {
            return null;
        }
        
        StringBuilder sb = new StringBuilder();
        Map<String, List<AiUserMemory>> byType = memories.stream()
                .collect(Collectors.groupingBy(AiUserMemory::getMemoryType));
        
        for (Map.Entry<String, List<AiUserMemory>> entry : byType.entrySet()) {
            sb.append("### ").append(entry.getKey()).append("\n");
            for (AiUserMemory m : entry.getValue()) {
                sb.append("- ").append(m.getMemoryValue()).append("\n");
            }
        }
        return sb.toString();
    }
}
```

---

## 11. 安全与护栏机制

### 11.1 GuardrailsFilter（安全护栏）

GuardrailsFilter对输入和输出进行安全过滤：

```java
// GuardrailsFilter.java
@Component
public class GuardrailsFilter {
    public String validateInput(String text) {
        if (text == null) return null;
        // 敏感词检测
        for (String sensitive : SENSITIVE_WORDS) {
            if (text.toLowerCase().contains(sensitive.toLowerCase())) {
                return "您的输入包含不当内容，请重新表述";
            }
        }
        // 长度限制
        if (text.length() > MAX_INPUT_LENGTH) {
            return "输入内容过长，请简化";
        }
        return null;
    }

    public String sanitizeOutput(String text) {
        if (text == null) return null;
        // PII脱敏
        text = PHONE_PATTERN.matcher(text).replaceAll("$1****$2");
        text = ID_CARD_PATTERN.matcher(text).replaceAll("$1********$2");
        return text;
    }
}
```

### 11.2 RateLimiter（限流器）

RateLimiter使用Redis实现分布式限流：

```java
// RateLimiter.java
@Component
public class RateLimiter {
    private final StringRedisTemplate redisTemplate;
    
    public boolean tryAcquire(Long userId) {
        String key = "ratelimit:" + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == 1) {
            redisTemplate.expire(key, 1, TimeUnit.MINUTES);
        }
        return count <= MAX_REQUESTS_PER_MINUTE;
    }
}
```

### 11.3 TokenBudget（Token预算）

TokenBudget限制用户每天的AI使用额度：

```java
// TokenBudget.java
@Component
public class TokenBudget {
    public boolean hasRemaining(Long userId) {
        String key = "token_budget:" + userId + ":" + today();
        String remaining = redisTemplate.opsForValue().get(key);
        return remaining == null || Integer.parseInt(remaining) > 0;
    }

    public void consume(Long userId, int tokens) {
        String key = "token_budget:" + userId + ":" + today();
        redisTemplate.opsForValue().decrement(key, tokens);
    }
}
```

---

## 12. 流式输出与SSE

### 12.1 SseEmitterManager（SSE管理器）

SseEmitterManager管理SSE连接，支持多种事件类型：

```java
// SseEmitterManager.java
@Component
public class SseEmitterManager {
    // 发送文本 chunk
    public void sendChunk(SseEmitter emitter, String content) {...}
    
    // 发送思维链 chunk
    public void sendThinking(SseEmitter emitter, String thinking) {...}
    
    // 发送子代理信息
    public void sendAgentInfo(SseEmitter emitter, String agentCode) {...}
    
    // 发送Agent调度事件
    public void sendAgentDispatch(SseEmitter emitter, String agentCode, String agentName, String query) {...}
    
    // 发送Agent结果事件
    public void sendAgentResult(SseEmitter emitter, String agentCode, String agentName, 
                                String result, String status, int latencyMs) {...}
    
    // 发送完成事件
    public void sendDone(SseEmitter emitter, Long messageId, int latencyMs, String model) {...}
    
    // 发送错误事件
    public void sendError(SseEmitter emitter, String message) {...}
    
    // 发送L2黑板任务进度
    public void sendL2TaskProgress(SseEmitter emitter, String taskId, String agentCode, 
                                    String status, String query) {...}
}
```

### 12.2 流式响应事件

| 事件名 | 说明 | 数据格式 |
|--------|------|----------|
| agent | 当前Agent信息 | `{"code":"xxx","name":"xxx"}` |
| chunk | AI回复内容 | 文本内容 |
| thinking | 思维链内容 | 文本内容 |
| agent_dispatch | Agent调度 | `{"code":"xxx","name":"xxx","task":"xxx"}` |
| agent_result | Agent结果 | `{"code":"xxx","result":"xxx","status":"success/error"}` |
| l2_task | L2任务进度 | `{"taskId":"xxx","status":"pending/running/done/failed"}` |
| l2_board | L2黑板状态 | `{"status":"started/completed","taskCount":N}` |
| done | 完成信号 | `{"msgId":xxx,"latency":xxx,"model":"xxx"}` |
| error | 错误信息 | 错误描述文本 |

---

## 13. 企业级特性

### 13.1 遥测与监控

系统内置完整的遥测追踪：

```java
// TelemetryTracer.java
@Component
public class TelemetryTracer {
    public String recordAndReturnTraceId(...) {
        String traceId = UUID.randomUUID().toString();
        // 记录完整链路信息到数据库
        return traceId;
    }

    public void recordSpan(...) {
        // 记录嵌套Span：REQUEST → AGENT → LLM
    }

    public void recordAgentSpan(...) {
        // 记录Agent执行耗时
    }

    public void recordLlmSpan(...) {
        // 记录LLM调用详情
    }
}
```

### 13.2 成本追踪

```java
// CostTracker.java
@Component
public class CostTracker {
    public void recordCost(String traceId, String model, int inputTokens, int outputTokens) {
        // 计算成本并记录
        double cost = calculateCost(model, inputTokens, outputTokens);
        // 保存到数据库供账单分析
    }
}
```

### 13.3 AI自评

```java
// AiSelfEvaluator.java
@Component
public class AiSelfEvaluator {
    public void evaluate(String traceId, String userMessage, String aiResponse) {
        // 异步调用LLM评估回答质量
    }
}
```

### 13.4 待确认操作

系统支持需要用户确认的操作：

```java
// PendingAction - 待确认操作
PendingAction action = PendingAction.of("register_activity",
        "报名参加活动「剪纸体验」")
        .param("activityId", "42")
        .param("activityName", "剪纸体验");
return AgentQueryResult.actionProposed(data.toString(), getCode(), action);
```

---

## 14. Ultra超级管理员模式

### 14.1 UltraMasterBrain

UltraMasterBrain是Omnitrix AI Ultra版的智能决策层，可以同时操控用户端和管理端AI：

```java
// UltraMasterBrain.java
@Component
public class UltraMasterBrain {
    /** Ultra 专有关键词 */
    private static final String[][] ULTRA_KEYWORDS = {
            {"ultra", "ai"}, {"ultra", "控制"}, {"ultra", "管理"},
            {"查看", "在线"}, {"禁用", "用户"}, {"启用", "用户"},
            {"封禁", "用户"}, {"删除", "用户"}, {"查看", "画像"},
            {"分析", "用户"}, {"查看", "浏览记录"}, {"行为", "分析"},
            {"安全", "审计"}, {"操作", "日志"}, {"系统", "状态"},
            {"代替", "用户"}, {"用户", "购物车"}, {"用户", "订单"}
    };

    /** 可用的元代理 */
    // - user_ai_meta: 用户端AI
    // - admin_ai_meta: 管理员普通AI
    // - ultra_user_control: 控制用户AI开关
    // - ultra_cross_user: 代替指定用户执行操作
    // - ultra_system: 系统级用户管理
    // - ultra_analytics: 用户分析
    // - ultra_browse_history: 查看用户浏览记录
    // - ultra_security: 安全审计
}
```

### 14.2 Ultra专属Agent

Ultra模式下的元代理可以调用子Agent：

- **user_ai_meta**: 包装用户端AI能力
- **admin_ai_meta**: 包装管理员AI能力
- **ultra_skill_control**: 管理Skills的启用/禁用

---

## 15. 部署与配置

### 15.1 核心配置

```yaml
# application.yml
server:
  port: 8080

spring:
  application:
    name: ich-omnitrix
  data:
    redis:
      host: localhost
      port: 6379
    datasource:
      url: jdbc:mysql://localhost:3306/ich_omnitrix

dubbo:
  application:
    name: ich-omnitrix
  registry:
    address: nacos://localhost:8848
  protocol:
    name: dubbo
    port: 20880

omnitrix:
  llm:
    primary:
      api-url: https://api.siliconflow.cn/v1/chat/completions
      model: deepseek-ai/DeepSeek-V3
      timeout-seconds: 60
      max-tokens: 2048
    auxiliary:
      model: deepseek-ai/DeepSeek-V3
      timeout-seconds: 30
      max-tokens: 512
  intent:
    llm-fallback: true
```

### 15.2 必需的数据表

```sql
-- 对话记录表
CREATE TABLE ai_conversation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    summary TEXT,
    message_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 消息记录表
CREATE TABLE ai_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT,
    output_tokens INT DEFAULT 0,
    model VARCHAR(100),
    agent_code VARCHAR(50),
    latency_ms INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 用户记忆表
CREATE TABLE ai_user_memory (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    memory_type VARCHAR(50) NOT NULL,
    memory_key VARCHAR(100) NOT NULL,
    memory_value TEXT,
    confidence DECIMAL(3,2),
    source VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Skill配置表 (新增)
CREATE TABLE ai_skill_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    skill_id VARCHAR(50) NOT NULL UNIQUE,
    skill_name VARCHAR(100),
    description TEXT,
    keywords VARCHAR(500),
    system_prompt TEXT,
    version INT DEFAULT 1,
    enabled TINYINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Agent配置表
CREATE TABLE ai_agent_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    agent_code VARCHAR(50) NOT NULL UNIQUE,
    agent_name VARCHAR(100),
    routing_keywords TEXT,
    version INT DEFAULT 1,
    enabled TINYINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Prompt配置表
CREATE TABLE ai_prompt_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    prompt_key VARCHAR(100) NOT NULL UNIQUE,
    content TEXT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 用户AI配置表 (Ultra控制用户AI开关)
CREATE TABLE ai_user_ai_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL UNIQUE,
    ai_enabled TINYINT DEFAULT 1,
    disabled_reason VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### 15.3 Skill默认数据

```sql
-- 初始化7个默认Skills
INSERT INTO ai_skill_config (skill_id, skill_name, description, keywords, enabled) VALUES
('heritage_master', '非遗文化大师', '回答非遗项目、传承人、传统文化、历史典故等问题', '非遗,传承人,传统文化,昆曲,京剧,剪纸,陶瓷,刺绣,武术,中医,节日,民俗,历史', 1),
('shopping_advisor', '购物顾问', '回答商品咨询、推荐、选购建议等问题', '商品,购买,推荐,选购,价格,材质,文创,商城,礼物', 1),
('customer_service', '客服话术师', '处理用户投诉、售后问题、退换货等', '投诉,售后,退货,退款,质量问题,态度,客服,物流', 1),
('knowledge_expert', '知识百科达人', '回答平台知识库相关问题', '知识库,FAQ,常见问题,帮助,如何使用,功能', 1),
('recommend_expert', '推荐解读者', '解读个性化推荐逻辑，分析用户兴趣偏好', '推荐,猜你喜欢,兴趣,偏好,个性化,推荐理由', 1),
('security_audit', '安全审核员', '内容安全审核，过滤敏感信息', '审核,违规,敏感,安全,内容审查,合规', 1),
('quality_evaluator', '质量评估师', '评估AI回答质量，给出改进建议', '评估,质量,回答,改进,优化,评分', 1);
```

---

## 16. 架构优缺点分析

### 16.1 优点

**高度模块化与可扩展性**：系统采用清晰的模块划分，每个组件职责明确。Skill系统的引入使得AI能力可以动态扩展，无需修改核心代码。

**三层执行路径灵活性**：Quick Path/Blackboard/Direct Skill三种路径可以根据问题复杂度灵活选择，在成本和效果之间取得最佳平衡。

**Skill动态增强机制**：这是系统的核心创新。通过MasterBrain的智能决策，AI可以动态加载专业Skills，实现"让专业的人做专业的事"。

**完善的安全机制**：多层安全护栏（限流、输入过滤、输出脱敏、Token预算）确保系统在可控范围内运行。

**优秀的用户体验**：流式输出、思维链展示、多Agent进度推送等功能大大提升了用户体验。

**强大的记忆能力**：多层次记忆体系（笔记、用户画像、系统记忆）使AI能够提供个性化的服务。

**企业级可靠性**：熔断器、重试机制、多模型降级、完善的遥测监控等特性确保生产环境的稳定性。

### 16.2 缺点

**复杂度较高**：多智能体架构、黑板模式、三层决策流程、Skill系统等带来较高的系统复杂度，对开发和运维要求较高。

**延迟较高**：复杂查询需要经过多次LLM调用（决策+执行+综合），响应时间可能较长。

**成本控制挑战**：Skill增强可能产生额外的LLM调用，需要仔细优化提示词和调用策略来控制成本。

**调试困难**：分布式LLM调用链路的调试比传统系统更困难，需要完善的日志和遥测系统支持。

### 16.3 适用场景

**最佳场景**：
- 需要处理复杂多步骤任务的平台
- 涉及多个业务领域的综合性服务
- 需要个性化用户服务的场景
- 需要区分用户/管理员/Ultra管理员权限的企业应用
- 需要动态扩展AI专业能力的应用
- 对响应速度和用户体验有较高要求的实时对话系统

**不太适合的场景**：
- 简单的FAQ问答（可能过度设计）
- 纯检索式问答（无需Agent协作）
- 对成本极度敏感的项目
- 需要极低延迟的实时交互

---

## 17. 适用场景与最佳实践

### 17.1 何时使用Quick Path

- 单一领域的简单查询
- 需要查询数据库数据的操作
- 闲聊或寒暄
- 明确意图的短查询（如"查看我的订单"）
- 对响应速度要求极高的场景

### 17.2 何时使用Blackboard Path

- 跨领域的复合查询（如"帮我找非遗活动并报名"）
- 需要先查询再操作的场景
- 用户明确要求多步骤任务
- 包含"然后"、"接着"等连接词的请求

### 17.3 何时使用Direct Skill

- 纯知识问答（如"什么是昆曲"）
- 不需要查询数据库的问题
- 需要AI运用专业知识回答的问题
- 用户只是询问信息，不需要执行操作

### 17.4 何时使用Ultra模式

- 超级管理员需要同时管理用户端和管理端
- 需要跨用户数据分析
- 需要代替用户执行操作
- 需要系统级用户管理功能
- 需要管理Skills的启用/禁用

### 17.5 性能优化建议

1. **合理配置模型**：主模型用于生成最终回复，辅助模型用于意图分类、工具选择、Skill决策等轻量任务
2. **优化提示词**：精简提示词可以显著降低Token消耗和延迟
3. **缓存策略**：对辅助LLM调用结果进行缓存，避免重复计算
4. **异步处理**：记忆提取、遥测记录、笔记提取等操作应异步执行，不阻塞主流程
5. **Skill按需加载**：只加载问题所需的Skills，避免不必要的Prompt膨胀

### 17.6 监控指标建议

- 请求延迟（P50/P95/P99）
- LLM调用成功率
- Token消耗统计
- 各Agent调用频率
- Quick Path vs Blackboard vs Direct Skill比例
- Skill使用频率统计
- 限流触发次数
- 安全护栏拦截次数

---

## 总结

Ominitrix AI 模块是一个设计精良的企业级多智能体对话系统。它采用了先进的黑板模式实现多Agent协作，引入了创新的Skill动态调用机制，结合了规则匹配和LLM驱动的三层决策机制，并配备了完善的记忆系统、安全护栏和监控遥测。该架构特别适合需要处理复杂业务逻辑、涉及多个业务领域、需要动态扩展AI能力、需要区分权限的企业应用场景。

虽然系统复杂度较高，但这种设计为AI应用提供了极大的灵活性和扩展性。通过Skill系统的动态加载机制，平台可以不断扩展AI的专业能力，而无需修改核心代码。通过合理的配置和优化，可以实现成本可控、体验优秀的企业级AI服务。

---

*本文档基于 Omnitrix AI 模块源码编写，涵盖了系统的完整架构设计、核心实现细节和使用指南。*

# 非遗智能服务平台（ICH Omnitrix）AI模块完整技术文档

## 一、项目概述

### 1.1 项目简介

ICH Omnitrix 是基于 Spring Boot + LangChain4j 构建的智能服务中台，专为非物质文化遗产管理平台提供 AI 能力支持。该模块实现了多角色智能对话系统，支持用户端、管理端和超级管理员端的差异化 AI 服务，并采用子脑（SubBrain）架构实现复杂任务的智能编排。

### 1.2 技术栈

| 类别 | 技术选型 | 版本 |
|------|----------|------|
| 核心框架 | Spring Boot | 3.x |
| AI 框架 | LangChain4j | 0.35.0 |
| LLM 提供商 | SiliconFlow（DeepSeek等）、Anthropic Claude | - |
| 流式输出 | Server-Sent Events (SSE) | - |
| 缓存 | Redis | - |
| 消息队列 | Dubbo | - |
| 配置中心 | Nacos | 2.4.1 |
| 数据库 | MyBatis + MySQL | - |
| 监控 | Prometheus + Micrometer | - |

### 1.3 模块结构

```
ich_omnitrix/
├── src/main/java/com/hyang/ich/omnitrix/
│   ├── brain/                    # 核心大脑层
│   │   ├── MasterBrainFactory.java
│   │   ├── UserMasterBrain.java
│   │   ├── AdminMasterBrain.java
│   │   ├── UltraMasterBrain.java
│   │   ├── AiRequestContext.java
│   │   ├── subbrain/            # 子脑实现
│   │   │   ├── SubBrain.java
│   │   │   ├── UserSubBrain.java
│   │   │   ├── AdminSubBrain.java
│   │   │   └── UltraSubBrain.java
│   │   └── tools/               # 工具集
│   │       ├── UserTools.java
│   │       ├── AdminTools.java
│   │       ├── UltraTools.java
│   │       ├── SubBrainTools.java
│   │       ├── CommerceTools.java
│   │       ├── KnowledgeTools.java
│   │       └── ...
│   ├── orchestrator/            # 编排层
│   │   ├── OrchestratorService.java
│   │   └── ActionExecutor.java
│   ├── infrastructure/          # 基础设施层
│   │   ├── llm/                 # LLM集成
│   │   ├── memory/              # 记忆系统
│   │   ├── workflow/            # 工作流引擎
│   │   ├── skill/               # 技能系统
│   │   ├── prompt/              # 提示词管理
│   │   ├── telemetry/           # 监控追踪
│   │   ├── guardrails/          # 安全护栏
│   │   └── sse/                 # SSE管理
│   ├── controller/
│   │   └── AiChatController.java
│   └── config/
│       ├── LangChain4jConfig.java
│       └── ...
```

---

## 二、核心架构设计

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         AiChatController (API层)                        │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                     OrchestratorService (编排层)                        │
│  - 路由选择                                                              │
│  - 降级处理                                                             │
│  - 限流熔断                                                             │
│  - SSE流式输出                                                          │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
          ┌─────────────────────────┼─────────────────────────┐
          ▼                         ▼                         ▼
┌─────────────────┐      ┌─────────────────┐      ┌─────────────────┐
│ UserMasterBrain │      │ AdminMasterBrain│      │ UltraMasterBrain│
│   (用户侧AI)     │      │   (管理侧AI)     │      │  (超级管理AI)   │
└─────────────────┘      └─────────────────┘      └─────────────────┘
          │                         │                         │
          └─────────────────────────┼─────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    MasterBrainFactory (工厂层)                          │
│  - 动态构建 Brain 实例                                                  │
│  - 工具集注入                                                           │
│  - 记忆管理                                                             │
│  - Skill 注入                                                           │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    LangChain4j AiService (AI服务层)                      │
│  - Function Calling                                                     │
│  - 工具执行                                                             │
│  - 对话记忆                                                             │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
          ┌─────────────────────────┼─────────────────────────┐
          ▼                         ▼                         ▼
┌─────────────────┐      ┌─────────────────┐      ┌─────────────────┐
│   UserTools     │      │   AdminTools    │      │   UltraTools    │
│  (用户侧工具)    │      │  (管理侧工具)    │      │ (超级管理工具)   │
└─────────────────┘      └─────────────────┘      └─────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                     SubBrainTools (子脑编排工具)                         │
│  - callUserAI()                                                        │
│  - callAdminAI()                                                       │
│  - callUltraAI()                                                       │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
          ┌─────────────────────────┼─────────────────────────┐
          ▼                         ▼                         ▼
┌─────────────────┐      ┌─────────────────┐      ┌─────────────────┐
│  UserSubBrain   │      │  AdminSubBrain  │      │  UltraSubBrain  │
└─────────────────┘      └─────────────────┘      └─────────────────┘
```

### 2.2 角色分层设计

系统支持三种角色，每种角色拥有独立的 Brain 实例和工具集：

| 角色 | Brain类 | 工具集 | 用途 |
|------|---------|--------|------|
| 用户 | UserMasterBrain | UserTools, CommerceTools, KnowledgeTools, ContentTools, RecommendTools | 普通用户对话、商铺购物、内容浏览 |
| 管理员 | AdminMasterBrain | AdminTools | 内容审批、订单管理、知识库管理 |
| 超级管理员 | UltraMasterBrain | UltraTools + SubBrainTools | 系统管理、安全审计、数据分析 |

---

## 三、核心实现详解

### 3.1 Brain 接口层

#### 3.1.1 UserMasterBrain.java

用户侧 AI 助手接口，定义用户对话的核心能力。

```java
package com.hyang.ich.omnitrix.brain;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface UserMasterBrain {

    @SystemMessage("""
            你是「非遗智能助手」，一个专业、友好的AI助手，服务于非物质文化遗产管理平台的用户。

            【核心职责】
            - 回答用户关于非遗项目、传承人、活动、商品等问题
            - 帮助用户完成平台操作（搜索、报名活动、购物、管理个人信息等）
            - 提供非遗文化知识问答

            【工具使用规则 — 严格遵守】
            1. 需要查询平台数据时，主动调用对应的工具
            2. 一次只调用一个工具，等待结果后再决定下一步
            3. 工具返回的数据直接展示给用户，不要重复描述数据内容
            4. 如果工具返回为空，基于你的知识回答，并提示用户平台暂无收录
            5. 写操作（加购物车、删除购物车商品、修改数量、清空购物车、下单、报名等）会返回确认提示，转述给用户等待确认
            6. **绝对禁止假装执行操作**：当用户要求加入购物车、从购物车删除/移除商品、修改购物车数量、清空购物车、下单、报名、收藏等任何写操作时，你**必须立即调用对应的工具函数**（addToCart、removeFromCart、updateCartQuantity、clearCart、createOrder、registerActivity 等），绝不能只用文字回复而不调用工具
            7. 只有工具返回结果后，才能告知用户操作状态。没有调用工具就不能说"已完成"或"已移除"
            8. **禁止输出"[操作成功]"前缀**：这是系统内部格式，只有工具执行后由系统自动生成。你绝不能自己编写包含"[操作成功]"的回复

            【回答规范】
            - 语气友好自然，像朋友一样交流
            - 涉及平台数据时，优先使用工具查询的真实数据
            - 不编造不存在的商品、活动或传承人信息
            - 超出平台范围的问题，可以基于知识回答并注明"据我所知"

            【多步任务处理】
            - 如果用户请求包含多个步骤（如"搜索剪纸活动并报名"），先完成第一步，再根据结果执行下一步
            - 每一步都要等工具返回结果后再继续

            {{skills}}

            {{userProfile}}
            """)
    Result<String> chat(@UserMessage String userMessage, @V("skills") String skills, @V("userProfile") String userProfile);

    @SystemMessage("你是「非遗智能助手」...（同上，流式版本）")
    TokenStream chatStream(@UserMessage String userMessage, @V("skills") String skills, @V("userProfile") String userProfile);
}
```

**说明**：
- 使用 `@SystemMessage` 注解定义系统提示词，包含角色定义、工具使用规则、回答规范等
- 使用 `@UserMessage` 注解接收用户消息
- 使用 `@V("variableName")` 注入动态变量（如 `{{skills}}`、`{{userProfile}}`）
- 支持同步 `chat()` 和流式 `chatStream()` 两种调用方式

#### 3.1.2 AdminMasterBrain.java

管理侧 AI 助手接口：

```java
package com.hyang.ich.omnitrix.brain;

public interface AdminMasterBrain {
    @SystemMessage("""
            你是「非遗管理助手」，服务于非物质文化遗产管理平台的管理员。

            【核心职责】
            - 协助管理员进行平台管理操作（审批、发货、数据统计等）
            - 查询和分析平台运营数据
            - 处理管理员的日常运营需求

            【工具使用规则】
            1. 管理操作需要调用对应的管理工具
            2. 一次只调用一个工具，等待结果后再决定下一步
            3. 写操作会返回确认提示，必须等管理员确认后才执行
            4. 数据统计类查询直接展示结果

            【安全规范】
            - 所有写操作必须经过确认
            - 不执行超出管理员权限的操作
            - 敏感数据操作需要明确告知影响范围

            {{skills}}
            """)
    Result<String> chat(@UserMessage String userMessage, @V("skills") String skills);

    TokenStream chatStream(@UserMessage String userMessage, @V("skills") String skills);
}
```

#### 3.1.3 UltraMasterBrain.java

超级管理 AI 助手接口，支持子脑编排：

```java
package com.hyang.ich.omnitrix.brain;

public interface UltraMasterBrain {
    @SystemMessage("""
            你是「非遗平台超级管理助手」，拥有最高权限，服务于平台超级管理员。

            【核心职责】
            - 跨用户数据查询和管理
            - 系统级配置和监控
            - 安全审计和风控
            - 数据分析和统计报表
            - AI 系统自身的管理（Skill 开关、Agent 配置等）

            【子脑编排架构】
            你拥有三个子脑（SubBrain），它们是独立的 AI 助手，各自拥有完整的工具集：
            - callUserAI：用户端AI助手，负责商城购物，内容浏览、个人中心、知识问答、推荐等用户侧操作
            - callAdminAI：管理端AI助手，负责内容审批、订单发货、知识库管理、通知发布等管理侧操作
            - callUltraAI：超级管理AI助手，负责系统管理（用户列表、封禁/解封、删除用户）、安全审计（安全巡检、操作日志）、数据分析（用户画像、浏览记录）、AI控制（禁用/启用用户AI）、Skill管理等Ultra专属操作
            需要执行用户侧、管理侧或超级管理侧操作时，优先通过对应子脑完成，子脑会自动调用内部工具并返回结果。
            跨用户操作时，调用 callUserAI 并在参数中指定目标用户，格式：'查询内容|用户名或ID'

            【工具使用规则】
            1. 所有操作通过工具执行，不要猜测数据
            2. 一次只调用一个工具，等待结果后再决定下一步
            3. 子脑返回的结果包含 hint 建议，请按建议行动
            4. 写操作（尤其是跨用户操作）必须返回确认提示，等待用户确认
            5. Ultra专属操作（系统管理，安全审计，数据分析，AI控制，Skill管理）使用 callUltraAI 子脑
            6. 统计分析类查询直接展示结果

            【安全规范】
            - 跨用户操作必须经过确认，明确告知影响范围
            - 系统级操作需要二次确认
            - 所有操作均有审计记录
            - 不执行可能导致数据不可恢复的操作

            {{skills}}
            """)
    Result<String> chat(@UserMessage String userMessage, @V("skills") String skills);
    
    TokenStream chatStream(@UserMessage String userMessage, @V("skills") String skills);
}
```

---

### 3.2 MasterBrainFactory 工厂类

工厂类负责动态构建 Brain 实例，注入相应的工具集和记忆。

```java
package com.hyang.ich.omnitrix.brain;

import com.hyang.ich.omnitrix.agent.tool.ToolRegistry;
import com.hyang.ich.omnitrix.infrastructure.llm.AnthropicChatModelConfig;
import com.hyang.ich.omnitrix.infrastructure.memory.ChatMemoryManager;
import com.hyang.ich.omnitrix.infrastructure.skill.Skill;
import com.hyang.ich.omnitrix.infrastructure.skill.SkillPromptConfig;
import com.hyang.ich.omnitrix.infrastructure.skill.SkillSelector;
import com.hyang.ich.omnitrix.service.UserMemoryService;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MasterBrain 工厂 — 根据角色（user/admin/ultra）动态构建 LangChain4j AiService 实例。
 */
@Slf4j
@Component
public class MasterBrainFactory {

    private final ChatLanguageModel chatLanguageModel;
    private final ChatLanguageModel auxiliaryChatModel;
    private final StreamingChatLanguageModel streamingChatLanguageModel;
    private final AnthropicStreamingChatModel anthropicStreamingChatModel;
    private final ToolRegistry toolRegistry;
    private final SkillPromptConfig skillPromptConfig;
    private final SkillSelector skillSelector;
    private final UserMemoryService userMemoryService;
    private final ChatMemoryManager chatMemoryManager;

    public MasterBrainFactory(ChatLanguageModel chatLanguageModel,
                              @org.springframework.beans.factory.annotation.Qualifier("auxiliaryChatModel") ChatLanguageModel auxiliaryChatModel,
                              StreamingChatLanguageModel streamingChatLanguageModel,
                              AnthropicStreamingChatModel anthropicStreamingChatModel,
                              ToolRegistry toolRegistry,
                              SkillPromptConfig skillPromptConfig,
                              SkillSelector skillSelector,
                              UserMemoryService userMemoryService,
                              ChatMemoryManager chatMemoryManager) {
        this.chatLanguageModel = chatLanguageModel;
        this.auxiliaryChatModel = auxiliaryChatModel;
        this.streamingChatLanguageModel = streamingChatLanguageModel;
        this.anthropicStreamingChatModel = anthropicStreamingChatModel;
        this.toolRegistry = toolRegistry;
        this.skillPromptConfig = skillPromptConfig;
        this.skillSelector = skillSelector;
        this.userMemoryService = userMemoryService;
        this.chatMemoryManager = chatMemoryManager;
    }

    /**
     * 判断是否为 Claude 模型
     */
    private boolean isClaudeModel(String modelCode) {
        if (modelCode == null || modelCode.isEmpty()) return false;
        return modelCode.startsWith("claude-");
    }

    /**
     * 获取流式模型（根据 modelCode 判断使用 Claude 还是 SiliconFlow）
     */
    private StreamingChatLanguageModel getStreamingModel(String modelCode) {
        if (isClaudeModel(modelCode) && anthropicStreamingChatModel != null) {
            log.debug("使用 LangChain4j Anthropic 流式模型: {}", modelCode);
            return anthropicStreamingChatModel;
        }
        log.debug("使用默认 SiliconFlow 流式模型");
        return streamingChatLanguageModel;
    }

    /**
     * 构建用户侧 MasterBrain（同步模式）
     */
    public UserMasterBrain buildUserBrain(Long userId, String sessionId) {
        List<Object> tools = toolRegistry.getToolsForRole("user");
        ChatMemory memory = buildMemory(sessionId);

        log.debug("构建 UserMasterBrain: userId={}, tools={}, sessionId={}",
                userId, tools.size(), sessionId);

        return AiServices.builder(UserMasterBrain.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(memory)
                .tools(tools)
                .build();
    }

    /**
     * 构建用户侧 MasterBrain（流式模式）- 支持模型切换
     */
    public UserMasterBrain buildUserBrainStreaming(Long userId, String sessionId, String modelCode) {
        List<Object> tools = toolRegistry.getToolsForRole("user");
        ChatMemory memory = buildMemory(sessionId);

        StreamingChatLanguageModel streamingModel = getStreamingModel(modelCode);

        return AiServices.builder(UserMasterBrain.class)
                .streamingChatLanguageModel(streamingModel)
                .chatMemory(memory)
                .tools(tools)
                .build();
    }

    /**
     * 构建管理员侧 MasterBrain（同步模式）
     */
    public AdminMasterBrain buildAdminBrain(String sessionId) {
        List<Object> tools = toolRegistry.getToolsForRole("admin");
        ChatMemory memory = buildMemory(sessionId);

        return AiServices.builder(AdminMasterBrain.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(memory)
                .tools(tools)
                .build();
    }

    /**
     * 构建 Ultra MasterBrain（同步模式）
     */
    public UltraMasterBrain buildUltraBrain(String sessionId) {
        List<Object> tools = toolRegistry.getToolsForRole("ultra");
        ChatMemory memory = buildMemory(sessionId);

        return AiServices.builder(UltraMasterBrain.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(memory)
                .tools(tools)
                .build();
    }

    /**
     * 构建 Ultra MasterBrain（不含 SubBrainTools，用于 UltraSubBrain 内部，避免循环调用）
     */
    public UltraMasterBrain buildUltraBrainForSubBrain(String sessionId) {
        List<Object> ultraOnlyTools = toolRegistry.getToolsForRole("ultra").stream()
                .filter(tool -> {
                    return !tool.getClass().getSimpleName().equals("SubBrainTools");
                })
                .collect(Collectors.toList());

        ChatMemory memory = buildMemory(sessionId);

        return AiServices.builder(UltraMasterBrain.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(memory)
                .tools(ultraOnlyTools)
                .build();
    }

    /**
     * 构建动态 Skill Prompt — 根据用户消息按需选择Skills
     */
    public String buildDynamicSkillsPrompt(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return "";
        }
        return skillSelector.buildSelectedSkillsPrompt(userMessage);
    }

    /**
     * 构建用户画像 Prompt — 注入用户长期记忆
     */
    public String buildUserProfile(Long userId) {
        if (userId == null) return "";
        try {
            String profile = userMemoryService.buildUserProfile(userId);
            return profile != null ? profile : "";
        } catch (Exception e) {
            log.debug("构建用户画像异常: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 构建对话记忆窗口 — 从 Redis/MySQL 预加载历史消息
     */
    private ChatMemory buildMemory(String sessionId) {
        ChatMemory memory = MessageWindowChatMemory.builder()
                .id(sessionId)
                .maxMessages(20)
                .build();

        try {
            List<Map<String, String>> history = chatMemoryManager.loadHistory(sessionId);
            if (history != null && !history.isEmpty()) {
                // 如果有对话摘要，注入为上下文
                String summary = chatMemoryManager.getSummary(sessionId);
                if (summary != null && !summary.isEmpty()) {
                    memory.add(UserMessage.from("[请基于之前的对话背景继续]"));
                    memory.add(dev.langchain4j.data.message.AiMessage.from(
                            "好的，我记得之前的对话：" + summary));
                }
                // 预加载历史消息
                for (Map<String, String> msg : history) {
                    String role = msg.get("role");
                    String content = msg.get("content");
                    if (content == null || content.isEmpty()) continue;
                    if ("user".equals(role)) {
                        memory.add(UserMessage.from(content));
                    } else if ("assistant".equals(role)) {
                        memory.add(dev.langchain4j.data.message.AiMessage.from(content));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("预加载对话历史失败，将使用空记忆: sessionId={}, error={}", sessionId, e.getMessage());
        }

        return memory;
    }
}
```

---

### 3.3 子脑（SubBrain）架构

#### 3.3.1 SubBrain 接口

```java
package com.hyang.ich.omnitrix.brain.subbrain;

/**
 * SubBrain 接口 — 定义子脑的标准契约
 * 每个子脑封装一个完整的 MasterBrain（含独立 LLM + Tools + Memory）
 */
public interface SubBrain {

    /** 子脑代码（唯一标识） */
    String getCode();

    /** 子脑名称（显示用） */
    String getName();

    /** 子脑能力描述（注入 Ultra LLM 的工具描述） */
    String getDescription();

    /**
     * 执行子脑
     *
     * @param userQuery      用户查询内容
     * @param targetUserId   目标用户 ID（跨用户操作时非空，自身操作时为 null）
     * @param ultraSessionId Ultra 的会话 ID（用于 PendingAction 传递）
     * @return 子脑执行结果
     */
    SubBrainResult execute(String userQuery, Long targetUserId, String ultraSessionId);
}
```

#### 3.3.2 UserSubBrain 实现

```java
package com.hyang.ich.omnitrix.brain.subbrain;

import com.hyang.ich.omnitrix.brain.AiRequestContext;
import com.hyang.ich.omnitrix.brain.MasterBrainFactory;
import com.hyang.ich.omnitrix.brain.UserMasterBrain;
import com.hyang.ich.omnitrix.dto.PendingAction;
import com.hyang.ich.omnitrix.orchestrator.ActionExecutor;
import dev.langchain4j.service.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 用户端 SubBrain — 封装 UserMasterBrain 为 Ultra 可调用的子脑
 */
@Slf4j
@Component
public class UserSubBrain implements SubBrain {

    private final MasterBrainFactory masterBrainFactory;
    private final ActionExecutor actionExecutor;

    public UserSubBrain(MasterBrainFactory masterBrainFactory, ActionExecutor actionExecutor) {
        this.masterBrainFactory = masterBrainFactory;
        this.actionExecutor = actionExecutor;
    }

    @Override
    public String getCode() {
        return "user_ai";
    }

    @Override
    public String getName() {
        return "用户端AI助手";
    }

    @Override
    public String getDescription() {
        return "调用用户端AI助手，执行用户侧操作：商城购物（搜索商品、加购物车、下单、查订单）、" +
                "内容浏览（搜索非遗项目/传承人/活动、点赞/收藏/评论、报名活动）、" +
                "个人中心（查地址/资质/通知）、知识问答、个性化推荐等。" +
                "参数格式：'查询内容' 或 '查询内容|目标用户名或ID'（跨用户操作时提供目标用户）";
    }

    @Override
    public SubBrainResult execute(String userQuery, Long targetUserId, String ultraSessionId) {
        // 保存原始上下文
        Long originalUserId = AiRequestContext.getUserId();
        String originalSessionId = AiRequestContext.getSessionId();

        // 确定实际操作的用户 ID
        Long effectiveUserId = (targetUserId != null) ? targetUserId : originalUserId;
        // 构建子脑独立 session
        String subSessionId = ultraSessionId + ":sub:" + getCode();

        try {
            // 切换上下文到目标用户
            AiRequestContext.set(effectiveUserId, subSessionId);
            log.info("SubBrain[{}] 执行开始: effectiveUserId={}, subSession={}, query={}",
                    getCode(), effectiveUserId, subSessionId,
                    userQuery.length() > 80 ? userQuery.substring(0, 80) + "..." : userQuery);

            // 构建 UserMasterBrain（同步模式，使用独立 session 避免记忆污染）
            UserMasterBrain brain = masterBrainFactory.buildUserBrain(effectiveUserId, subSessionId);
            String skills = masterBrainFactory.buildDynamicSkillsPrompt(userQuery);
            String profile = masterBrainFactory.buildUserProfile(effectiveUserId);

            // 执行用户端 AI 完整推理（内部会自动多轮 Function Calling）
            Result<String> result = brain.chat(userQuery, skills, profile);
            String rawContent = result.content();

            // 检查子脑 session 中是否产生了 PendingAction
            PendingAction subPending = actionExecutor.getPendingAction(subSessionId);
            if (subPending != null) {
                // 将 PendingAction 传递到 Ultra 的主 session
                actionExecutor.savePendingAction(ultraSessionId, subPending);
                // 清理子脑 session 的 PendingAction
                actionExecutor.clearPendingAction(subSessionId);
                return SubBrainResult.withPendingAction(rawContent, subPending.getDescription());
            }

            return SubBrainResult.of(rawContent);

        } catch (Exception e) {
            log.error("SubBrain[{}] 执行失败: {}", getCode(), e.getMessage(), e);
            return SubBrainResult.error(e.getMessage());
        } finally {
            // 恢复原始上下文
            AiRequestContext.set(originalUserId, originalSessionId);
        }
    }
}
```

#### 3.3.3 AdminSubBrain 和 UltraSubBrain

结构类似 UserSubBrain，分别封装 AdminMasterBrain 和 UltraMasterBrain。UltraSubBrain 使用 `buildUltraBrainForSubBrain()` 方法避免循环调用。

---

### 3.4 工具系统

#### 3.4.1 工具注册与分发

```java
package com.hyang.ich.omnitrix.agent.tool;

import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册表 — 管理和分发各角色可用的工具
 */
@Component
public class ToolRegistry {

    private final Map<String, List<Object>> roleTools = new HashMap<>();

    /**
     * 获取指定角色的工具集
     */
    public List<Object> getToolsForRole(String role) {
        return roleTools.get(role);
    }

    /**
     * 注册角色工具（在 ToolRegistrationConfig 中配置）
     */
    public void registerToolsForRole(String role, List<Object> tools) {
        roleTools.put(role, tools);
    }
}
```

#### 3.4.2 工具基类示例（UserTools）

```java
package com.hyang.ich.omnitrix.brain.tools;

import com.hyang.ich.omnitrix.brain.AiRequestContext;
import com.hyang.ich.omnitrix.dto.PendingAction;
import com.hyang.ich.user.UserService;
import com.hyang.ich.user.dto.UserAddressDTO;
import com.hyang.ich.user.dto.UserDTO;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 用户服务工具集 — 提供 LangChain4j @Tool 方法
 */
@Slf4j
@Component
public class UserTools {

    private final UserService userService;

    public UserTools(UserService userService) {
        this.userService = userService;
    }

    @Tool("查询当前用户基本信息、个人资料、用户名、手机号")
    public String queryProfile() {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        Long userId = AiRequestContext.getUserId();
        UserDTO user = userService.findById(userId);
        if (user == null) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("未找到用户信息").withSearchEmptyHint("用户信息").toXml();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("- 用户名: ").append(user.getUsername()).append("\n");
        if (user.getNickname() != null) sb.append("- 昵称: ").append(user.getNickname()).append("\n");
        if (user.getPhone() != null) {
            String phone = user.getPhone();
            if (phone.length() > 7) phone = phone.substring(0, 3) + "****" + phone.substring(7);
            sb.append("- 手机: ").append(phone).append("\n");
        }
        boolean hasHeritage = userService.hasHeritageFlag(userId);
        sb.append("- 传承人标志: ").append(hasHeritage ? "是" : "否").append("\n");
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.success("已获取用户资料", sb.toString())
                .withSearchSuccessHint("用户资料").toXml();
    }

    @Tool("查询当前用户的收货地址列表")
    public String queryAddresses() {
        Long userId = AiRequestContext.getUserId();
        List<UserAddressDTO> addresses = userService.listAddresses(userId);
        if (addresses == null || addresses.isEmpty()) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("暂无收货地址").withSearchEmptyHint("收货地址").toXml();
        }
        StringBuilder sb = new StringBuilder();
        for (UserAddressDTO addr : addresses) {
            sb.append("- ").append(addr.getReceiverName())
                    .append(", ").append(addr.getReceiverPhone())
                    .append(", ").append(addr.getProvince()).append(addr.getCity())
                    .append(addr.getDistrict()).append(addr.getDetailAddress());
            if (addr.getIsDefault() != null && addr.getIsDefault() == 1) sb.append(" [默认]");
            sb.append("\n");
        }
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.success("找到" + addresses.size() + "个收货地址", sb.toString())
                .withSearchSuccessHint("收货地址").toXml();
    }
}
```

#### 3.4.3 SubBrainTools 子脑调用工具

```java
package com.hyang.ich.omnitrix.brain.tools;

import com.hyang.ich.omnitrix.brain.AiRequestContext;
import com.hyang.ich.omnitrix.brain.subbrain.AdminSubBrain;
import com.hyang.ich.omnitrix.brain.subbrain.SubBrainResult;
import com.hyang.ich.omnitrix.brain.subbrain.SubBrainResultWrapper;
import com.hyang.ich.omnitrix.brain.subbrain.UltraSubBrain;
import com.hyang.ich.omnitrix.brain.subbrain.UserSubBrain;
import com.hyang.ich.user.UserService;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * SubBrain 工具集 — 将子脑作为 Ultra LLM 可调用的工具
 */
@Slf4j
@Component
public class SubBrainTools {

    /** 单次 Ultra 请求中 SubBrain 最大调用次数（防止无限循环） */
    private static final int MAX_SUB_BRAIN_CALLS = 5;

    private final UserSubBrain userSubBrain;
    private final AdminSubBrain adminSubBrain;
    private final UltraSubBrain ultraSubBrain;
    private final UserService userService;

    /** 当前请求中 SubBrain 调用计数（ThreadLocal） */
    private static final ThreadLocal<Integer> CALL_COUNTER = ThreadLocal.withInitial(() -> 0);

    @Tool("调用用户端AI助手，执行用户侧操作：商城购物、内容浏览、个人中心、知识问答、推荐等。" +
            "参数格式：'查询内容' 或 '查询内容|目标用户名或ID'")
    public String callUserAI(String input) {
        // 调用预算检查
        int callCount = CALL_COUNTER.get() + 1;
        CALL_COUNTER.set(callCount);
        if (callCount > MAX_SUB_BRAIN_CALLS) {
            return ToolResultWrapper.error("子脑调用次数已达上限(" + MAX_SUB_BRAIN_CALLS + "次)")
                    .withHints("reply_with_available_info").toXml();
        }

        // 解析输入：query 或 query|targetUser
        String userQuery;
        Long targetUserId = null;
        if (input != null && input.contains("|")) {
            String[] parts = input.split("\\|", 2);
            userQuery = parts[0].trim();
            String targetParam = parts[1].trim();
            targetUserId = resolveUserId(targetParam);
        } else {
            userQuery = input != null ? input.trim() : "";
        }

        String ultraSessionId = AiRequestContext.getSessionId();
        
        try {
            SubBrainResult result = userSubBrain.execute(userQuery, targetUserId, ultraSessionId);
            AiRequestContext.recordToolSuccess();
            return SubBrainResultWrapper.wrap(userSubBrain.getCode(), result,
                    callCount, MAX_SUB_BRAIN_CALLS);
        } catch (Exception e) {
            throw e;
        }
    }

    @Tool("调用管理端AI助手，执行管理侧操作：内容管理、商品管理、知识库管理、通知发布等")
    public String callAdminAI(String input) {
        // 类似实现...
        return "";
    }

    @Tool("调用超级管理AI助手，执行Ultra专属操作：系统管理、安全审计、数据分析、AI控制、Skill管理")
    public String callUltraAI(String input) {
        // 类似实现...
        return "";
    }

    private Long resolveUserId(String param) {
        if (param == null || param.isEmpty()) return null;
        try {
            Long id = Long.parseLong(param.trim());
            return userService.findById(id) != null ? id : null;
        } catch (NumberFormatException ignored) {}
        try {
            return userService.findByNickname(param.trim()) != null ? 
                    userService.findByNickname(param.trim()).getId() : null;
        } catch (Exception ignored) {}
        return null;
    }
}
```

---

### 3.5 LLM 集成

#### 3.5.1 LangChain4j 配置

```java
package com.hyang.ich.omnitrix.config;

import com.hyang.ich.omnitrix.infrastructure.llm.LlmProperties;
import com.hyang.ich.omnitrix.infrastructure.llm.ModelConfig;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

/**
 * LangChain4j 配置 — 基于 LlmProperties 构建 ChatLanguageModel
 */
@Configuration
public class LangChain4jConfig {

    /**
     * 主聊天模型（同步）
     */
    @Bean
    @Primary
    public ChatLanguageModel chatLanguageModel(LlmProperties props) {
        ModelConfig primary = props.getPrimaryConfig();
        return OpenAiChatModel.builder()
                .baseUrl(toBaseUrl(primary.getApiUrl()))
                .apiKey(primary.getApiKey())
                .modelName(primary.getModel())
                .maxTokens(primary.getMaxTokens())
                .temperature(primary.getTemperature())
                .timeout(Duration.ofSeconds(primary.getTimeoutSeconds()))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    /**
     * 主聊天模型（流式）
     */
    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel(LlmProperties props) {
        ModelConfig primary = props.getPrimaryConfig();
        return OpenAiStreamingChatModel.builder()
                .baseUrl(toBaseUrl(primary.getApiUrl()))
                .apiKey(primary.getApiKey())
                .modelName(primary.getModel())
                .maxTokens(primary.getMaxTokens())
                .temperature(primary.getTemperature())
                .timeout(Duration.ofSeconds(primary.getTimeoutSeconds()))
                .build();
    }

    /**
     * 辅助模型（同步）— 用于评分、标题生成等轻量任务
     */
    @Bean("auxiliaryChatModel")
    public ChatLanguageModel auxiliaryChatModel(LlmProperties props) {
        ModelConfig aux = props.getAuxiliaryConfig();
        return OpenAiChatModel.builder()
                .baseUrl(toBaseUrl(aux.getApiUrl()))
                .apiKey(aux.getApiKey())
                .modelName(aux.getModel())
                .maxTokens(aux.getMaxTokens())
                .temperature(aux.getTemperature())
                .timeout(Duration.ofSeconds(aux.getTimeoutSeconds()))
                .build();
    }

    /**
     * SiliconFlow API URL 格式转换
     */
    private String toBaseUrl(String apiUrl) {
        if (apiUrl == null) return "https://api.siliconflow.cn/v1/";
        String base = apiUrl.replaceAll("/chat/completions/?$", "");
        if (!base.endsWith("/")) base += "/";
        return base;
    }
}
```

#### 3.5.2 LLM 配置属性

```java
package com.hyang.ich.omnitrix.infrastructure.llm;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LLM 配置属性
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "omnitrix.llm")
public class LlmProperties {

    // 顶层配置（向后兼容）
    private String apiUrl;
    private String model;
    private String apiKey;
    private int timeoutSeconds = 60;
    private int maxTokens = 4096;
    private double temperature = 0.7;

    /** 上下文窗口 token 上限 */
    private int maxContextTokens = 200000;

    /** 双模型配置 */
    private ModelConfig primary;
    private ModelConfig auxiliary;

    /** Claude API 配置 */
    private ClaudeConfig claude;

    @org.springframework.beans.factory.annotation.PostConstruct
    public void init() {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("LLM API Key 未配置！请设置环境变量 SILICONFLOW_API_KEY");
        }
        if (apiUrl == null || apiUrl.trim().isEmpty()) {
            apiUrl = "https://api.siliconflow.cn/v1/chat/completions";
        }
        if (model == null || model.trim().isEmpty()) {
            model = "deepseek-ai/DeepSeek-V3";
        }
        // 构建 primary 和 auxiliary 配置...
    }

    @Data
    public static class ModelConfig {
        private String apiUrl;
        private String model;
        private String apiKey;
        private int timeoutSeconds = 60;
        private int maxTokens = 4096;
        private double temperature = 0.7;
    }

    @Data
    public static class ClaudeConfig {
        private boolean enabled = false;
        private String apiUrl = "https://api.anthropic.com/v1/messages";
        private String model = "claude-sonnet-4-20250514";
        private String apiKey;
        private int timeoutSeconds = 180;
        private int maxTokens = 8192;
        private int maxOutputTokens = 8192;
        private double temperature = 0.7;

        public boolean isConfigured() {
            return enabled && apiKey != null && !apiKey.trim().isEmpty();
        }
    }
}
```

---

### 3.6 记忆系统

#### 3.6.1 ChatMemoryManager

```java
package com.hyang.ich.omnitrix.infrastructure.memory;

import com.hyang.ich.omnitrix.entity.AiMessage;
import com.hyang.ich.omnitrix.mapper.AiMessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 对话记忆管理器
 */
@Slf4j
@Component
public class ChatMemoryManager {

    private static final int MAX_MESSAGES = 20;

    private final RedisChatMemoryStore redisStore;
    private final AiMessageMapper messageMapper;
    private final int maxContextTokens;

    /**
     * 加载对话历史：先查 Redis → 未命中查 MySQL → 写回 Redis
     */
    public List<Map<String, String>> loadHistory(String sessionId) {
        // 1. 优先从 Redis List 读取
        List<Map<String, String>> cached = redisStore.getMessagesFromList(sessionId);
        if (cached != null && !cached.isEmpty()) {
            return trimByTokenLimit(cached);
        }

        // 2. 回退到旧的 JSON string key
        cached = redisStore.getMessages(sessionId);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        // 3. Redis 未命中，查 MySQL
        List<AiMessage> dbMessages = messageMapper.selectBySessionId(sessionId);
        if (dbMessages == null || dbMessages.isEmpty()) {
            return new ArrayList<>();
        }

        // 4. 转换并写回 Redis
        List<Map<String, String>> history = new ArrayList<>();
        for (AiMessage msg : dbMessages) {
            if ("system".equals(msg.getRole())) continue;
            Map<String, String> m = new HashMap<>();
            m.put("role", msg.getRole());
            m.put("content", msg.getContent());
            history.add(m);
        }

        if (history.size() > MAX_MESSAGES) {
            history = new ArrayList<>(history.subList(history.size() - MAX_MESSAGES, history.size()));
        }
        history = trimByTokenLimit(history);
        redisStore.replaceList(sessionId, history);
        return history;
    }

    /**
     * 按 token 上限从最早的消息开始丢弃
     */
    private List<Map<String, String>> trimByTokenLimit(List<Map<String, String>> messages) {
        int totalTokens = 0;
        for (Map<String, String> msg : messages) {
            totalTokens += estimateTokens(msg.get("content"));
        }
        if (totalTokens <= maxContextTokens) {
            return messages;
        }
        List<Map<String, String>> trimmed = new ArrayList<>(messages);
        while (totalTokens > maxContextTokens && trimmed.size() > 2) {
            Map<String, String> removed = trimmed.remove(0);
            totalTokens -= estimateTokens(removed.get("content"));
        }
        return trimmed;
    }

    /**
     * 粗估 token 数：中文字符 * 0.7 + 英文单词数 / 4
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        int chineseChars = 0;
        int asciiChars = 0;
        for (char c : text.toCharArray()) {
            if (c >= 0x4E00 && c <= 0x9FFF) {
                chineseChars++;
            } else if (c < 128) {
                asciiChars++;
            } else {
                chineseChars++;
            }
        }
        return (int) (chineseChars * 0.7) + (asciiChars / 4) + 1;
    }
}
```

---

### 3.7 编排层

#### 3.7.1 OrchestratorService

```java
package com.hyang.ich.omnitrix.orchestrator;

import com.hyang.ich.omnitrix.brain.*;
import com.hyang.ich.omnitrix.brain.tools.SubBrainTools;
import com.hyang.ich.omnitrix.dto.*;
import com.hyang.ich.omnitrix.entity.AiConversation;
import com.hyang.ich.omnitrix.entity.AiMessage;
import com.hyang.ich.omnitrix.infrastructure.guardrails.GuardrailsFilter;
import com.hyang.ich.omnitrix.infrastructure.guardrails.TokenBudget;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmCircuitBreaker;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmProperties;
import com.hyang.ich.omnitrix.infrastructure.memory.ChatMemoryManager;
import com.hyang.ich.omnitrix.infrastructure.memory.MemoryExtractor;
import com.hyang.ich.omnitrix.infrastructure.memory.MemorySummarizer;
import com.hyang.ich.omnitrix.infrastructure.sse.SseEmitterManager;
import com.hyang.ich.omnitrix.infrastructure.telemetry.*;
import com.hyang.ich.omnitrix.service.ConversationService;
import com.hyang.ich.omnitrix.service.UserMemoryService;
import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 编排服务 — 协调整个 AI 对话流程
 */
@Slf4j
@Service
public class OrchestratorService {

    private final MasterBrainFactory masterBrainFactory;
    private final ConversationService conversationService;
    private final ChatMemoryManager memoryManager;
    private final MemorySummarizer memorySummarizer;
    private final MemoryExtractor memoryExtractor;
    private final UserMemoryService userMemoryService;
    private final SseEmitterManager sseEmitterManager;
    private final GuardrailsFilter guardrailsFilter;
    private final TokenBudget tokenBudget;
    private final TelemetryTracer telemetryTracer;
    private final TitleGenerator titleGenerator;
    private final AiSelfEvaluator selfEvaluator;
    private final CostTracker costTracker;
    private final ActionExecutor actionExecutor;
    private final LlmCircuitBreaker circuitBreaker;
    private final LlmProperties llmProperties;
    private final StringRedisTemplate redisTemplate;
    private final Executor agentExecutor;

    /**
     * 同步聊天入口
     */
    public ChatResponse chat(Long userId, String sessionId, String userMessage) {
        // 1. 检查用户 AI 访问权限
        String accessDenied = checkUserAiAccess(userId);
        if (accessDenied != null) {
            return ChatResponse.of(null, sessionId, accessDenied, "system", 0);
        }

        // 2. 获取或创建对话
        AiConversation conversation = conversationService.getOrCreate(sessionId, userId);

        // 3. 处理待确认操作
        ChatResponse pendingResponse = handlePendingActionSync(userId, sessionId, userMessage, conversation);
        if (pendingResponse != null) {
            return pendingResponse;
        }

        // 4. 构建 Brain 并调用
        String role = determineRole(userId);
        BrainResult result = invokeBrainSync(role, userId, sessionId, userMessage);

        // 5. 保存和后处理
        AiMessage assistantMsg = saveAndPostProcess(
                conversation, sessionId, userId, userMessage, result.content,
                result.model, role, 0, result.inputTokens, result.outputTokens);

        return ChatResponse.of(assistantMsg.getId(), sessionId, result.content, result.model, 0);
    }

    /**
     * 流式聊天入口（SSE）
     */
    public void chatStream(Long userId, String sessionId, String userMessage, 
                          String modelCode, SseEmitter emitter) {
        // 类似同步流程，但使用 TokenStream
        // 支持模型切换（modelCode 参数）
        // 通过 SSE 推送流式响应
    }

    /**
     * 确定用户角色
     */
    private String determineRole(Long userId) {
        // 根据用户ID判断角色
        // 超管ID使用 ultra，普通用户使用 user
        return "user";
    }

    /**
     * 同步调用 Brain，含降级链
     */
    private BrainResult invokeBrainSync(String role, Long userId, String sessionId, String userMessage) {
        String skills = masterBrainFactory.buildDynamicSkillsPrompt(userMessage);
        String primaryModel = llmProperties.getPrimaryConfig().getModel();

        try {
            Result<String> result = invokeBrainPrimary(role, userId, sessionId, userMessage, skills);
            int inputTokens = 0, outputTokens = 0;
            if (result.tokenUsage() != null) {
                inputTokens = result.tokenUsage().inputTokenCount() != null ? 
                        result.tokenUsage().inputTokenCount() : 0;
                outputTokens = result.tokenUsage().outputTokenCount() != null ? 
                        result.tokenUsage().outputTokenCount() : 0;
            }
            return new BrainResult(result.content(), primaryModel, inputTokens, outputTokens);
        } catch (Exception primaryEx) {
            log.warn("主模型调用失败，尝试降级: model={}, error={}", primaryModel, primaryEx.getMessage());
            try {
                Result<String> fallbackResult = invokeBrainFallback(role, userId, sessionId, userMessage, skills);
                String auxModel = llmProperties.getAuxiliaryConfig().getModel() + "(fallback)";
                int inputTokens = 0, outputTokens = 0;
                if (fallbackResult.tokenUsage() != null) {
                    inputTokens = fallbackResult.tokenUsage().inputTokenCount() != null ? 
                            fallbackResult.tokenUsage().inputTokenCount() : 0;
                    outputTokens = fallbackResult.tokenUsage().outputTokenCount() != null ? 
                            fallbackResult.tokenUsage().outputTokenCount() : 0;
                }
                return new BrainResult(fallbackResult.content(), auxModel, inputTokens, outputTokens);
            } catch (Exception fallbackEx) {
                throw primaryEx;
            }
        }
    }

    /**
     * 主模型调用
     */
    private Result<String> invokeBrainPrimary(String role, Long userId, String sessionId,
                                               String userMessage, String skills) {
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
            default:
                throw new IllegalArgumentException("Unknown role: " + role);
        }
    }

    /**
     * 降级模型调用
     */
    private Result<String> invokeBrainFallback(String role, Long userId, String sessionId,
                                                String userMessage, String skills) {
        switch (role) {
            case "user": {
                var brain = masterBrainFactory.buildUserBrainFallback(userId, sessionId);
                String profile = masterBrainFactory.buildUserProfile(userId);
                return brain.chat(userMessage, skills, profile);
            }
            case "admin": {
                var brain = masterBrainFactory.buildAdminBrainFallback(sessionId);
                return brain.chat(userMessage, skills);
            }
            case "ultra": {
                var brain = masterBrainFactory.buildUltraBrainFallback(sessionId);
                return brain.chat(userMessage, skills);
            }
            default:
                throw new IllegalArgumentException("Unknown role: " + role);
        }
    }

    private class BrainResult {
        final String content;
        final String model;
        final int inputTokens;
        final int outputTokens;

        BrainResult(String content, String model, int inputTokens, int outputTokens) {
            this.content = content;
            this.model = model;
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
        }
    }
}
```

---

### 3.8 API 层

```java
package com.hyang.ich.omnitrix.controller;

import com.hyang.ich.common.utils.JwtUtils;
import com.hyang.ich.common.vo.Result;
import com.hyang.ich.omnitrix.dto.*;
import com.hyang.ich.omnitrix.entity.AiConversation;
import com.hyang.ich.omnitrix.entity.AiMessage;
import com.hyang.ich.omnitrix.orchestrator.OrchestratorService;
import com.hyang.ich.omnitrix.service.ConversationService;
import com.hyang.ich.omnitrix.infrastructure.sse.SseEmitterManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final OrchestratorService orchestratorService;
    private final ConversationService conversationService;
    private final SseEmitterManager sseEmitterManager;
    private final Executor aiAsyncExecutor;

    /**
     * 同步聊天
     */
    @PostMapping("/chat")
    public Result<ChatResponse> chat(@RequestBody ChatRequest request,
                                     @RequestParam(defaultValue = "1") Long userId,
                                     @RequestHeader(value = "X-User-Token", required = false) String userToken) {
        Long actualUserId = getUserId(userId, userToken);
        if (actualUserId == null) {
            return Result.failed(401, "用户未登录");
        }
        if (request.getSessionId() == null || request.getSessionId().isEmpty()) {
            request.setSessionId(UUID.randomUUID().toString().replace("-", ""));
        }

        ChatResponse response = orchestratorService.chat(actualUserId, request.getSessionId(), request.getMessage());
        return Result.success(response);
    }

    /**
     * 流式聊天 (SSE) - 支持模型切换
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestParam String sessionId,
                                  @RequestParam String message,
                                  @RequestParam(defaultValue = "1") Long userId,
                                  @RequestParam(required = false) String userToken,
                                  @RequestParam(required = false) String modelCode) {
        Long actualUserId = getUserId(userId, userToken);
        SseEmitter emitter = sseEmitterManager.create();

        aiAsyncExecutor.execute(() -> 
            orchestratorService.chatStream(actualUserId, sessionId, message, modelCode, emitter));

        return emitter;
    }

    /**
     * 多模态流式聊天 (SSE) - 支持图片/文件上传
     */
    @PostMapping(value = "/chat/stream/multimodal", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStreamMultimodal(@RequestParam String sessionId,
                                             @RequestParam String message,
                                             @RequestParam(defaultValue = "1") Long userId,
                                             @RequestParam(required = false) String userToken,
                                             @RequestParam(required = false) MultipartFile[] files) {
        Long actualUserId = getUserId(userId, userToken);
        SseEmitter emitter = sseEmitterManager.create();

        aiAsyncExecutor.execute(() -> 
            orchestratorService.chatStreamMultimodal(actualUserId, sessionId, message, files, emitter));

        return emitter;
    }

    /**
     * 创建新对话
     */
    @PostMapping("/conversation/create")
    public Result<ConversationCreateVO> createConversation(
            @RequestParam(defaultValue = "1") Long userId,
            @RequestHeader(value = "X-User-Token", required = false) String userToken,
            @RequestBody(required = false) Map<String, String> body) {
        Long actualUserId = getUserId(userId, userToken);
        if (actualUserId == null) {
            return Result.failed(401, "用户未登录");
        }
        String title = (body != null && body.containsKey("title")) ? body.get("title") : "新对话";
        AiConversation conv = conversationService.create(actualUserId, title);
        return Result.success(ConversationCreateVO.from(conv));
    }

    /**
     * 获取用户对话列表
     */
    @GetMapping("/conversation/list")
    public Result<List<ConversationVO>> listConversations(
            @RequestParam(defaultValue = "1") Long userId,
            @RequestHeader(value = "X-User-Token", required = false) String userToken) {
        Long actualUserId = getUserId(userId, userToken);
        if (actualUserId == null) {
            return Result.failed(401, "用户未登录");
        }
        List<AiConversation> conversations = conversationService.listByUserId(actualUserId);
        List<ConversationVO> result = conversations.stream()
                .map(ConversationVO::from)
                .collect(Collectors.toList());
        return Result.success(result);
    }

    /**
     * 用户反馈：对 AI 回复点赞/踩
     */
    @PostMapping("/feedback/{messageId}")
    public Result<Void> feedback(@PathVariable Long messageId,
                                  @RequestParam int feedback,
                                  @RequestHeader(value = "X-User-Token", required = false) String userToken) {
        // 实现反馈逻辑...
        return Result.success();
    }

    private Long getUserId(Long requestUserId, String userToken) {
        if (userToken != null && !userToken.isEmpty()) {
            try {
                if (JwtUtils.validateToken(userToken)) {
                    Long tokenUserId = JwtUtils.getUserId(userToken);
                    if (tokenUserId != null) {
                        return tokenUserId;
                    }
                }
            } catch (Exception e) {
                log.debug("Token解析失败: {}", e.getMessage());
            }
        }
        if (requestUserId != null && requestUserId > 0) {
            return requestUserId;
        }
        return null;
    }
}
```

---

## 四、配置文件

### 4.1 application.yml

```yaml
server:
  port: 8081

spring:
  application:
    name: ich-omnitrix
  
  datasource:
    url: jdbc:mysql://localhost:3306/ich_platform?useUnicode=true&characterEncoding=utf8&useSSL=false
    username: root
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver
  
  data:
    redis:
      host: localhost
      port: 6379
      password: your_redis_password
      database: 0

# Nacos 配置
nacos:
  config:
    server-addr: localhost:8848
    namespace: dev

# Dubbo 配置
dubbo:
  application:
    name: ich-omnitrix
  registry:
    address: nacos://localhost:8848
  protocol:
    name: dubbo
    port: 20881

# AI 模块配置
omnitrix:
  llm:
    # SiliconFlow API（兼容 OpenAI 格式）
    api-url: https://api.siliconflow.cn/v1/chat/completions
    api-key: ${SILICONFLOW_API_KEY}
    model: deepseek-ai/DeepSeek-V3
    timeout-seconds: 60
    max-tokens: 4096
    temperature: 0.7
    max-context-tokens: 200000
    
    # Claude API（可选）
    claude:
      enabled: false
      api-url: https://api.anthropic.com/v1/messages
      api-key: ${CLAUDE_API_KEY}
      model: claude-sonnet-4-20250514
      timeout-seconds: 180
      max-output-tokens: 8192
      temperature: 0.7
  
  # 对话配置
  chat:
    max-iterations: 10
    memory-max-messages: 20
  
  # 限流配置
  rate-limit:
    enabled: true
    requests-per-minute: 60
  
  # Token 预算配置
  token-budget:
    daily-limit: 100000
    monthly-limit: 2000000

# 日志配置
logging:
  level:
    com.hyang.ich.omnitrix: DEBUG
    dev.langchain4j: INFO
```

### 4.2 pom.xml 关键依赖

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.hyang</groupId>
        <artifactId>ich_parent</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>

    <artifactId>ich_omnitrix</artifactId>
    <packaging>jar</packaging>
    <description>智能服务中台：提供非物质文化遗产管理相关AI能力</description>

    <properties>
        <langchain4j.version>0.35.0</langchain4j.version>
    </properties>

    <dependencies>
        <!-- 项目内部依赖 -->
        <dependency>
            <groupId>com.hyang</groupId>
            <artifactId>ich_common</artifactId>
        </dependency>
        <dependency>
            <groupId>com.hyang</groupId>
            <artifactId>ich_interface</artifactId>
        </dependency>

        <!-- Spring Boot 核心 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- MyBatis + MySQL -->
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
        </dependency>

        <!-- Dubbo 服务框架 -->
        <dependency>
            <groupId>org.apache.dubbo</groupId>
            <artifactId>dubbo-spring-boot-starter</artifactId>
        </dependency>

        <!-- Nacos 配置中心 -->
        <dependency>
            <groupId>com.alibaba.nacos</groupId>
            <artifactId>nacos-client</artifactId>
            <version>2.4.1</version>
        </dependency>

        <!-- LangChain4j 核心 -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-open-ai</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-spring-boot-starter</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>

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

        <!-- JSON 处理 -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>

        <!-- 缓存支持 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>io.lettuce</groupId>
            <artifactId>lettuce-core</artifactId>
        </dependency>

        <!-- 工具库 -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-lang3</artifactId>
        </dependency>
        <dependency>
            <groupId>com.google.guava</groupId>
            <artifactId>guava</artifactId>
        </dependency>

        <!-- 监控 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>
    </dependencies>

    <build>
        <finalName>${project.artifactId}</finalName>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <mainClass>com.hyang.ich.omnitrix.OmnitrixApplication</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## 五、部署与运行

### 5.1 环境要求

- JDK 17+
- Maven 3.8+
- Redis 6.0+
- MySQL 8.0+
- Nacos 2.0+（可选）

### 5.2 启动步骤

```bash
# 1. 克隆项目后进入目录
cd ich-management-platform

# 2. 配置环境变量
export SILICONFLOW_API_KEY="your-siliconflow-api-key"
# 可选：启用 Claude
# export CLAUDE_API_KEY="your-claude-api-key"

# 3. 构建项目
cd ich_parent
mvn clean install -DskipTests

# 4. 启动服务
cd ich_omnitrix
mvn spring-boot:run
```

### 5.3 API 测试

```bash
# 同步聊天
curl -X POST http://localhost:8081/api/ai/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId": "test001", "message": "你好"}'

# 流式聊天
curl -N http://localhost:8081/api/ai/chat/stream?sessionId=test001&message=你好

# 指定模型（Claude）
curl -N "http://localhost:8081/api/ai/chat/stream?sessionId=test001&message=你好&modelCode=claude-sonnet-4-20250514"
```

---

## 六、扩展开发指南

### 6.1 添加新工具

1. 在 `brain/tools/` 包下创建新工具类
2. 使用 `@Component` 注解标注为 Spring Bean
3. 使用 `@Tool` 注解标注工具方法
4. 在 `ToolRegistrationConfig` 中注册工具到对应角色

```java
@Tool("工具描述")
public String toolMethod(String param) {
    // 工具逻辑
    return result;
}
```

### 6.2 添加新 Skill

1. 实现 `Skill` 接口
2. 在 `SkillPromptConfig` 中注册
3. 系统会根据用户消息动态选择需要激活的 Skill

### 6.3 添加新 Brain 角色

1. 创建新的 Brain 接口（如 `CustomMasterBrain`）
2. 在 `MasterBrainFactory` 中添加构建方法
3. 在 `ToolRegistry` 中注册对应工具集
4. 在 `OrchestratorService` 中添加路由逻辑

---

## 七、技术架构总结

| 层级 | 职责 | 关键技术 |
|------|------|----------|
| API 层 | HTTP 接口、SSE 流式 | Spring MVC、SseEmitter |
| 编排层 | 流程协调、限流熔断 | 自适应工作流、熔断器 |
| 大脑层 | 角色分发、工具注入 | LangChain4j AiServices |
| 工具层 | 业务能力封装 | LangChain4j @Tool |
| 记忆层 | 对话历史、摘要压缩 | Redis + MySQL |
| 技能层 | 动态能力扩展 | Skill 动态加载 |
| LLM 层 | 模型接入 | LangChain4j + SiliconFlow/Claude |

本模块采用**分层架构**和**工厂模式**，实现了：
- ✅ 多角色差异化 AI 服务
- ✅ 子脑编排架构
- ✅ Function Calling 工具调用
- ✅ SSE 流式响应
- ✅ 对话记忆与摘要
- ✅ 动态 Skill 加载
- ✅ 限流熔断保护
- ✅ 完整监控追踪

---

## 七、RAG向量数据库（Qdrant）

### 7.1 概述

RAG（Retrieval-Augmented Generation，检索增强生成）是一种结合向量检索和LLM生成的技术。通过将非遗知识文档向量化存储到向量数据库，当用户提问时，系统先检索相关知识，再让LLM基于检索结果生成回答，从而解决LLM"幻觉"问题和知识截止问题。

### 7.2 技术架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        用户问题                                  │
└─────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                     EmbeddingService                            │
│              （将问题转换为向量）                                  │
└─────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Qdrant 向量数据库                             │
│              （相似度搜索，返回Top-K结果）                         │
└─────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                    VectorRagService                             │
│              （构建Prompt，调用LLM生成回答）                       │
└─────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                        最终回答                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 7.3 核心组件

#### 7.3.1 VectorProperties 配置类

```java
package com.hyang.ich.omnitrix.infrastructure.vector;

/**
 * Qdrant 向量数据库配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "omnitrix.vector")
public class VectorProperties {

    /** 是否启用向量检索功能 */
    private boolean enabled = false;

    /** Qdrant 服务地址 */
    private String host = "localhost";

    /** Qdrant HTTP 端口 */
    private int port = 6333;

    /** Qdrant gRPC 端口 */
    private int grpcPort = 6334;

    /** Qdrant API Key（可选） */
    private String apiKey;

    /** 默认集合名称 */
    private String collectionName = "ich_knowledge";

    /** Embedding 模型配置 */
    private EmbeddingConfig embedding = new EmbeddingConfig();

    /** 向量检索配置 */
    private SearchConfig search = new SearchConfig();

    @Data
    public static class EmbeddingConfig {
        /** Embedding 模型名称 */
        private String model = "BAAI/bge-large-zh-v1.5";

        /** 向量维度 */
        private int dimension = 1024;

        /** Embedding API URL */
        private String apiUrl = "https://api.siliconflow.cn/v1";

        /** Embedding API Key */
        private String apiKey;
    }

    @Data
    public static class SearchConfig {
        /** 搜索返回结果数量 */
        private int topK = 5;

        /** 相似度阈值（0-1） */
        private Double scoreThreshold = 0.7;
    }
}
```

#### 7.3.2 EmbeddingService 嵌入服务

```java
package com.hyang.ich.omnitrix.infrastructure.vector;

/**
 * 嵌入服务 - 负责将文本转换为向量表示
 */
@Slf4j
@Component
public class EmbeddingService {

    private final VectorProperties properties;
    private final EmbeddingModel embeddingModel;

    public EmbeddingService(VectorProperties properties) {
        this.properties = properties;
        this.embeddingModel = createEmbeddingModel();
    }

    /**
     * 单文本嵌入
     */
    public float[] embed(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new float[0];
        }

        try {
            Response<Embedding> response = embeddingModel.embed(text);
            return response.content().vector();
        } catch (Exception e) {
            log.error("文本嵌入失败: {}", e.getMessage(), e);
            return new float[0];
        }
    }

    /**
     * 批量文本嵌入
     */
    public List<float[]> embedAll(List<String> texts) {
        // 批量嵌入实现
    }

    public int getDimension() {
        return properties.getEmbedding().getDimension();
    }
}
```

#### 7.3.3 QdrantVectorStore 向量存储

```java
package com.hyang.ich.omnitrix.infrastructure.vector;

/**
 * Qdrant 向量存储服务
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "omnitrix.vector", name = "enabled", havingValue = "true")
public class QdrantVectorStore {

    private final VectorProperties properties;
    private final EmbeddingService embeddingService;
    private QdrantClient qdrantClient;

    /**
     * 添加文档到向量库
     */
    public void addDocument(String id, String text, Map<String, Object> metadata) {
        float[] vector = embeddingService.embed(text);
        // 构建向量点并存储到Qdrant
    }

    /**
     * 向量相似度搜索
     */
    public List<SearchResult> search(String query, Integer topK, Double scoreThreshold) {
        float[] queryVector = embeddingService.embed(query);
        // 在Qdrant中执行相似度搜索
        // 返回Top-K结果
    }
}
```

#### 7.3.4 VectorRagService RAG服务

```java
package com.hyang.ich.omnitrix.infrastructure.vector;

/**
 * RAG（检索增强生成）服务
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "omnitrix.vector", name = "enabled", havingValue = "true")
public class VectorRagService {

    private final QdrantVectorStore vectorStore;
    private final EmbeddingService embeddingService;
    private final ChatLanguageModel chatModel;

    /**
     * RAG问答
     */
    public RagResult answer(String query) {
        // 1. 向量检索
        List<SearchResult> searchResults = vectorStore.search(query);

        // 2. 构建上下文
        String context = buildContext(searchResults);

        // 3. 调用LLM生成
        String prompt = buildPrompt(query, context);
        String answer = chatModel.chat(prompt);

        // 4. 返回结果
        return RagResult.builder()
                .answer(answer)
                .sources(sources)
                .build();
    }
}
```

### 7.4 配置说明

在 `application.yml` 中配置：

```yaml
omnitrix:
  # 向量数据库配置 (Qdrant)
  vector:
    enabled: true              # 启用RAG功能
    host: localhost           # Qdrant服务地址
    port: 6333               # HTTP端口
    grpc-port: 6334          # gRPC端口
    api-key:                 # Qdrant Cloud API Key（可选）
    collection-name: ich-knowledge

    # Embedding 模型配置
    embedding:
      model: BAAI/bge-large-zh-v1.5    # 中文embedding模型
      dimension: 1024                   # 向量维度
      api-url: https://api.siliconflow.cn/v1
      api-key: sk-xxxxxxxxxxxxxxx      # SiliconFlow API Key

    # 检索配置
    search:
      top-k: 5           # 返回Top-5结果
      score-threshold: 0.7  # 相似度阈值
```

### 7.5 使用示例

```java
// 注入RAG服务
@Autowired
private VectorRagService ragService;

// 添加知识库文档
Map<String, Object> metadata = new HashMap<>();
metadata.put("title", "剪纸艺术介绍");
metadata.put("category", "非遗项目");
ragService.addDocument("paper-cutting-001", "剪纸是中国传统民间艺术...", metadata);

// RAG问答
RagResult result = ragService.answer("什么是剪纸艺术？");
System.out.println(result.getAnswer());  // AI生成的回答
System.out.println(result.getSources()); // 检索到的知识来源

// 批量添加
List<Document> docs = Arrays.asList(
    Document.builder().id("1").text("内容1").build(),
    Document.builder().id("2").text("内容2").build()
);
ragService.addDocuments(docs);
```

### 7.6 部署Qdrant

**本地Docker部署：**
```bash
docker run -d --name qdrant \
  -p 6333:6333 \
  -p 6334:6334 \
  qdrant/qdrant:latest
```

**Qdrant Cloud（托管服务）：**
1. 注册 https://cloud.qdrant.io
2. 创建集群，获取API Key和URL
3. 在配置中填写对应参数

---

## 八、多模态图片理解

### 8.1 概述

多模态功能让AI能够"看"懂图片，实现图片理解、图片问答、图像内容提取等能力。基于Anthropic Claude Vision实现。

### 8.2 核心组件

#### 8.2.1 MultimodalModel 多模态模型

```java
package com.hyang.ich.omnitrix.infrastructure.llm;

/**
 * 多模态模型服务
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "omnitrix.multimodal", name = "enabled", havingValue = "true")
public class MultimodalModel {

    private final LlmProperties llmProperties;
    private ChatLanguageModel multimodalModel;

    public MultimodalModel(LlmProperties properties) {
        this.llmProperties = properties;
        this.multimodalModel = createMultimodalModel();
    }

    private ChatLanguageModel createMultimodalModel() {
        LlmProperties.MultimodalConfig config = llmProperties.getMultimodal();

        // 使用 Claude Vision
        return AnthropicChatModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModel())
                .maxTokens(config.getMaxTokens())
                .temperature(config.getTemperature())
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .build();
    }
}
```

#### 8.2.2 MultimodalService 多模态服务

```java
package com.hyang.ich.omnitrix.infrastructure.multimodal;

/**
 * 多模态服务 - 提供图片理解能力
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "omnitrix.multimodal", name = "enabled", havingValue = "true")
public class MultimodalService {

    private final ChatLanguageModel multimodalModel;

    /**
     * 图片理解（单图）
     */
    public String analyzeImage(MultipartFile imageFile, String question) {
        String base64Image = convertToBase64(imageFile);
        String userMessage = String.format(
                "图片: data:image/jpeg;base64,%s\n\n请仔细观察图片，然后回答：%s",
                base64Image, question
        );

        ChatResponse response = multimodalModel.chat(userMessage);
        return response.aiMessage().singleText();
    }

    /**
     * 图片理解（多图）
     */
    public String analyzeImages(List<MultipartFile> imageFiles, String question) {
        // 处理多张图片
    }

    /**
     * 图片文字提取（OCR功能）
     */
    public String extractTextFromImage(MultipartFile imageFile) {
        // 提取图片中的文字
    }

    /**
     * 图片分类
     */
    public String categorizeImage(MultipartFile imageFile, List<String> categories) {
        // 图片分类
    }

    /**
     * 图片比较
     */
    public String compareImages(MultipartFile image1, MultipartFile image2) {
        // 比较两张图片差异
    }
}
```

### 8.3 配置说明

```yaml
omnitrix:
  # 多模态配置（图片理解）
  multimodal:
    enabled: true                         # 启用多模态
    provider: anthropic                   # 模型提供商
    model: claude-sonnet-4-20250514     # Claude Vision模型
    api-key: sk-ant-xxxxxxxxxxxxxxxx     # Claude API Key
    timeout-seconds: 180
    max-tokens: 4096
    temperature: 0.7
```

### 8.4 使用示例

```java
@Autowired
private MultimodalService multimodalService;

// 单图理解
String result = multimodalService.analyzeImage(
    imageFile, 
    "这张图片展示的是什么非遗项目？"
);

// 文字提取（OCR）
String text = multimodalService.extractTextFromImage(imageFile);

// 图片分类
List<String> categories = Arrays.asList("剪纸", "刺绣", "陶瓷", "其他");
String category = multimodalService.categorizeImage(imageFile, categories);

// 多图比较
String diff = multimodalService.compareImages(image1, image2);
```

---

## 九、配置汇总

### 9.1 完整配置示例

```yaml
omnitrix:
  llm:
    # 主模型（SiliconFlow DeepSeek）
    api-url: https://api.siliconflow.cn/v1/chat/completions
    model: deepseek-ai/DeepSeek-V3
    api-key: sk-xxxxxxxxxxxxxxxxxxxxxxxx
    timeout-seconds: 60
    max-tokens: 4096
    temperature: 0.7

    # Claude API（可选）
    claude:
      enabled: false
      model: claude-sonnet-4-20250514
      api-key: sk-ant-xxxxxxxxxxxxxxxx

  # 向量数据库（RAG）
  vector:
    enabled: true
    host: localhost
    port: 6333
    grpc-port: 6334
    collection-name: ich-knowledge

    embedding:
      model: BAAI/bge-large-zh-v1.5
      dimension: 1024
      api-url: https://api.siliconflow.cn/v1
      api-key: sk-xxxxxxxxxxxxxxxxxxxxxxxx
      use-remote: true

    search:
      top-k: 5
      score-threshold: 0.7

  # 多模态（图片理解）
  multimodal:
    enabled: false
    provider: anthropic
    model: claude-sonnet-4-20250514
    api-key: sk-ant-xxxxxxxxxxxxxxxx
    timeout-seconds: 180
    max-tokens: 4096
    temperature: 0.7
```

---

## 十、技术架构总结

| 层级 | 职责 | 关键技术 |
|------|------|----------|
| API 层 | HTTP 接口、SSE 流式 | Spring MVC、SseEmitter |
| 编排层 | 流程协调、限流熔断 | 自适应工作流、熔断器 |
| 大脑层 | 角色分发、工具注入 | LangChain4j AiServices |
| 工具层 | 业务能力封装 | LangChain4j @Tool |
| 记忆层 | 对话历史、摘要压缩 | Redis + MySQL |
| 技能层 | 动态能力扩展 | Skill 动态加载 |
| LLM 层 | 模型接入 | LangChain4j + SiliconFlow/Claude |
| 向量层 | RAG检索增强 | Qdrant + Embedding |
| 多模态层 | 图片理解 | Claude Vision |

本模块采用**分层架构**和**工厂模式**，实现了：
- ✅ 多角色差异化 AI 服务
- ✅ 子脑编排架构
- ✅ Function Calling 工具调用
- ✅ SSE 流式响应
- ✅ 对话记忆与摘要
- ✅ 动态 Skill 加载
- ✅ 限流熔断保护
- ✅ 完整监控追踪
- ✅ **RAG向量检索（Qdrant）**
- ✅ **多模态图片理解（Claude Vision）**

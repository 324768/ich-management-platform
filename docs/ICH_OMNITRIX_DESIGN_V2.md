# ICH Omnitrix 智能中台 —— 完整落地设计图 V2

> 基于 Kortex AI 框架思想重构，面向非遗文化管理平台的 AI 智能中台。
> 本文档描述 `ich_omnitrix` 模块实现成功后的完整形态。

---

## 一、系统定位

`ich_omnitrix` 是整个非遗管理平台的 **AI 智能中台微服务**。

它不是一个简单的"问答接口"，而是一个具备以下能力的 **AI 编排中枢**：

- **主脑编排**：接收用户自然语言请求，自动判断意图，调度合适的子代理
- **子代理体系**：每个业务域（内容、商城、用户、管理）各有专属 AI 子代理
- **对话记忆**：多轮对话上下文管理，支持摘要压缩，Redis 短期 + MySQL 长期
- **知识库问答**：非遗 FAQ 知识库，关键词匹配 + LLM 增强
- **Prompt 管理**：三层 Prompt 回退体系（数据库可配置 → 配置文件 → 代码兜底）
- **AI 自评分**：每次回答自动打分，记录质量追踪数据
- **SSE 流式输出**：实时推送 AI 生成内容，提升用户体验
- **智能推荐**：基于用户行为 + AI 分析的个性化推荐

---

## 二、整体架构图

```
┌──────────────────────────────────────────────────────────────────┐
│                        前端层 (Vue.js)                           │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────┐ │
│  │ 用户端 AI 聊天 │  │ 管理端 AI 面板 │  │ 管理端 Prompt/知识库配置 │ │
│  └──────┬───────┘  └──────┬───────┘  └───────────┬────────────┘ │
└─────────┼────────────────┼───────────────────────┼──────────────┘
          │ HTTP/SSE       │ HTTP                   │ HTTP
          ▼                ▼                        ▼
┌──────────────────────────────────────────────────────────────────┐
│                 ich_omnitrix (端口 8083)                          │
│                    AI 智能中台微服务                                │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                    Controller 层                              │ │
│  │  AiChatController    AiAdminController    AiConfigController │ │
│  └──────────┬──────────────────┬──────────────────┬────────────┘ │
│             │                  │                  │              │
│  ┌──────────▼──────────────────▼──────────────────▼────────────┐ │
│  │                  Orchestrator 层 (主脑)                      │ │
│  │                                                              │ │
│  │  ┌──────────────────────────────────────────────────────┐   │ │
│  │  │              OrchestratorService                      │   │ │
│  │  │  1. 接收用户消息                                        │   │ │
│  │  │  2. 组装 System Prompt (三层回退 + 上下文注入)            │   │ │
│  │  │  3. 意图路由 → 选择子代理                                │   │ │
│  │  │  4. 调用子代理执行                                       │   │ │
│  │  │  5. 包装结果 + 自评分                                    │   │ │
│  │  │  6. 返回/流式推送                                        │   │ │
│  │  └──────────────────────────┬─────────────────────────────┘   │ │
│  └─────────────────────────────┼─────────────────────────────────┘ │
│                                │                                  │
│  ┌─────────────────────────────▼─────────────────────────────────┐ │
│  │                    Sub-Agent 层 (子代理)                        │ │
│  │                                                                │ │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐         │ │
│  │  │ content  │ │ commerce │ │   user   │ │knowledge │         │ │
│  │  │_assistant│ │_assistant│ │_assistant│ │_assistant│         │ │
│  │  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘         │ │
│  │       │            │            │            │               │ │
│  │  ┌────▼─────┐ ┌────▼─────┐ ┌────▼─────┐ ┌────▼─────┐         │ │
│  │  │ general  │ │  admin   │ │recommend │ │          │         │ │
│  │  │_assistant│ │_assistant│ │_assistant│ │          │         │ │
│  │  └────┬─────┘ └────┬─────┘ └────┬─────┘ └──────────┘         │ │
│  └───────┼────────────┼────────────┼────────────────────────────┘ │
│          │            │            │                              │
│  ┌───────▼────────────▼────────────▼────────────────────────────┐ │
│  │                 Infrastructure 层                              │ │
│  │                                                                │ │
│  │  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐ │ │
│  │  │ LLM Client │ │  Memory    │ │  Prompt    │ │ Telemetry  │ │ │
│  │  │ (Ollama)   │ │  Manager   │ │  Manager   │ │  Tracer    │ │ │
│  │  └────────────┘ └────────────┘ └────────────┘ └────────────┘ │ │
│  │  ┌────────────┐ ┌────────────┐ ┌────────────┐               │ │
│  │  │    SSE     │ │  Intent    │ │  Result    │               │ │
│  │  │  Emitter   │ │  Router    │ │  Wrapper   │               │ │
│  │  └────────────┘ └────────────┘ └────────────┘               │ │
│  └──────────────────────────────────────────────────────────────┘ │
│                                                                  │
│            Dubbo RPC ↕                  Redis ↕    MySQL ↕       │
└──────────────────────────────────────────────────────────────────┘
          │                                │           │
          ▼                                ▼           ▼
┌──────────────────┐              ┌──────────┐  ┌──────────┐
│ 现有业务 Provider  │              │  Redis   │  │  MySQL   │
│                  │              │ 127.0.0.1│  │ai_service│
│ ┌──────────────┐ │              └──────────┘  └──────────┘
│ │ContentService│ │ (端口 8082)
│ │ProductService│ │ (端口 8082)
│ │ UserService  │ │ (端口 8081)
│ │ OrderService │ │ (端口 8081)
│ │SystemService │ │ (端口 8081)
│ └──────────────┘ │
└──────────────────┘
```

---

## 三、Java 包结构（完整目录树）

```
com.hyang.ich.omnitrix
│
├── OmnitrixApplication.java                    # Spring Boot 启动类
│
├── controller/                                 # ━━━ Controller 层 ━━━
│   ├── AiChatController.java                   # 用户端：聊天、对话管理
│   ├── AiAdminController.java                  # 管理端：AI Dashboard、对话审计
│   └── AiConfigController.java                 # 管理端：Prompt 配置、知识库 CRUD
│
├── orchestrator/                               # ━━━ 主脑编排层 ━━━
│   ├── OrchestratorService.java                # 核心编排器：意图判断 → 子代理调度 → 结果包装
│   ├── IntentRouter.java                       # 意图路由器：根据用户输入选择子代理
│   └── ResultWrapper.java                      # 结果包装器：统一子代理返回格式 + 状态判断
│
├── agent/                                      # ━━━ 子代理层 ━━━
│   ├── SubAgent.java                           # 子代理接口定义
│   ├── SubAgentRegistry.java                   # 子代理注册表（Spring 自动发现）
│   ├── impl/
│   │   ├── ContentSubAgent.java                # 非遗内容子代理
│   │   ├── CommerceSubAgent.java               # 商城/订单子代理
│   │   ├── UserSubAgent.java                   # 用户信息子代理
│   │   ├── AdminSubAgent.java                  # 管理运营子代理
│   │   ├── KnowledgeSubAgent.java              # 知识库问答子代理
│   │   ├── RecommendSubAgent.java              # 智能推荐子代理
│   │   └── GeneralSubAgent.java                # 通用问答子代理（兜底）
│   └── tool/                                   # 子代理可调用的业务工具
│       ├── ContentTools.java                   # 封装 ContentService Dubbo 调用
│       ├── CommerceTools.java                  # 封装 ProductService + OrderService
│       ├── UserTools.java                      # 封装 UserService
│       └── SystemTools.java                    # 封装 SystemService
│
├── infrastructure/                             # ━━━ 基础设施层 ━━━
│   ├── llm/
│   │   ├── LlmClient.java                     # LLM 调用客户端（统一封装）
│   │   ├── LlmProperties.java                 # LLM 配置属性类
│   │   ├── LlmRequest.java                    # 请求体 DTO
│   │   ├── LlmResponse.java                   # 响应体 DTO
│   │   └── LlmStreamHandler.java              # SSE 流式响应处理器
│   │
│   ├── memory/
│   │   ├── ChatMemoryManager.java              # 对话记忆管理器（Redis 短期 + DB 长期）
│   │   ├── MemorySummarizer.java               # 记忆摘要压缩器（调用 LLM 压缩旧消息）
│   │   └── RedisChatMemoryStore.java           # Redis 记忆存储实现
│   │
│   ├── prompt/
│   │   ├── PromptManager.java                  # Prompt 管理器（三层回退）
│   │   ├── PromptAssembler.java                # Prompt 组装器（注入上下文、时间、用户信息）
│   │   └── PromptTemplate.java                 # Prompt 模板枚举
│   │
│   ├── telemetry/
│   │   ├── TelemetryTracer.java                # AI 调用追踪记录器
│   │   ├── AiSelfEvaluator.java                # AI 自评分器（LLM-as-a-Judge）
│   │   └── TraceRecord.java                    # 追踪记录 DTO
│   │
│   └── sse/
│       └── SseEmitterManager.java              # SSE 连接管理器
│
├── service/                                    # ━━━ 业务服务层 ━━━
│   ├── AiServiceImpl.java                      # AiService Dubbo 接口实现
│   ├── ConversationService.java                # 对话管理服务
│   ├── KnowledgeService.java                   # 知识库管理服务
│   └── RecommendService.java                   # 推荐服务
│
├── entity/                                     # ━━━ 数据库实体 ━━━
│   ├── AiConversation.java                     # 对话实体
│   ├── AiMessage.java                          # 消息实体
│   ├── AiKnowledgeBase.java                    # 知识库实体
│   ├── AiPromptConfig.java                     # Prompt 配置实体
│   ├── AiTraceLog.java                         # 追踪日志实体
│   └── AiUserBehavior.java                     # 用户行为实体
│
├── mapper/                                     # ━━━ MyBatis Mapper ━━━
│   ├── AiConversationMapper.java
│   ├── AiMessageMapper.java
│   ├── AiKnowledgeBaseMapper.java
│   ├── AiPromptConfigMapper.java
│   ├── AiTraceLogMapper.java
│   └── AiUserBehaviorMapper.java
│
├── dto/                                        # ━━━ 数据传输对象 ━━━
│   ├── ChatRequest.java                        # 聊天请求
│   ├── ChatResponse.java                       # 聊天响应
│   ├── ConversationVO.java                     # 对话视图
│   ├── MessageVO.java                          # 消息视图
│   ├── TraceVO.java                            # 追踪视图
│   └── PromptConfigVO.java                     # Prompt 配置视图
│
└── config/                                     # ━━━ 配置类 ━━━
    ├── RedisConfig.java                        # Redis 序列化配置
    ├── WebMvcConfig.java                       # CORS、SSE 超时配置
    └── DubboConsumerConfig.java                # Dubbo 消费者引用配置
```

---

## 四、数据库设计（ai_service 库）

### 4.1 对话表

```sql
CREATE TABLE `ai_conversation` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`       BIGINT       NOT NULL              COMMENT '用户ID',
    `session_id`    VARCHAR(64)  NOT NULL              COMMENT '会话唯一标识(UUID)',
    `title`         VARCHAR(100) DEFAULT '新对话'       COMMENT '对话标题(AI自动生成)',
    `message_count` INT          DEFAULT 0             COMMENT '消息数量',
    `summary`       TEXT                               COMMENT '对话摘要(LLM自动生成)',
    `status`        TINYINT      DEFAULT 1             COMMENT '状态: 0=已删除 1=正常 2=归档',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_id` (`session_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话表';
```

### 4.2 消息表

```sql
CREATE TABLE `ai_message` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `conversation_id` BIGINT       NOT NULL              COMMENT '所属对话ID',
    `session_id`      VARCHAR(64)  NOT NULL              COMMENT '会话标识(冗余，加速查询)',
    `role`            VARCHAR(20)  NOT NULL              COMMENT '角色: system/user/assistant',
    `content`         MEDIUMTEXT   NOT NULL              COMMENT '消息内容',
    `tokens`          INT          DEFAULT 0             COMMENT '消耗token数',
    `model`           VARCHAR(50)                        COMMENT '使用的模型名',
    `sub_agent`       VARCHAR(50)                        COMMENT '处理该消息的子代理编码',
    `latency_ms`      INT                                COMMENT '响应耗时(毫秒)',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_conversation_id` (`conversation_id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI消息表';
```

### 4.3 知识库表

```sql
CREATE TABLE `ai_knowledge_base` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `question`      VARCHAR(500) NOT NULL              COMMENT '问题',
    `answer`        TEXT         NOT NULL              COMMENT '答案',
    `category_id`   BIGINT                             COMMENT '关联非遗分类ID(可空)',
    `keywords`      VARCHAR(500)                       COMMENT '关键词(逗号分隔,用于匹配)',
    `hit_count`     INT          DEFAULT 0             COMMENT '命中次数',
    `status`        TINYINT      DEFAULT 1             COMMENT '状态: 0=禁用 1=启用',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_status` (`status`),
    FULLTEXT KEY `ft_question` (`question`, `keywords`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI知识库表';
```

### 4.4 Prompt 配置表

```sql
CREATE TABLE `ai_prompt_config` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `prompt_key`    VARCHAR(100) NOT NULL              COMMENT 'Prompt唯一标识',
    `prompt_name`   VARCHAR(100) NOT NULL              COMMENT 'Prompt名称(便于管理)',
    `content`       TEXT         NOT NULL              COMMENT 'Prompt内容(支持{{变量}}占位符)',
    `category`      VARCHAR(50)  DEFAULT 'system'      COMMENT '分类: system/agent/flavor',
    `description`   VARCHAR(500)                       COMMENT '说明',
    `status`        TINYINT      DEFAULT 1             COMMENT '状态: 0=禁用 1=启用',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_prompt_key` (`prompt_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI Prompt 配置表';
```

### 4.5 追踪日志表

```sql
CREATE TABLE `ai_trace_log` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `trace_id`        VARCHAR(64)  NOT NULL              COMMENT '追踪链路ID',
    `conversation_id` BIGINT                             COMMENT '关联对话ID',
    `user_id`         BIGINT                             COMMENT '用户ID',
    `user_query`      TEXT                               COMMENT '用户原始问题',
    `intent`          VARCHAR(50)                        COMMENT '识别到的意图',
    `sub_agent`       VARCHAR(50)                        COMMENT '调度的子代理',
    `model`           VARCHAR(50)                        COMMENT '使用的模型',
    `input_tokens`    INT          DEFAULT 0             COMMENT '输入token数',
    `output_tokens`   INT          DEFAULT 0             COMMENT '输出token数',
    `latency_ms`      INT          DEFAULT 0             COMMENT '总耗时(毫秒)',
    `self_score`      DECIMAL(3,1)                       COMMENT 'AI自评分(0.0-10.0)',
    `status`          VARCHAR(20)  DEFAULT 'success'     COMMENT '状态: success/error/timeout',
    `error_message`   TEXT                               COMMENT '错误信息(如有)',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_trace_id` (`trace_id`),
    KEY `idx_conversation_id` (`conversation_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI追踪日志表';
```

### 4.6 用户行为表

```sql
CREATE TABLE `ai_user_behavior` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`       BIGINT       NOT NULL              COMMENT '用户ID',
    `item_id`       BIGINT                             COMMENT '关联项目/商品ID',
    `item_type`     VARCHAR(30)  NOT NULL              COMMENT '类型: ich_item/product/activity/post',
    `behavior_type` TINYINT      NOT NULL              COMMENT '行为: 1=浏览 2=收藏 3=购买 4=搜索 5=问AI',
    `keywords`      VARCHAR(255)                       COMMENT '搜索/提问关键词',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_item` (`item_type`, `item_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户行为记录表(用于AI推荐)';
```

### ER 关系图

```
ai_conversation 1───N ai_message
       │
       └─────────────── ai_trace_log (追踪每次AI调用)

ai_knowledge_base (独立，FAQ问答库)

ai_prompt_config (独立，Prompt配置管理)

ai_user_behavior ──→ 关联外部 ich_item / product 表
```

---

## 五、核心组件详细设计

### 5.1 主脑编排器 —— OrchestratorService

这是整个系统的**中枢控制器**，对标 Kortex 的 `OrchestratorService`。

```
用户消息进入
    │
    ▼
┌─────────────────────────────────────────────────────────┐
│              OrchestratorService.chat()                  │
│                                                         │
│  Step 1: 加载对话历史（ChatMemoryManager）                 │
│          └─ Redis 缓存 → 无则从 MySQL 加载                │
│                                                         │
│  Step 2: 组装 System Prompt（PromptAssembler）            │
│          ├─ 2a. 三层回退取基础 Prompt                      │
│          │     DB ai_prompt_config → yml → 代码常量        │
│          ├─ 2b. 注入当前时间                               │
│          ├─ 2c. 注入用户身份上下文                          │
│          ├─ 2d. 注入对话摘要 (如有)                        │
│          └─ 2e. 注入知识库命中 (如有)                      │
│                                                         │
│  Step 3: 意图路由（IntentRouter）                         │
│          ├─ 规则匹配关键词                                 │
│          ├─ 选出最佳子代理                                 │
│          └─ 获取子代理专属 Prompt 追加                     │
│                                                         │
│  Step 4: 调用 LLM（LlmClient）                           │
│          ├─ 组装 messages 数组                             │
│          ├─ 调用 Ollama API                               │
│          └─ 解析响应                                      │
│                                                         │
│  Step 5: 结果包装（ResultWrapper）                         │
│          ├─ 判断回答状态: success / empty / error          │
│          └─ 生成结构化返回                                 │
│                                                         │
│  Step 6: 自评分（AiSelfEvaluator）— 异步                  │
│          ├─ 用 LLM 对 [问题, 回答] 打分                   │
│          └─ 记录到 ai_trace_log                          │
│                                                         │
│  Step 7: 记忆更新                                        │
│          ├─ 保存消息到 Redis + MySQL                      │
│          ├─ 检查消息条数，触发摘要压缩                      │
│          └─ 异步生成/更新对话标题                          │
│                                                         │
│  Step 8: 返回响应                                        │
│          ├─ 同步模式: 直接返回 ChatResponse               │
│          └─ 流式模式: 通过 SseEmitter 逐 chunk 推送       │
└─────────────────────────────────────────────────────────┘
```

**核心方法签名：**

```java
public class OrchestratorService {

    /** 同步聊天（用于简单场景） */
    public ChatResponse chat(Long userId, String sessionId, String userMessage);

    /** 流式聊天（SSE推送，主推方案） */
    public SseEmitter chatStream(Long userId, String sessionId, String userMessage);
}
```

### 5.2 意图路由器 —— IntentRouter

对标 Kortex 的 `DynamicSubAgentToolProvider`，但用**规则匹配**替代 Function Calling。

```java
public class IntentRouter {

    /**
     * 根据用户输入判断应该由哪个子代理处理。
     * 返回子代理编码，如 "content_assistant", "commerce_assistant" 等。
     */
    public String route(String userQuery) {
        // 按优先级匹配
        if (matchesContent(userQuery))   return "content_assistant";
        if (matchesCommerce(userQuery))  return "commerce_assistant";
        if (matchesUser(userQuery))      return "user_assistant";
        if (matchesAdmin(userQuery))     return "admin_assistant";
        if (matchesKnowledge(userQuery)) return "knowledge_assistant";
        if (matchesRecommend(userQuery)) return "recommend_assistant";
        return "general_assistant"; // 兜底
    }
}
```

**路由规则示例：**

| 子代理 | 匹配关键词示例 |
|--------|---------------|
| `content_assistant` | 非遗、传承人、项目、文化、活动、剪纸、京剧、皮影... |
| `commerce_assistant` | 商品、购买、订单、购物车、发货、价格、库存... |
| `user_assistant` | 我的信息、地址、资格认证、传承人认证、修改密码... |
| `admin_assistant` | 统计、数据、管理、通知、审批、角色... |
| `knowledge_assistant` | 什么是、怎么、为什么、如何、请问... (+ 知识库命中) |
| `recommend_assistant` | 推荐、有什么好看的、类似的、相关的... |
| `general_assistant` | 以上都不匹配时的兜底 |

### 5.3 子代理接口 —— SubAgent

对标 Kortex 的 `SystemSubAgent` 接口。

```java
public interface SubAgent {

    /** 子代理唯一编码 */
    String getCode();

    /** 子代理名称 */
    String getName();

    /** 子代理描述（用于路由提示） */
    String getDescription();

    /**
     * 执行子代理逻辑。
     *
     * @param userQuery  用户原始问题
     * @param context    编排上下文(含用户信息、对话历史等)
     * @return           处理结果文本
     */
    String execute(String userQuery, AgentContext context);
}
```

### 5.4 七个子代理的具体职责

#### ① ContentSubAgent —— 非遗内容助手

```
职责: 回答非遗项目、传承人、活动相关问题
数据来源: ContentService (Dubbo RPC)
能力:
  - 查询非遗项目详情 → contentService.getItemById()
  - 搜索非遗项目列表 → contentService.listItems()
  - 查询传承人信息   → contentService.getHeritageManById()
  - 查询活动信息     → contentService.getActivityById()
  - 查询帖子/动态    → contentService.listPosts()

工作流程:
  1. 从用户问题提取关键词
  2. 调用 ContentService 查询相关数据
  3. 将查询结果 + 用户问题一起发给 LLM
  4. LLM 基于真实数据生成自然语言回答
```

#### ② CommerceSubAgent —— 商城助手

```
职责: 回答商品、订单、购物车相关问题
数据来源: ProductService + OrderService (Dubbo RPC)
能力:
  - 查询商品信息     → productService.getProductById()
  - 搜索商品列表     → productService.listProducts()
  - 查询用户订单     → orderService.listUserOrders()
  - 查询订单详情     → orderService.getOrderById()
  - 查询购物车       → productService.listCartItems()

工作流程:
  1. 从用户问题判断是查商品还是查订单
  2. 调用对应 Service 获取数据
  3. 将数据 + 问题发给 LLM 生成回答
```

#### ③ UserSubAgent —— 用户信息助手

```
职责: 回答用户个人信息、认证相关问题
数据来源: UserService (Dubbo RPC)
能力:
  - 查询用户信息     → userService.findById()
  - 查询地址列表     → userService.listAddresses()
  - 查询资格认证状态 → userService.getQualificationByUserId()
  - 查询传承人标志   → userService.hasHeritageFlag()
```

#### ④ AdminSubAgent —— 管理运营助手

```
职责: 回答管理统计、运营数据相关问题（仅管理员可触发）
数据来源: 多个 Service 聚合 (类似 AdminDashboardController)
能力:
  - 查询平台统计     → countItems() + countUsers() + countProducts() + countOrders()
  - 查询低库存商品   → productService.listLowStockProducts()
  - 查询最近订单     → orderService.listRecentOrders()
  - 查询订单趋势     → orderService.getWeeklyOrderCounts()
```

#### ⑤ KnowledgeSubAgent —— 知识库问答助手

```
职责: 基于 FAQ 知识库回答常见问题
数据来源: ai_knowledge_base 表
工作流程:
  1. 用关键词 / 全文检索 匹配知识库
  2. 如果命中 → 将知识库答案作为参考，LLM 润色后返回
  3. 如果未命中 → 交给 GeneralSubAgent 处理
  4. 更新命中计数
```

#### ⑥ RecommendSubAgent —— 智能推荐助手

```
职责: 基于用户行为生成个性化推荐
数据来源: ai_user_behavior 表 + ContentService + ProductService
工作流程:
  1. 查询用户近期行为（浏览、收藏、购买）
  2. 提取用户兴趣标签
  3. 调用 ContentService / ProductService 查询相关内容
  4. 用 LLM 生成推荐理由
  5. 返回推荐列表 + 理由
```

#### ⑦ GeneralSubAgent —— 通用问答助手（兜底）

```
职责: 处理不属于任何特定领域的通用问题
数据来源: 无外部数据，纯 LLM 对话
工作流程:
  1. 直接将用户问题 + 系统 Prompt 发给 LLM
  2. 返回 LLM 回答
  特点: 不查业务数据，纯粹依赖模型知识
```

### 5.5 Prompt 管理体系 —— PromptManager

三层回退，对标 Kortex 的 Langfuse → Nacos → 代码兜底。

```
┌─────────────────────────────────────────────────────────┐
│                    PromptManager                         │
│                                                         │
│  resolve("master_brain_system"):                        │
│                                                         │
│  Layer 1: 数据库 ai_prompt_config 表                     │
│           └─ SELECT content FROM ai_prompt_config       │
│              WHERE prompt_key = ? AND status = 1        │
│           └─ 优势: 管理后台可随时修改，无需重启            │
│           │                                             │
│           ▼ (未命中)                                     │
│                                                         │
│  Layer 2: application.yml 配置文件                       │
│           └─ omnitrix.prompts.master-brain-system        │
│           └─ 优势: 版本可控，跟随代码发布                  │
│           │                                             │
│           ▼ (未命中)                                     │
│                                                         │
│  Layer 3: 代码硬编码常量                                  │
│           └─ PromptTemplate.MASTER_BRAIN_SYSTEM          │
│           └─ 优势: 永不为空的兜底值                       │
└─────────────────────────────────────────────────────────┘
```

**Prompt 组装器注入的 5 层上下文（对标 Kortex OrchestratorService）：**

```
最终 System Prompt = 

  [基础 Prompt]                     ← 三层回退取得
  + [当前时间]                       ← "今天是 2026年3月9日 星期一"
  + [用户身份上下文]                  ← "用户: 张三, 角色: 普通用户"
  + [对话摘要]                       ← "之前讨论了京剧的历史..."
  + [知识库参考]                     ← "相关知识: 京剧起源于..."
```

### 5.6 对话记忆管理 —— ChatMemoryManager

```
┌──────────────────────────────────────────────┐
│            ChatMemoryManager                  │
│                                              │
│  读取记忆:                                    │
│  1. 先查 Redis (key: chat:session:{id})      │
│  2. Redis 未命中 → 查 MySQL ai_message 表     │
│  3. 加载后写回 Redis                          │
│                                              │
│  写入记忆:                                    │
│  1. 写入 Redis (实时)                         │
│  2. 异步写入 MySQL (持久化)                    │
│                                              │
│  摘要压缩 (MemorySummarizer):                 │
│  当消息数 > 20 条时触发:                       │
│  1. 取出前 14 条消息                           │
│  2. 调用 LLM 生成摘要                         │
│  3. 用 1 条摘要消息替换 14 条原始消息           │
│  4. 摘要存入 ai_conversation.summary           │
│  5. Redis 中同步更新                          │
│                                              │
│  Redis 数据结构:                               │
│  chat:session:{sessionId}  → List<Message>   │
│  chat:summary:{sessionId}  → String          │
│  TTL: 24 小时                                 │
└──────────────────────────────────────────────┘
```

### 5.7 AI 自评分 —— AiSelfEvaluator

对标 Kortex 的 `LangfuseTracingListener` + HTML 版的 `TelemetryTracer`。

```
┌──────────────────────────────────────────────┐
│           AiSelfEvaluator (异步执行)           │
│                                              │
│  输入:                                        │
│    - userQuery: "京剧的起源是什么？"            │
│    - aiResponse: "京剧起源于18世纪中期..."      │
│    - subAgent: "content_assistant"            │
│    - latencyMs: 2300                          │
│                                              │
│  流程:                                        │
│  1. 构造评分 Prompt:                           │
│     "请对以下 AI 回答打分(0-10分)，            │
│      评估维度: 准确性、完整性、相关性。          │
│      用户问题: {userQuery}                     │
│      AI 回答: {aiResponse}                     │
│      请只返回一个数字分数。"                    │
│                                              │
│  2. 调用 LLM 获取分数                          │
│                                              │
│  3. 写入 ai_trace_log 表                      │
│     (trace_id, user_query, intent,            │
│      sub_agent, latency_ms, self_score...)    │
│                                              │
│  4. 如果分数 < 4.0，标记 needsRedo (可选)     │
└──────────────────────────────────────────────┘
```

### 5.8 SSE 流式输出 —— SseEmitterManager

```
┌──────────────────────────────────────────────┐
│           SSE 流式输出流程                      │
│                                              │
│  前端:                                        │
│  const es = new EventSource(                 │
│    '/api/ai/chat/stream?sessionId=xxx'       │
│  );                                          │
│  es.onmessage = (e) => {                     │
│    appendToChat(e.data);   // 逐 chunk 追加   │
│  };                                          │
│                                              │
│  后端:                                        │
│  @GetMapping("/chat/stream")                 │
│  public SseEmitter chatStream(...) {         │
│    SseEmitter emitter = new SseEmitter();     │
│    // 异步调用 Ollama stream=true             │
│    // 每收到一个 chunk → emitter.send(chunk)   │
│    // 全部完成 → emitter.complete()            │
│    return emitter;                            │
│  }                                           │
│                                              │
│  消息事件类型:                                  │
│  - "chunk"  : AI 回答的文本片段                │
│  - "agent"  : 子代理调度通知                   │
│  - "done"   : 回答完成                        │
│  - "error"  : 错误信息                        │
└──────────────────────────────────────────────┘
```

---

## 六、REST API 设计

### 6.1 用户端 —— AiChatController

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/ai/chat` | 同步聊天（发消息，等完整回复） |
| `GET`  | `/api/ai/chat/stream` | 流式聊天（SSE 逐 chunk 推送） |
| `POST` | `/api/ai/conversation/create` | 创建新对话 |
| `GET`  | `/api/ai/conversation/list` | 获取用户对话列表 |
| `GET`  | `/api/ai/conversation/{id}` | 获取对话详情（含消息） |
| `DELETE` | `/api/ai/conversation/{id}` | 删除对话 |
| `GET`  | `/api/ai/recommend` | 获取 AI 推荐内容 |

**POST /api/ai/chat 请求/响应示例：**

```json
// 请求
{
    "userId": 1,
    "sessionId": "sess_abc123",
    "message": "请介绍一下京剧的历史"
}

// 响应
{
    "code": 200,
    "data": {
        "messageId": 42,
        "sessionId": "sess_abc123",
        "role": "assistant",
        "content": "京剧是中国影响最大的戏曲剧种，起源于18世纪中期的北京...",
        "subAgent": "content_assistant",
        "latencyMs": 2300,
        "relatedItems": [
            { "id": 1, "name": "京剧", "type": "ich_item" },
            { "id": 5, "name": "梅兰芳", "type": "heritage_man" }
        ]
    }
}
```

### 6.2 管理端 —— AiAdminController

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/admin/ai/dashboard` | AI 面板统计数据 |
| `GET` | `/api/admin/ai/traces` | 分页查询追踪日志 |
| `GET` | `/api/admin/ai/conversations` | 分页查询所有对话 |
| `GET` | `/api/admin/ai/conversations/{id}` | 查看对话详情 |

**GET /api/admin/ai/dashboard 响应示例：**

```json
{
    "code": 200,
    "data": {
        "totalConversations": 156,
        "totalMessages": 1240,
        "todayMessages": 23,
        "avgScore": 7.8,
        "avgLatencyMs": 1850,
        "topAgents": [
            { "agent": "content_assistant", "count": 520 },
            { "agent": "knowledge_assistant", "count": 340 },
            { "agent": "commerce_assistant", "count": 180 }
        ],
        "scoreDistribution": {
            "excellent": 45,
            "good": 67,
            "fair": 30,
            "poor": 14
        }
    }
}
```

### 6.3 配置管理 —— AiConfigController

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/admin/ai/prompt/list` | 查询 Prompt 配置列表 |
| `POST` | `/api/admin/ai/prompt/save` | 新增/修改 Prompt |
| `DELETE` | `/api/admin/ai/prompt/{id}` | 删除 Prompt |
| `GET` | `/api/admin/ai/knowledge/list` | 分页查询知识库 |
| `POST` | `/api/admin/ai/knowledge/save` | 新增/修改知识 |
| `DELETE` | `/api/admin/ai/knowledge/{id}` | 删除知识 |

---

## 七、配置文件设计

```yaml
# application.yml (ich_omnitrix)
server:
  port: 8083

spring:
  application:
    name: ich-omnitrix
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://127.0.0.1:3306/ai_service?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: 123456
  redis:
    host: 127.0.0.1
    port: 6379
    database: 1

mybatis:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.hyang.ich.omnitrix.entity
  configuration:
    map-underscore-to-camel-case: true

dubbo:
  application:
    name: ich-omnitrix
  registry:
    address: zookeeper://127.0.0.1:2181
  protocol:
    name: dubbo
    port: 20883
  scan:
    base-packages: com.hyang.ich.omnitrix

# ━━━ Omnitrix AI 专属配置 ━━━
omnitrix:
  llm:
    api-url: http://localhost:11434/v1/chat/completions
    model: qwen2.5:3b
    timeout-seconds: 60
    max-tokens: 2048
    temperature: 0.7

  memory:
    max-messages: 20            # 触发摘要压缩的消息条数阈值
    redis-ttl-hours: 24         # Redis 记忆 TTL
    summary-enabled: true       # 是否启用自动摘要

  evaluation:
    enabled: true               # 是否启用 AI 自评分
    async: true                 # 是否异步执行评分

  prompts:
    master-brain-system: |
      # Role & Identity
      你是非物质文化遗产管理平台的智能助手 Omnitrix AI。
      你专注于非遗文化保护、传承、展示，以及文创商品推荐。
      沟通风格：专业、简洁、有文化底蕴。
      禁止套话，禁止重复用户的问题。

logging:
  level:
    root: info
    com.hyang.ich.omnitrix: debug
```

---

## 八、Dubbo 服务消费配置

`ich_omnitrix` 作为 AI 中台，需要**消费**其他 Provider 的 Dubbo 服务。

```java
@Configuration
public class DubboConsumerConfig {

    @DubboReference(check = false)
    private ContentService contentService;

    @DubboReference(check = false)
    private ProductService productService;

    @DubboReference(check = false)
    private UserService userService;

    @DubboReference(check = false)
    private OrderService orderService;

    @DubboReference(check = false)
    private SystemService systemService;

    // 通过 @Bean 暴露给 Spring 容器，供各 SubAgent 注入
    @Bean public ContentService contentService() { return contentService; }
    @Bean public ProductService productService() { return productService; }
    @Bean public UserService userService()       { return userService; }
    @Bean public OrderService orderService()     { return orderService; }
    @Bean public SystemService systemService()   { return systemService; }
}
```

**这是整个设计最精华的衔接点：**
- `ich_omnitrix` 通过 Dubbo 消费现有 Provider 的能力
- 子代理封装这些能力为"AI 可调度的工具"
- 主脑根据意图选择子代理 → 子代理调用 Dubbo 服务 → 获取真实数据 → LLM 生成回答

---

## 九、完整调用链路（时序图）

### 用户发起聊天的完整链路

```
用户(前端)           AiChatController      OrchestratorService     IntentRouter
   │                      │                      │                    │
   │  POST /api/ai/chat   │                      │                    │
   │─────────────────────▶│                      │                    │
   │                      │  chat(userId,         │                    │
   │                      │   sessionId, msg)     │                    │
   │                      │─────────────────────▶│                    │
   │                      │                      │  route(userQuery)   │
   │                      │                      │──────────────────▶│
   │                      │                      │  "content_assistant"│
   │                      │                      │◀──────────────────│
   │                      │                      │                    │

                          PromptAssembler        ContentSubAgent      LlmClient
                               │                      │                 │
   │                      │    │                      │                 │
   │                      │  assemble()               │                 │
   │                      │──▶│                      │                 │
   │                      │  systemPrompt             │                 │
   │                      │◀──│                      │                 │
   │                      │                           │                 │
   │                      │  subAgent.execute()       │                 │
   │                      │─────────────────────────▶│                 │
   │                      │                          │                 │
   │                      │          ┌───────────────┤                 │
   │                      │          │ 1. 提取关键词  │                 │
   │                      │          │ 2. Dubbo 调用  │                 │
   │                      │          │    ContentService               │
   │                      │          │ 3. 获取非遗数据 │                 │
   │                      │          │ 4. 拼装 Prompt │                 │
   │                      │          └───────────────┤                 │
   │                      │                          │  callLlm()      │
   │                      │                          │────────────────▶│
   │                      │                          │                 │ HTTP POST
   │                      │                          │                 │ Ollama API
   │                      │                          │  aiResponse     │
   │                      │                          │◀────────────────│
   │                      │          result          │                 │
   │                      │◀─────────────────────────│                 │
   │                      │                                            │

                          ChatMemoryManager    AiSelfEvaluator
                               │                    │
   │                      │    │                    │
   │                      │  saveMessages()         │
   │                      │──▶│                    │
   │                      │                         │
   │                      │  evaluate() [async]     │
   │                      │────────────────────────▶│
   │                      │                         │ → ai_trace_log
   │                      │                         │
   │  ChatResponse        │                         │
   │◀─────────────────────│                         │
   │                      │                         │
```

---

## 十、前端接入点设计

### 10.1 用户端 Vue 页面规划

```
ich-user-web-vue/src/views/ai/
├── AiChat.vue              # AI 聊天主页面
│   ├── 左侧: 对话列表（历史会话）
│   ├── 中间: 聊天消息区（支持 Markdown 渲染）
│   ├── 底部: 输入框 + 发送按钮
│   └── 右侧: 推荐内容卡片（可选）
│
└── components/
    ├── ChatMessage.vue     # 单条消息组件（区分 user/assistant 样式）
    ├── SessionList.vue     # 对话列表组件
    └── RecommendPanel.vue  # 推荐面板组件
```

### 10.2 管理端 Vue 页面规划

```
ich-admin-web-vue/src/views/ai/
├── AiDashboard.vue         # AI 面板
│   ├── 统计卡片：总对话数、今日消息数、平均分、平均耗时
│   ├── 图表：子代理调用分布（饼图）
│   ├── 图表：评分分布（柱状图）
│   └── 图表：每日消息趋势（折线图）
│
├── AiConversationList.vue  # 对话审计列表
│   └── 可展开查看完整对话内容
│
├── AiKnowledgeList.vue     # 知识库管理
│   ├── 列表：问题、答案、关键词、命中次数、状态
│   └── 表单：新增/编辑知识条目
│
├── AiPromptList.vue        # Prompt 配置管理
│   ├── 列表：Prompt Key、名称、分类、状态
│   └── 表单：编辑 Prompt 内容（大文本框）
│
└── AiTraceList.vue         # 追踪日志查看
    └── 列表：时间、用户、问题、意图、子代理、耗时、评分
```

---

## 十一、"实现成功后"的用户体验

### 场景 1：用户问非遗知识

```
用户: 京剧有哪些流派？

[系统内部流程]
  IntentRouter → content_assistant
  ContentSubAgent:
    1. 搜索 ich_item 表: keyword="京剧"
    2. 搜索 ai_knowledge_base: keyword="京剧 流派"
    3. 命中知识库条目 + 查到京剧项目数据
    4. 将真实数据注入 Prompt，调用 LLM

AI: 京剧主要有四大流派：
    - **梅派**（梅兰芳创立）：以华丽典雅著称...
    - **程派**（程砚秋创立）：以幽咽婉转见长...
    - **荀派**（荀慧生创立）：以活泼自然为特色...
    - **尚派**（尚小云创立）：以刚健洒脱为风格...

    我们平台收录了相关非遗项目「京剧」(国家级)，您可以查看详情了解更多。

[追踪记录]
  intent: content_assistant
  latency: 2.1s
  self_score: 8.5
```

### 场景 2：用户问订单

```
用户: 我最近有什么订单？

[系统内部流程]
  IntentRouter → commerce_assistant
  CommerceSubAgent:
    1. orderService.listUserOrders(userId, null, 1, 5)
    2. 获取到 3 笔订单数据
    3. 将订单数据注入 Prompt

AI: 您近期有 3 笔订单：
    1. 订单 ORD2026030901 — 「手工剪纸套装」— 已发货
    2. 订单 ORD2026030502 — 「景德镇陶瓷杯」— 已完成
    3. 订单 ORD2026030103 — 「皮影戏摆件」— 待付款

    需要我帮您查看某笔订单的详情吗？
```

### 场景 3：用户请求推荐

```
用户: 有什么好看的非遗项目推荐给我？

[系统内部流程]
  IntentRouter → recommend_assistant
  RecommendSubAgent:
    1. 查询 ai_user_behavior: 用户近期浏览了「剪纸」「皮影戏」
    2. 提取兴趣标签: 传统手工艺、表演艺术
    3. contentService.listItems(keyword="手工艺")
    4. LLM 生成推荐理由

AI: 根据您最近对「剪纸」和「皮影戏」的兴趣，为您推荐：

    🎭 **木偶戏** — 与皮影戏同属传统表演艺术
    🧵 **苏绣** — 与剪纸同为精湛手工技艺
    🏺 **景德镇陶瓷** — 国家级非遗，工艺之美

    点击可查看详情。
```

### 场景 4：管理员查看 AI Dashboard

```
管理员打开 AI 面板:

┌─────────────────────────────────────────────────────┐
│  AI 智能中台运营面板                                   │
│                                                     │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────┐ │
│  │ 总对话数  │ │ 今日消息  │ │ 平均评分  │ │平均耗时│ │
│  │   156    │ │    23    │ │   7.8    │ │ 1.85s │ │
│  └──────────┘ └──────────┘ └──────────┘ └────────┘ │
│                                                     │
│  [子代理调用分布]        [评分分布]                    │
│  ┌──────────────┐     ┌──────────────┐              │
│  │    ●         │     │  ████        │              │
│  │   ╱╲ 42%    │     │  ████████    │              │
│  │  ╱  ╲ 内容  │     │  ██████      │              │
│  │ 27% 知识库  │     │  ███         │              │
│  │ 15% 商城    │     │ 优 良 中 差   │              │
│  │ 16% 其他    │     └──────────────┘              │
│  └──────────────┘                                   │
│                                                     │
│  [最近追踪记录]                                       │
│  时间     用户   问题           意图     评分  耗时    │
│  10:30  张三  京剧的流派?    content   8.5  2.1s   │
│  10:28  李四  我的订单?      commerce  7.0  1.8s   │
│  10:25  王五  什么是非遗?    knowledge 9.0  1.2s   │
└─────────────────────────────────────────────────────┘
```

---

## 十二、分阶段实施路线

### Phase 1：基础骨架（优先级最高）

```
目标: 能跑通"用户发消息 → AI 回复"的最小闭环

要做的事:
  □ 创建 ai_service 数据库 + 全部 6 张表
  □ LlmClient: 封装 Ollama HTTP 调用
  □ ConversationService: 对话/消息 CRUD
  □ PromptManager: 三层 Prompt 回退
  □ PromptAssembler: 5 层上下文注入
  □ GeneralSubAgent: 通用问答子代理
  □ OrchestratorService: 主脑（暂不路由，全走 General）
  □ AiChatController: POST /api/ai/chat
  □ AiServiceImpl: 实现 Dubbo AiService 接口

验收标准:
  ✓ 调用 POST /api/ai/chat 能收到 AI 回复
  ✓ 对话和消息正确存入 MySQL
  ✓ Prompt 从 yml 或代码常量正确加载
```

### Phase 2：子代理体系

```
目标: 实现意图路由 + 多个业务子代理

要做的事:
  □ IntentRouter: 规则匹配路由
  □ SubAgent 接口 + SubAgentRegistry
  □ DubboConsumerConfig: 消费业务 Service
  □ ContentSubAgent + ContentTools
  □ CommerceSubAgent + CommerceTools
  □ KnowledgeSubAgent + KnowledgeService
  □ AiConfigController: 知识库 CRUD 接口

验收标准:
  ✓ 问非遗问题 → 路由到 ContentSubAgent → 返回含真实数据的回答
  ✓ 问商品问题 → 路由到 CommerceSubAgent
  ✓ 知识库命中时优先使用知识库答案
```

### Phase 3：记忆与流式

```
目标: 多轮对话体验 + 实时流式输出

要做的事:
  □ ChatMemoryManager: Redis 短期 + MySQL 长期
  □ MemorySummarizer: 摘要压缩
  □ SseEmitterManager: SSE 流式输出
  □ AiChatController: GET /api/ai/chat/stream
  □ 对话标题自动生成

验收标准:
  ✓ 多轮对话保持上下文
  ✓ 超过 20 条消息自动摘要
  ✓ SSE 流式输出逐字显示
```

### Phase 4：评分与运营

```
目标: AI 质量追踪 + 管理后台

要做的事:
  □ AiSelfEvaluator: 自评分
  □ TelemetryTracer: 追踪记录
  □ AiAdminController: Dashboard + 日志查询
  □ AiConfigController: Prompt 配置管理
  □ RecommendSubAgent + UserSubAgent + AdminSubAgent

验收标准:
  ✓ 每次 AI 回答自动打分并记录
  ✓ 管理后台能看到 Dashboard 统计
  ✓ 管理后台能修改 Prompt 并立即生效
  ✓ 推荐功能基于用户行为生成
```

---

## 十三、与现有系统的关系总结

```
┌─────────────────────────────────────────────────────────────┐
│                    ich-management-platform                    │
│                                                             │
│  ich_interface ─── 所有 Dubbo 接口定义(含 AiService)         │
│       │                                                     │
│       ├── ich_user_provider ──── 用户/订单/系统 服务实现      │
│       │      ↑                                              │
│       │      │ Dubbo RPC                                    │
│       │      │                                              │
│       ├── ich_content_provider ─ 内容/商品 服务实现           │
│       │      ↑                                              │
│       │      │ Dubbo RPC                                    │
│       │      │                                              │
│       ├── ich_omnitrix ─────── AI 智能中台 ◀── 本文档描述    │
│       │      │                                              │
│       │      ├─ 消费 ContentService (Dubbo)                 │
│       │      ├─ 消费 ProductService  (Dubbo)                │
│       │      ├─ 消费 UserService     (Dubbo)                │
│       │      ├─ 消费 OrderService    (Dubbo)                │
│       │      ├─ 消费 SystemService   (Dubbo)                │
│       │      ├─ 暴露 AiService       (Dubbo, 给其他模块用)  │
│       │      ├─ 暴露 REST API        (给前端用)              │
│       │      └─ 调用 Ollama LLM      (HTTP)                │
│       │                                                     │
│       ├── ich_user_web ─────── 用户端 Web API                │
│       │      └─ 可通过 Dubbo 调用 AiService                 │
│       │                                                     │
│       └── ich_admin_web ────── 管理端 Web API                │
│              └─ 可通过 Dubbo 调用 AiService                  │
│                                                             │
│  ich-user-web-vue ──── 用户端前端 (AI 聊天页面)              │
│  ich-admin-web-vue ─── 管理端前端 (AI Dashboard)             │
│  ich_user_app_flutter ─ 移动端 (AI 助手入口)                 │
└─────────────────────────────────────────────────────────────┘
```

---

## 十四、技术选型确认

| 层次 | 技术 | 说明 |
|------|------|------|
| LLM | Ollama + qwen2.5:3b | 本地部署，免费，无需外网 |
| HTTP 客户端 | Spring RestTemplate / OkHttp | 调用 Ollama API |
| 流式输出 | Spring SseEmitter | 原生支持，无需 WebFlux |
| 缓存 | Spring Data Redis + Lettuce | 已在 pom.xml 中 |
| 数据库 | MySQL 8.0 + MyBatis | 与现有技术栈一致 |
| 服务调用 | Apache Dubbo 2.7.8 | 与现有技术栈一致 |
| 注册中心 | ZooKeeper | 与现有技术栈一致 |
| JSON | Jackson + Gson | 已在 pom.xml 中 |
| 异步执行 | Spring @Async | 自评分、摘要压缩等异步任务 |

---

> **本文档描述的是 `ich_omnitrix` 模块实现成功后的完整形态。**
> **按 Phase 1 → 4 分阶段实施，每个 Phase 都有独立的验收标准。**
> **核心设计思想来源于 Kortex AI 框架，但完全适配了非遗管理平台的业务域和技术栈。**

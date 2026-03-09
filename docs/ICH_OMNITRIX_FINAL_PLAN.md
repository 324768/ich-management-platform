# Omnitrix AI 智能中台 —— 最终实施方案

> 本文档是 `ich_omnitrix` 模块的**最终实施蓝图**，整合了全部分析讨论成果。
> 实施时严格按照本文档执行。
> 详细实施步骤见 [ICH_OMNITRIX_FINAL_PLAN_IMPL.md](./ICH_OMNITRIX_FINAL_PLAN_IMPL.md)

---

## 第一章 架构决策总表

| 编号 | 决策项 | 结论 | 原因 |
|------|--------|------|------|
| D1 | LLM 模型 | Ollama + qwen2.5:3b（本地部署） | 免费、无需外网、中文能力可用 |
| D2 | 编排模式 | **代码驱动编排**（Java IntentRouter） | 3B 模型不支持可靠的 Function Calling |
| D3 | 子代理本质 | Java Service 方法（不是独立 LLM 会话） | 降低 LLM 调用次数和延迟 |
| D4 | LLM 调用次数 | 每次用户请求 **1 次** LLM 调用 | 子代理查数据→数据注入Prompt→LLM生成回答 |
| D5 | 是否引入 Langfuse | **否** | 自研 AiSelfEvaluator + ai_trace_log 替代 |
| D6 | 是否引入 Skills 层 | **否** | SubAgent 内部用枚举区分技能即可 |
| D7 | 数据访问方式 | **Dubbo RPC 实时查询**（无数据同步） | 零延迟，新增数据立即可被 AI 感知 |
| D8 | 是否需要向量数据库 | **否** | MySQL 关键词匹配 + Dubbo 实时查询满足需求 |
| D9 | Prompt 管理 | 三层回退（DB → yml → 代码常量） | 替代 Kortex 的 Langfuse → Nacos → 代码 |
| D10 | 记忆摘要格式 | **自然语言**（非 XML） | 3B 模型输出严格 XML 不稳定 |
| D11 | 用户隔离 | 逻辑隔离（userId 区分） | 1 个服务实例服务所有用户 |

---

## 第二章 架构对比：Kortex AI vs Omnitrix AI

### 2.1 一句话概括

- **Kortex AI** = AI 驱动的编排：LLM 自己决定调哪个子代理
- **Omnitrix AI** = 代码驱动的编排 + AI 增强：代码做决策，LLM 只做生成

### 2.2 核心流程差异

```
Kortex:
  用户消息 → [LLM思考:该调谁?] → [LLM生成tool_call] → 子代理(独立LLM)执行
           → [LLM读hint:够不够?] → 可能再调下一个子代理 → 整合结果

Omnitrix:
  用户消息 → [代码IntentRouter匹配] → 子代理(Java方法)查数据库
           → 数据注入Prompt → [LLM:把数据说成人话] → 返回
```

### 2.3 逐维度对比

| 维度 | Kortex AI | Omnitrix AI |
|------|-----------|-------------|
| 编排决策者 | LLM 自主决策 | Java IntentRouter 代码决策 |
| Function Calling | 核心依赖 | 不使用 |
| 子代理本质 | 独立 LLM 会话（有自己的模型/Prompt/记忆/工具集） | Java Service 方法（查数据库返回文本） |
| LLM 调用次数/请求 | N 次（主脑+每个子代理+工具调用） | 1 次 |
| 子代理间通信 | 主脑通过 context 参数传递（记忆隔离） | 不需要（Java 对象共享内存） |
| 多任务串联 | 支持（LLM 自主多轮调度） | 单任务（一次请求一个子代理） |
| 模型要求 | 强模型 (GPT-4/72B+) | 弱模型即可 (3B) |
| Prompt 层数 | 7 层 | 5 层 |
| Hint 状态数 | 15+ 种 | 4 种 |

### 2.4 Omnitrix AI 优势

- **延迟低**：DB查询 + 1次LLM调用，比Kortex快3-5倍
- **确定性高**：代码路由不会"选错子代理"
- **零幻觉风险**：数据来自真实数据库查询，LLM只做格式化
- **可调试**：Java代码可断点调试
- **成本低**：本地3B模型，零API费用

### 2.5 Omnitrix AI 局限（可接受）

- 不支持多任务串联（非遗平台场景几乎不需要）
- 不支持LLM自主纠错（查询失败直接告知用户）

---

## 第三章 用户隔离机制

### 3.1 设计原则

`ich_omnitrix` 只部署 **1 个服务实例**，通过 `userId` + `sessionId` 实现逻辑隔离。每个用户体验到的是"自己专属的 AI"。

### 3.2 五维隔离

| 隔离维度 | 实现方式 | 数据存储 | 关键字段 |
|----------|---------|---------|---------|
| **对话隔离** | `ai_conversation` 按 `user_id` 区分 | MySQL | `user_id` |
| **消息隔离** | `ai_message` 通过 `conversation_id` 关联 | MySQL | `conversation_id` |
| **记忆隔离** | Redis key 按 `session_id` 区分 | Redis | `chat:session:{sessionId}` |
| **数据隔离** | 子代理查数据传入 `userId` | Dubbo RPC | 方法参数 `userId` |
| **推荐隔离** | `ai_user_behavior` 按 `user_id` 记录 | MySQL | `user_id` |
| **公共数据共享** | 非遗项目、商品列表所有用户看到一样 | 业务库 | 无需隔离 |

### 3.3 数据隔离示例

```
用户A(userId=1)问"我的订单":
  → orderService.listUserOrders(userId=1, ...) → 只返回用户A的订单

用户B(userId=2)问"我的订单":
  → orderService.listUserOrders(userId=2, ...) → 只返回用户B的订单
```

### 3.4 记忆隔离示例

```
Redis 数据结构:
  chat:session:sess_a001  → 用户A第1个对话的消息    TTL:24h
  chat:summary:sess_a001  → 用户A第1个对话的摘要    TTL:24h
  chat:session:sess_b001  → 用户B第1个对话的消息    TTL:24h
  chat:summary:sess_b001  → 用户B第1个对话的摘要    TTL:24h
  每个key独立，用户A看不到用户B的记忆
```

### 3.5 安全边界

- Controller 层从 JWT 解析 userId，**不允许**请求体传入 userId
- Service 层校验对话归属：`conversation.userId == currentUserId`
- AdminSubAgent 校验角色：`context.userRole == "admin"`

---

## 第四章 实时数据访问机制

### 4.1 核心原则

Omnitrix AI **不存储业务数据副本**，每次子代理通过 Dubbo RPC 实时查询业务数据库。

### 4.2 数据流

```
管理员新增商品 → MySQL INSERT → 数据在库
用户问AI"新商品" → Dubbo RPC → MySQL SELECT → 查到新商品 → 注入Prompt → AI回答
                    ↑ 实时查询，零延迟
```

### 4.3 各子代理数据来源

| 子代理 | Dubbo Service | 数据库 | 数据类型 |
|--------|---------------|--------|---------|
| ContentSubAgent | ContentService | content_center | 公共 |
| CommerceSubAgent | ProductService + OrderService | product_center + order_center | 混合 |
| UserSubAgent | UserService | user_center | 用户私有 |
| AdminSubAgent | 多Service聚合 | 多库 | 管理员专属 |
| KnowledgeSubAgent | 本地 Mapper | ai_service | 公共 |
| RecommendSubAgent | 本地 Mapper + ContentService + ProductService | ai_service + 业务库 | 用户私有+公共 |
| GeneralSubAgent | 无 | 无 | 纯LLM知识 |

---

## 第五章 Prompt 工程体系

### 5.1 五层体系（从 Kortex 7 层适配）

```
层级1: 主脑 System Prompt（基础角色）
  来源: ai_prompt_config表 → yml → 代码常量（三层回退）

层级2: 动态注入段（PromptAssembler每次拼接）
  + 当前时间 + 用户身份 + 历史摘要 + 查询结果

层级3: 子代理角色切换段（SubAgent.getAgentPrompt()）
  IntentRouter选中子代理后追加到Prompt尾部

层级4: 结果包装段（ResultWrapper 4种状态）
  SUCCESS/EMPTY/ERROR/NO_AUTH → 不同注入文本

层级5: 记忆摘要 Prompt（MemorySummarizer调LLM）
  消息>20条时压缩历史
```

Kortex 层级3（工具描述）和层级4（工具参数schema）被跳过，因为 Omnitrix 不走 Function Calling。

### 5.2 层级1 主脑 System Prompt 默认值

```
# Role: Omnitrix AI — 非遗文化智能助手

你是非物质文化遗产管理平台的核心智能终端 Omnitrix AI。
你专注于非遗文化的保护、传承与展示，同时为平台的文创商城和用户服务提供智能支持。

## 核心原则
1. **基于事实回答** — 当系统提供了查询结果数据时，必须基于真实数据回答，禁止编造。
2. **精准简洁** — 直接给出答案，不要套话，用自然对话语气。
3. **承认不知** — 系统未提供相关数据且你不确定时，说"我没有找到相关信息"。
4. **保留关键数据** — 回答中保留所有关键信息（名称、日期、数字、状态）。

## 回答风格
- 使用中文回答
- 专业且有温度，体现对非遗文化的尊重
- 适量使用 Markdown 分点陈述
- 禁止输出 HTML 标签
- 禁止重复用户的原始问题
- 禁止套话，禁止以"好的"、"当然"开头

## 参考数据使用规则
当消息中包含 [查询结果] 标签时：
- 该数据来自平台真实数据库，必须基于此回答
- 不要添加数据中没有的推测
- 数据为空时告知用户"未找到相关内容"
```

### 5.3 层级2 动态注入段完整结构

```
最终 System Prompt =
  [层级1基础Prompt]
  + "\n## 当前时间\n今天是 2026-03-09（星期一），当前时间 11:15。"
  + "\n## 当前用户信息\n- 用户姓名: 张三\n- 角色: 普通用户"       ← JWT解析
  + "\n## 历史对话摘要\n> 用户之前询问了京剧的历史..."              ← 条件注入
  + "\n## 当前任务模式: 非遗文化助手\n你现在专注于..."             ← 层级3，条件注入
  + "\n[查询结果]\n京剧项目数据: ..."                             ← 层级4，条件注入
```

### 5.4 层级3 七个子代理角色切换 Prompt

| 子代理 | 核心指令 |
|--------|---------|
| content_assistant | 专注非遗文化，基于查询结果回答，引导用户查看详情 |
| commerce_assistant | 专注文创商城，金额/状态精确引用，不推测价格 |
| user_assistant | 专注用户服务，只展示必要信息，引导到页面操作 |
| admin_assistant | 管理运营助手，数字精确，可给运营建议但注明"建议" |
| knowledge_assistant | 基于知识库FAQ回答，可润色但不改核心含义 |
| recommend_assistant | 个性化推荐，每条附推荐理由 |
| general_assistant | 不追加额外指令，用基础Prompt |

### 5.5 层级4 ResultWrapper 4种状态

| 状态 | 注入文本 |
|------|---------|
| SUCCESS | `[查询结果]\n{数据}` |
| EMPTY | `[系统提示: 未在平台数据库中找到相关内容，请基于你的知识回答]` |
| ERROR | `[系统提示: 数据查询服务暂时不可用，请基于你的知识尽力回答]` |
| NO_AUTH | `[系统提示: 该功能仅限管理员使用，请礼貌告知用户]` |

### 5.6 层级5 记忆摘要 Prompt

```
你是一个对话摘要专家。请将以下对话历史压缩为简洁的记忆摘要。

【处理原则】
1. 保留用户核心意图和关键信息（人名、项目名、日期、数字）
2. 保留AI已确认的事实和结论
3. 去除寒暄、重复、无关内容
4. 标记对话当前状态

【输出要求】
自然语言，不超过200字。格式：
- 用户意图: ...
- 关键信息: ...
- 当前状态: ...

【对话历史】
{history}
```

### 5.7 可管理的 Prompt 清单

| prompt_key | 说明 |
|------------|------|
| master_brain_system | 主脑基础角色 |
| content_agent_prompt | 非遗内容子代理 |
| commerce_agent_prompt | 商城子代理 |
| user_agent_prompt | 用户服务子代理 |
| admin_agent_prompt | 管理运营子代理 |
| knowledge_agent_prompt | 知识库问答子代理 |
| recommend_agent_prompt | 智能推荐子代理 |
| memory_summarizer | 记忆摘要 |
| self_evaluator | AI自评分 |
| title_generator | 对话标题生成 |

---

## 第六章 整体架构图

```
┌──────────────────────────────────────────────────────────────────┐
│                        前端层 (Vue.js)                           │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────┐ │
│  │ 用户端 AI 聊天 │  │ 管理端 AI 面板 │  │ Prompt/知识库配置       │ │
│  └──────┬───────┘  └──────┬───────┘  └───────────┬────────────┘ │
└─────────┼────────────────┼───────────────────────┼──────────────┘
          │ HTTP/SSE       │ HTTP                   │ HTTP
          ▼                ▼                        ▼
┌──────────────────────────────────────────────────────────────────┐
│                 ich_omnitrix (端口 8083)                          │
│                                                                  │
│  Controller → OrchestratorService → IntentRouter                 │
│                    │                     │                       │
│                    ▼                     ▼                       │
│              PromptAssembler        SubAgent 层                  │
│              PromptManager     ┌─────────────────────┐           │
│                    │          │ content  commerce    │           │
│                    ▼          │ user     admin       │           │
│               LlmClient      │ knowledge recommend  │           │
│              (Ollama API)    │ general              │           │
│                    │          └────────┬────────────┘           │
│                    │                   │ Dubbo RPC              │
│              Infrastructure:          │                         │
│              Memory / SSE / Telemetry  │                         │
└──────────────────┼─────────────────────┼────────────────────────┘
                   │                     │
          ┌────────▼──┐          ┌───────▼───────────┐
          │  Ollama   │          │ 业务 Provider      │
          │ localhost │          │ ContentService     │
          │  :11434   │          │ ProductService     │
          └───────────┘          │ OrderService       │
                                 │ UserService        │
     ┌──────────┐               │ SystemService      │
     │  Redis   │               └───────────────────┘
     │  :6379   │
     └──────────┘         ┌───────────┐
                          │  MySQL    │
                          │ai_service │
                          └───────────┘
```

---

## 第七章 Java 包结构

```
com.hyang.ich.omnitrix
├── OmnitrixApplication.java                    # 启动类（已存在）
├── controller/
│   ├── AiChatController.java                   # 用户端：聊天、对话管理、SSE
│   ├── AiAdminController.java                  # 管理端：Dashboard、审计、日志
│   └── AiConfigController.java                 # 管理端：Prompt CRUD、知识库 CRUD
├── orchestrator/
│   ├── OrchestratorService.java                # 核心：8步处理流程
│   ├── IntentRouter.java                       # 意图路由：关键词匹配→子代理编码
│   └── ResultWrapper.java                      # 结果包装：4种状态→Prompt注入文本
├── agent/
│   ├── SubAgent.java                           # 接口
│   ├── AgentContext.java                       # 上下文DTO
│   ├── SubAgentRegistry.java                   # 注册表
│   └── impl/
│       ├── ContentSubAgent.java                # 非遗内容
│       ├── CommerceSubAgent.java               # 文创商城
│       ├── UserSubAgent.java                   # 用户服务
│       ├── AdminSubAgent.java                  # 管理运营
│       ├── KnowledgeSubAgent.java              # 知识库FAQ
│       ├── RecommendSubAgent.java              # 智能推荐
│       └── GeneralSubAgent.java                # 通用兜底
├── infrastructure/
│   ├── llm/
│   │   ├── LlmClient.java                     # Ollama HTTP 调用
│   │   ├── LlmProperties.java                 # 配置属性
│   │   ├── LlmRequest.java                    # 请求DTO
│   │   ├── LlmResponse.java                   # 响应DTO
│   │   └── LlmStreamHandler.java              # SSE流式处理
│   ├── memory/
│   │   ├── ChatMemoryManager.java              # Redis短期+MySQL长期
│   │   ├── MemorySummarizer.java               # 摘要压缩
│   │   └── RedisChatMemoryStore.java           # Redis操作
│   ├── prompt/
│   │   ├── PromptManager.java                  # 三层回退
│   │   ├── PromptAssembler.java                # 动态拼接
│   │   └── PromptTemplate.java                 # 代码常量枚举
│   ├── telemetry/
│   │   ├── TelemetryTracer.java                # 追踪记录
│   │   └── AiSelfEvaluator.java                # LLM自评分
│   └── sse/
│       └── SseEmitterManager.java              # SSE连接管理
├── service/
│   ├── AiServiceImpl.java                      # Dubbo @DubboService
│   ├── ConversationService.java                # 对话CRUD
│   ├── KnowledgeService.java                   # 知识库CRUD+搜索
│   └── RecommendService.java                   # 推荐服务
├── entity/
│   ├── AiConversation.java
│   ├── AiMessage.java
│   ├── AiKnowledgeBase.java
│   ├── AiPromptConfig.java
│   ├── AiTraceLog.java
│   └── AiUserBehavior.java
├── mapper/
│   ├── AiConversationMapper.java
│   ├── AiMessageMapper.java
│   ├── AiKnowledgeBaseMapper.java
│   ├── AiPromptConfigMapper.java
│   ├── AiTraceLogMapper.java
│   └── AiUserBehaviorMapper.java
├── dto/
│   ├── ChatRequest.java
│   ├── ChatResponse.java
│   ├── AgentQueryResult.java
│   └── DashboardStatsVO.java
└── config/
    ├── RedisConfig.java
    ├── WebMvcConfig.java
    ├── AsyncConfig.java
    └── DubboConsumerConfig.java
```

---

*（续：第八章至第十六章见 [ICH_OMNITRIX_FINAL_PLAN_IMPL.md](./ICH_OMNITRIX_FINAL_PLAN_IMPL.md)）*

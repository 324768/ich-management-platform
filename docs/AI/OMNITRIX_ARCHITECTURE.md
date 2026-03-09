# Omnitrix AI 模块架构说明文档

> 版本: 2.0 | 更新日期: 2026-03-09 | 模块: ich_omnitrix (port 8083)

---

## 一、架构总览

Omnitrix 是非物质文化遗产管理平台的 AI 智能服务模块，采用 **三级权限 × 层次化多智能体编排** 架构。

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端入口                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────┐ │
│  │ 用户端 AI     │  │ 管理员普通 AI │  │ Ultra AI（密码保护）    │ │
│  │ OmnitrixFloat │  │ OmnitrixFloat│  │ OmnitrixFloat Ultra面板│ │
│  └──────┬───────┘  └──────┬───────┘  └──────────┬─────────────┘ │
└─────────┼──────────────────┼────────────────────┼───────────────┘
          │                  │                    │
          ▼                  ▼                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                     HTTP API 层 (Controller)                     │
│  AiChatController      AiAdminController      AiUltraController │
│  /api/ai/chat/*        /api/ai/admin/*         /api/ai/ultra/*   │
└─────────────────────────┬───────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                   OrchestratorService (编排核心)                  │
│                                                                   │
│  用户入口 ──→ IntentRouter ──→ SubAgent ──→ Prompt ──→ LLM       │
│  管理入口 ──→ AdminIntentRouter ──→ SubAgent ──→ Prompt ──→ LLM  │
│  Ultra入口 ──→ UltraTaskDecomposer(L2黑板) ──┐                   │
│              │                                ▼                   │
│              │  ┌────────────────────────────────────────┐        │
│              │  │ L2 黑板 (TaskBoard)                     │        │
│              │  │  ├─ UserAiMetaAgent ──→ L1黑板 ──→ Sub │        │
│              │  │  ├─ AdminAiMetaAgent ──→ L1黑板 ──→ Sub│        │
│              │  │  ├─ UltraSystemAgent                    │        │
│              │  │  ├─ UltraCrossUserAgent                 │        │
│              │  │  ├─ UltraUserControlAgent               │        │
│              │  │  └─ UltraAnalyticsAgent                 │        │
│              │  └────────────────────────────────────────┘        │
│              │                                                    │
│              └─→ UltraIntentRouter ──→ SubAgent ──→ Prompt ──→LLM│
│                  (简单指令快速路径)                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 二、三级权限体系

| 级别 | 入口 | 认证方式 | 可用 Agent | 记忆层级 |
|------|------|----------|-----------|---------|
| **用户端 AI** | `/api/ai/chat/*` | 用户登录 token | 用户侧 6 个 SubAgent | L1~L3 |
| **管理员普通 AI** | `/api/ai/admin/*` | 管理员登录 token | 管理侧 8 个 SubAgent | L1~L3 |
| **Ultra AI** | `/api/ai/ultra/*` | 独立密码 + Ultra token | 全部 Agent + Ultra 专属 4 个 | L1~L4 |

### 2.1 用户端 AI
- **适用角色**: 普通用户
- **功能范围**: 浏览非遗内容、文创商品搜索/购买、个人信息管理、智能推荐、知识问答
- **安全约束**: 受 `ai_enabled` 开关控制（Ultra 可远程禁用）
- **不可见**: 完全不知道 Ultra AI 的存在

### 2.2 管理员普通 AI
- **适用角色**: 后台管理员
- **功能范围**: 运营数据统计、订单管理、审批操作、内容管理、系统通知
- **安全约束**: 受普通管理员权限约束
- **不可见**: 完全不知道 Ultra AI 的存在

### 2.3 Ultra AI
- **适用角色**: 最高权限管理员
- **认证流程**: 独立密码 → SHA-256 验证 → Redis token（2h TTL） → 连续错误5次锁定30分钟
- **功能范围**: 包含用户端+管理端全部能力，外加 Ultra 专属能力（跨用户操作、用户管理、AI 开关控制、用户分析）
- **特有**: L4 系统全局记忆注入、L2 黑板多智能体编排

---

## 三、模块目录结构

```
ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/
├── OmnitrixApplication.java          # Spring Boot 启动类
├── agent/                             # 子智能体层
│   ├── AgentContext.java              #   执行上下文 (userId, sessionId, role)
│   ├── AgentUtils.java                #   代理工具方法
│   ├── SubAgent.java                  #   子智能体接口 (getCode/getName/execute)
│   ├── SubAgentRegistry.java          #   代理注册中心 (自动发现 + 动态代理)
│   ├── tool/                          #   工具调用子系统
│   │   ├── AgentTool.java             #     工具定义
│   │   ├── ToolCallResult.java        #     工具调用结果
│   │   └── ToolSelector.java          #     LLM 工具选择器
│   └── impl/                          #   具体代理实现
│       ├── ContentSubAgent.java       #     非遗文化助手 (用户+管理)
│       ├── CommerceSubAgent.java      #     文创商城助手 (用户+管理)
│       ├── UserSubAgent.java          #     个人服务助手 (用户)
│       ├── RecommendSubAgent.java     #     智能推荐助手 (用户)
│       ├── KnowledgeSubAgent.java     #     知识库问答助手 (通用)
│       ├── GeneralSubAgent.java       #     通用对话助手 (兜底)
│       ├── AdminSubAgent.java         #     管理端数据统计 (管理)
│       ├── AdminActionAgent.java      #     管理端操作执行 (管理)
│       ├── DynamicSubAgent.java       #     数据库驱动的动态代理
│       ├── UltraUserControlAgent.java #     [Ultra] 用户AI开关控制
│       ├── UltraCrossUserAgent.java   #     [Ultra] 跨用户操作
│       ├── UltraSystemAgent.java      #     [Ultra] 系统级用户管理
│       ├── UltraAnalyticsAgent.java   #     [Ultra] 用户行为分析
│       ├── UserAiMetaAgent.java       #     [L2元代理] 用户端AI入口
│       └── AdminAiMetaAgent.java      #     [L2元代理] 管理员AI入口
├── blackboard/                        # 黑板架构 (任务分解与并行执行)
│   ├── TaskNode.java                  #   任务节点 (id/agent/query/deps/status)
│   ├── TaskBoard.java                 #   任务看板 (创建/依赖/调度/收集结果)
│   ├── TaskDecomposer.java            #   L1 任务分解器 (用户/管理端)
│   └── UltraTaskDecomposer.java       #   L2 任务分解器 (Ultra 主脑)
├── orchestrator/                      # 编排层
│   ├── OrchestratorService.java       #   核心编排器 (同步/流式/Ultra全流程)
│   ├── IntentRouter.java              #   用户端意图路由
│   ├── AdminIntentRouter.java         #   管理端意图路由
│   ├── UltraIntentRouter.java         #   Ultra 意图路由 (全 Agent 可达)
│   └── ActionExecutor.java            #   操作执行器 (处理待确认操作)
├── controller/                        # HTTP API 层
│   ├── AiChatController.java          #   用户端 AI API
│   ├── AiAdminController.java         #   管理端 AI API
│   ├── AiUltraController.java         #   Ultra AI API
│   ├── AiAgentController.java         #   Agent 配置管理 API
│   └── AiConfigController.java        #   AI 全局配置 API
├── infrastructure/                    # 基础设施层
│   ├── llm/                           #   LLM 调用子系统
│   │   ├── LlmClient.java            #     LLM HTTP 客户端 (主/辅双模型)
│   │   ├── LlmStreamHandler.java      #     流式响应处理 (SSE)
│   │   ├── LlmCircuitBreaker.java     #     熔断器
│   │   ├── LlmProperties.java        #     模型配置属性
│   │   ├── LlmRequest/Response.java   #     请求/响应 DTO
│   │   ├── LlmMessageBuilder.java     #     消息构建器
│   │   └── ModelConfig.java           #     单个模型配置
│   ├── guardrails/                    #   安全护栏
│   │   ├── GuardrailsFilter.java      #     输入/输出安全过滤
│   │   ├── RateLimiter.java           #     速率限流 (Redis 滑动窗口)
│   │   └── TokenBudget.java           #     Token 预算控制
│   ├── memory/                        #   记忆子系统
│   │   ├── ChatMemoryManager.java     #     L1 短期记忆 (对话历史)
│   │   ├── RedisChatMemoryStore.java  #     Redis 历史存储
│   │   ├── MemoryExtractor.java       #     L2 记忆提取 (从对话中提取事实)
│   │   └── MemorySummarizer.java      #     对话摘要生成
│   ├── prompt/                        #   Prompt 工程
│   │   ├── PromptAssembler.java       #     四层 Prompt 组装器
│   │   ├── PromptManager.java         #     Prompt 模板管理 (DB + 缓存)
│   │   └── PromptTemplate.java        #     模板变量替换
│   ├── sse/                           #   SSE 流式推送
│   │   └── SseEmitterManager.java     #     SSE 事件管理 (chunk/agent/done/L2进度)
│   └── telemetry/                     #   可观测性
│       ├── TelemetryTracer.java       #     调用链追踪
│       ├── AiSelfEvaluator.java       #     AI 自评估
│       └── TitleGenerator.java        #     对话标题生成
├── service/                           # 业务服务层
│   ├── ConversationService.java       #   对话 CRUD 管理
│   ├── KnowledgeService.java          #   知识库检索
│   ├── UserMemoryService.java         #   L3 用户长期记忆
│   ├── SystemMemoryService.java       #   L4 系统全局记忆 (Ultra独享)
│   ├── UltraAuthService.java          #   Ultra 密码认证 + Token 管理
│   ├── RecommendService.java          #   智能推荐服务
│   └── AiServiceImpl.java            #   Dubbo 对外 AI 服务
├── entity/                            # 数据实体 (10 个表)
├── mapper/                            # MyBatis Mapper (10 个)
├── dto/                               # 数据传输对象 (11 个)
└── config/                            # 配置类
    ├── AsyncConfig.java               #   异步线程池 (aiAsyncExecutor)
    ├── DubboConsumerConfig.java        #   Dubbo 消费者配置
    ├── RedisConfig.java               #   Redis 序列化配置
    └── WebMvcConfig.java              #   CORS 跨域配置
```

---

## 四、四层记忆体系

```
┌───────────────────────────────────────────────────────────────┐
│                     Prompt 组装 (PromptAssembler)              │
│                                                                │
│  ┌─ L1: 短期记忆 ──────────────────────────────────────────┐  │
│  │  ChatMemoryManager → Redis (最近N轮对话)                 │  │
│  │  MemorySummarizer → 超出窗口自动压缩为摘要               │  │
│  │  作用域: 单次会话 | 存储: Redis (TTL 24h)                │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                │
│  ┌─ L2: 中期记忆 ──────────────────────────────────────────┐  │
│  │  MemoryExtractor → 从对话中提取事实/偏好/上下文           │  │
│  │  作用域: 跨对话 | 存储: ai_user_memory 表                │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                │
│  ┌─ L3: 长期记忆 (用户画像) ────────────────────────────────┐  │
│  │  UserMemoryService → 用户偏好/行为汇总                    │  │
│  │  作用域: 永久 | 存储: ai_user_memory 表 (type=profile)   │  │
│  │  注入: assemble(context, agent, result, summary, profile)│  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                │
│  ┌─ L4: 系统全局记忆 (Ultra 独享) ──────────────────────────┐  │
│  │  SystemMemoryService → 管理指令/策略/模式/决策/洞察       │  │
│  │  作用域: 全平台 | 存储: ai_system_memory 表              │  │
│  │  仅 Ultra 可写入 | 普通管理员可读 policy 子集             │  │
│  │  注入: assemble(..., systemMemory)                        │  │
│  └──────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────┘
```

---

## 五、层次化多智能体编排 (Hierarchical Multi-Agent Orchestration)

### 5.1 L1 黑板 —— 用户端/管理端 AI 内部

```
用户查询 → TaskDecomposer.isComplexQuery() 关键词快速判定
         ├─ 简单 → IntentRouter 路由到单个 SubAgent → 直接执行
         └─ 复杂 → TaskDecomposer.decompose() LLM JSON分解
                    → TaskBoard 创建任务+依赖
                    → SubAgentRegistry 分配 Agent
                    → 异步并行执行 (aiAsyncExecutor)
                    → TaskBoard.collectResults() 汇总
```

**L1 任务分解示例**:
```
用户: "帮我搜一下皮影戏相关的商品，顺便看看有什么非遗活动"
  → TaskDecomposer 识别为复杂查询
  → 分解为:
    [0] commerce_assistant: "搜索皮影戏相关商品"  (dependsOn: [])
    [1] content_assistant: "查看非遗活动列表"    (dependsOn: [])
  → 两个任务并行执行，结果合并后交给 LLM 生成回复
```

### 5.2 L2 黑板 —— Ultra 主脑

```
Ultra 指令 → UltraTaskDecomposer.isComplexUltraQuery() 关键词快速判定
           ├─ 简单 → UltraIntentRouter 路由到单个 Agent → 直接执行
           └─ 复杂 → UltraTaskDecomposer.decompose() LLM JSON分解
                      → TaskBoard 创建 L2 任务+依赖
                      → 分发给元代理和/或 Ultra 专属代理
                      → 异步并行执行
                      → 元代理内部再启动 L1 流程
                      → L2 TaskBoard.collectResults() 汇总
                      → LLM 生成统一回复
```

**L2 任务分解示例**:
```
Ultra: "查看在线用户，同时帮张三的购物车加入皮影戏商品，顺便看看今日运营数据"
  → UltraTaskDecomposer 识别为复杂指令
  → 分解为:
    [0] ultra_system:    "查看在线用户列表"        (dependsOn: [])
    [1] ultra_cross_user: "帮张三购物车加入皮影戏商品" (dependsOn: [])
    [2] admin_ai_meta:   "查看今日运营数据"         (dependsOn: [])
  → [0][1] Ultra 专属代理直接执行
  → [2] AdminAiMetaAgent 内部启动 L1 流程：
         → IntentRouter → admin_data_agent → 执行
  → 三路结果合并，LLM 生成统一回复
```

### 5.3 元代理 (Meta-Agent) 机制

| 元代理 | 编码 | 作用 | 内部路由 |
|--------|------|------|---------|
| `UserAiMetaAgent` | `user_ai_meta` | 将 L2 任务转化为用户端 AI 执行 | IntentRouter → 用户端 SubAgent |
| `AdminAiMetaAgent` | `admin_ai_meta` | 将 L2 任务转化为管理端 AI 执行 | IntentRouter → 管理端 SubAgent |

**关键特性**: 元代理在被 L2 黑板调用时，内部依然经过完整的 L1 流程（关键词判定 → 是否需要 L1 黑板分解 → 路由 → 执行），保证了架构的一致性。

---

## 六、SubAgent 全览

### 6.1 通用/用户端代理

| 编码 | 名称 | 功能 | 可用级别 |
|------|------|------|---------|
| `content_assistant` | 非遗文化助手 | 分类/项目/传承人/活动查询 | 用户+管理+Ultra |
| `commerce_assistant` | 文创商城助手 | 商品/购物车/订单操作 | 用户+管理+Ultra |
| `user_assistant` | 个人服务助手 | 个人信息/地址/认证 | 用户+Ultra |
| `browse_history_assistant` | 浏览历史助手 | 浏览记录查询(不可删除/按天分组) | 用户+Ultra |
| `recommend_assistant` | 智能推荐助手 | 个性化推荐 | 用户+Ultra |
| `knowledge_assistant` | 知识库问答助手 | 知识检索+RAG | 通用 |
| `general_assistant` | 通用对话助手 | 兜底闲聊 | 通用 |

### 6.2 管理端代理

| 编码 | 名称 | 功能 | 可用级别 |
|------|------|------|---------|
| `admin_data_agent` | 运营数据统计 | 销售/用户/订单/库存统计 | 管理+Ultra |
| `admin_action_agent` | 管理操作执行 | 审批/发货/上下架/通知 | 管理+Ultra |

### 6.3 Ultra 专属代理

| 编码 | 名称 | 功能 | 可用级别 |
|------|------|------|---------|
| `ultra_user_control` | 用户AI控制 | 禁用/启用用户AI功能 | Ultra |
| `ultra_cross_user` | 跨用户操作 | 代替用户操作购物车/订单 | Ultra |
| `ultra_system` | 系统管理 | 删除/封禁用户、查看在线 | Ultra |
| `ultra_analytics` | 用户分析 | 用户画像/行为分析 | Ultra |
| `ultra_browse_history` | 用户行为追踪 | 查看任意用户浏览记录/行为轨迹 | Ultra |
| `ultra_security` | 安全审计 | 操作日志/可疑行为检测/安全巡检 | Ultra |

### 6.4 L2 元代理

| 编码 | 名称 | 功能 | 可用级别 |
|------|------|------|---------|
| `user_ai_meta` | 用户端AI元代理 | L2黑板→L1用户端流程 | Ultra(L2) |
| `admin_ai_meta` | 管理员AI元代理 | L2黑板→L1管理端流程 | Ultra(L2) |

---

## 七、数据流详解

### 7.1 用户端 AI 同步聊天

```
1. AiChatController.chat(userId, sessionId, message)
2. OrchestratorService.chat()
   a. checkUserAiAccess(userId) → 检查 ai_enabled 开关
   b. guardrailsFilter.validateInput() → 安全过滤
   c. rateLimiter.check() → 速率限流
   d. conversationService.findOrCreate() → 获取/创建对话
   e. handlePendingAction() → 检查待确认操作
   f. memoryManager.loadHistory() → 加载 L1 短期记忆
   g. IntentRouter.route() → 意图路由
   h. taskDecomposer.isComplexQuery() → 判定是否需要 L1 黑板
      - 简单: 单 Agent 执行
      - 复杂: L1 黑板并行执行
   i. PromptAssembler.assemble(L1+L2+L3) → 组装 Prompt
   j. llmClient.chat() → 调用 LLM (失败自动降级到辅助模型)
   k. guardrailsFilter.sanitizeOutput() → 输出安全过滤
   l. saveAndPostProcess() → 保存消息+异步后处理
      - 提取 L2 记忆
      - 更新对话摘要
      - 更新用户画像
      - 记录遥测数据
      - 自评估
   m. 返回 ChatResponse
```

### 7.2 Ultra AI 流式聊天 (L2 黑板路径)

```
1. AiUltraController.ultraChatStream(token, sessionId, message)
   - validateToken → 验证 Ultra token
2. OrchestratorService.ultraChatStream()
   a. guardrailsFilter.validateInput()
   b. conversationService.findOrCreate() → context.userRole = "ultra"
   c. handlePendingAction()
   d. memoryManager.loadHistory()
   e. UltraTaskDecomposer.isComplexUltraQuery()
      ├─ true → decompose() → TaskBoard
      │         ├─ SSE: l2_board(started) + l2_task(pending) × N
      │         ├─ 并行执行各元代理/Ultra代理
      │         │   ├─ SSE: l2_task(running)
      │         │   ├─ 元代理内部 L1 流程...
      │         │   └─ SSE: l2_task(done/failed)
      │         ├─ SSE: l2_board(completed)
      │         └─ collectResults() → 合并结果
      └─ false → UltraIntentRouter → 单Agent快速路径
   f. PromptAssembler.assemble(L1+L2+L3+L4) → 含系统记忆
   g. llmStreamHandler.streamChat() → SSE chunk 推送
   h. SSE: done(messageId, latencyMs, model)
   i. saveAndPostProcess()
```

---

## 八、安全体系

### 8.1 多层安全

| 层级 | 组件 | 功能 |
|------|------|------|
| **输入过滤** | GuardrailsFilter | 敏感词/注入攻击/长度检测 |
| **速率限流** | RateLimiter | Redis 滑动窗口，按用户限流 |
| **Token预算** | TokenBudget | 每日/每用户 Token 消耗上限 |
| **输出审核** | GuardrailsFilter | 输出安全清洗 |
| **认证隔离** | UltraAuthService | Ultra 独立密码认证 |
| **LLM 容错** | LlmCircuitBreaker | 熔断器 + 主/辅模型自动降级 |
| **AI 开关** | ai_user_ai_config | Ultra 可远程禁用用户 AI |

### 8.2 Ultra 信息隔离

**核心原则**: 用户端 AI 和管理员普通 AI **完全不知道 Ultra AI 的存在**。

- 用户端/管理端的前端代码中 **没有任何 Ultra 相关的 API 调用、UI 组件或标识**
- L2 黑板的 SSE 事件（`l2_board`, `l2_task`）**仅在 Ultra 流式接口发送**
- 当 L2 黑板调度 `UserAiMetaAgent` 或 `AdminAiMetaAgent` 执行任务时，**元代理内部走标准 L1 流程**，不会向用户端/管理端面板发送任何信息
- Ultra 专属代理的编码（`ultra_*`）**不在用户/管理端的 IntentRouter 路由表中**
- Ultra 的 API 端点 (`/api/ai/ultra/*`) **不在用户端/管理端的 API 定义文件中**

---

## 九、数据库设计 (ai_service 库)

| 表名 | 用途 |
|------|------|
| `ai_conversation` | 对话元数据 (sessionId, userId, title, summary) |
| `ai_conversation_message` | 消息记录 (role, content, tokens, model, subAgent) |
| `ai_knowledge_base` | 知识库条目 (question, answer, category, embedding) |
| `ai_user_memory` | 用户记忆 (userId, type, key, value, confidence) |
| `ai_system_memory` | 系统全局记忆 (type, key, value, createdBy) |
| `ai_agent_config` | 动态代理配置 (name, code, prompt, routing_keywords) |
| `ai_prompt_config` | Prompt 模板配置 (key, template, variables) |
| `ai_user_ai_config` | 用户AI开关 (userId, ai_enabled, disabled_reason) |
| `ai_user_behavior` | 用户行为记录 (userId, action, target) |
| `ai_trace_log` | AI 调用链追踪日志 |

---

## 十、前端架构

### 10.1 管理端 (ich-admin-web-vue)

| 文件 | 功能 |
|------|------|
| `src/api/omnitrix.js` | API 接口定义 (普通AI + Ultra AI) |
| `src/components/OmnitrixFloat.vue` | 浮动聊天组件 (含 Ultra 面板 + L2 进度UI) |

### 10.2 用户端 (ich-user-web-vue)

| 文件 | 功能 |
|------|------|
| `src/api/omnitrix.js` | API 接口定义 (仅用户AI，无Ultra) |
| `src/components/OmnitrixFloat.vue` | 浮动聊天组件 (无Ultra相关代码) |

---

## 十一、架构优点

### 11.1 层次化编排的优势
1. **关注点分离**: L2 负责跨域任务分发，L1 负责域内任务执行，职责清晰
2. **复用性**: 元代理复用了用户端/管理端 AI 的完整 L1 流程，无需重复实现
3. **并行加速**: 独立任务可通过线程池并行执行，显著降低复杂指令的响应延迟
4. **优雅降级**: L2 分解失败自动回退到单 Agent 快速路径，不影响基本功能
5. **依赖感知**: TaskBoard 支持任务间依赖关系，后续任务可基于前置任务结果执行

### 11.2 安全架构优势
1. **权限纵深**: 三级权限体系 + Ultra 独立认证，攻击面最小化
2. **信息隔离**: 低权限层完全不知道高权限层的存在，防止信息泄露
3. **远程管控**: Ultra 可远程禁用任意用户的 AI 功能，实现紧急控制
4. **防暴力破解**: SHA-256 + 连续错误锁定机制

### 11.3 可扩展性优势
1. **SubAgent 接口标准化**: 新代理只需实现 `SubAgent` 接口 + `@Component` 注解即可自动注册
2. **动态代理**: 通过数据库配置即可创建新的 AI 代理，无需重启
3. **Prompt 模板化**: Prompt 存储在数据库，可热更新
4. **LLM 无关**: 通过 `LlmClient` 抽象，可切换任意 LLM 提供商

### 11.4 可观测性优势
1. **调用链追踪**: TelemetryTracer 记录完整的请求链路
2. **AI 自评估**: AiSelfEvaluator 对每次响应质量打分
3. **L2 实时进度**: SSE 推送黑板任务状态，前端可视化执行过程

---

## 十二、架构缺点与改进方向

### 12.1 已修复的缺点

1. **LLM 调用开销** ✅ 已优化
   - L2 分解本身需要一次 LLM 调用，复杂查询最少需要 2 次 LLM 调用（分解+生成）
   - **已改进**:
     - `IntentRouter` / `UltraIntentRouter` 新增更多关键词快速路径（浏览历史、安全审计等），减少 LLM 兜底调用
     - `UltraTaskDecomposer.ULTRA_CROSS_HINTS` 扩展了跨域关键词组合，提高本地判断准确率
     - L2 黑板使用独立线程池 `l2BoardExecutor`，避免与普通 AI 请求互相阻塞

2. **黑板分解准确性依赖 LLM** ✅ 已增强
   - **已改进**:
     - `UltraTaskDecomposer` 添加 `VALID_L2_AGENTS` 白名单校验，LLM 生成无效 agent code 时自动跳过
     - 任务数量上限截断（最多6个），防止 LLM 过度分解
     - 有效任务不足2个时自动回退单 Agent 路径

3. **无跨节点分布式支持** ⏳ 未实现（预留方向）
   - 当前并行执行基于 JVM 线程池，无法跨多个服务实例
   - **预留改进**: 引入消息队列（如 RabbitMQ）实现分布式任务分发

4. **元代理上下文传递有限** ✅ 已修复
   - **已改进**:
     - `AgentContext` 新增 `parentQuery` 字段（L2 原始指令）和 `l2Metadata` 字段（任务ID、来源Agent等）
     - `AgentContext.withL2()` 方法支持为 L2 子任务创建携带上下文的子 Context
     - `OrchestratorService.executeL2Blackboard()` 已集成，每个 L2 任务自动获得完整上下文

5. **缺乏任务取消机制** ✅ 已修复
   - **已改进**:
     - `TaskBoard` 新增 `cancel()` 方法（取消所有未完成任务）和 `cancelTask(taskId)` 方法（取消单个任务）
     - `TaskBoard` 新增 `isCancelled()` 标志位，运行中的任务可检查是否已取消
     - `OrchestratorService` 在 L2 黑板超时或异常时自动调用 `l2Board.cancel()` 取消剩余任务
     - L2 任务执行前检查 `l2Board.isCancelled()`，已取消则直接跳过

6. **测试覆盖不足** ⏳ 待补充
   - 缺少单元测试和集成测试
   - **计划**: 增加 Agent 单元测试、黑板分解测试、端到端流程测试

### 12.2 已修复的潜在风险

1. **循环依赖风险** ✅ 已修复: `UltraTaskDecomposer` 添加 `VALID_L2_AGENTS` 白名单，只允许合法的 agent code 进入黑板。同时通过 L1 `MAX_ROUNDS` 限制递归深度。
2. **线程池饱和** ✅ 已修复: `AsyncConfig` 新增 `l2BoardExecutor` 独立线程池（core=2, max=6, queue=20），与普通 AI 线程池 `aiAsyncExecutor` 完全隔离，防止 Ultra 并发请求饱和普通线程池。
3. **SSE 连接泄漏**: 已通过 `SSE_TIMEOUT` (120s) 缓解。

---

## 十三、关键配置

```yaml
# application.yml
omnitrix:
  llm:
    primary:
      api-url: https://api.example.com/v1/chat/completions
      model: deepseek-chat
      api-key: ${LLM_API_KEY}
    auxiliary:
      api-url: https://api.example.com/v1/chat/completions
      model: deepseek-chat-lite
      api-key: ${LLM_API_KEY}
  intent:
    llm-fallback: true       # 关键词未匹配时是否使用 LLM 兜底
  ultra:
    password-hash: <sha256>  # Ultra 密码的 SHA-256 哈希值
```

---

## 十四、SSE 事件协议

| 事件名 | 数据格式 | 发送场景 |
|--------|---------|---------|
| `thinking` | `string` (思维链文本) | 流式响应期间 |
| `chunk` | `string` (回复文本片段) | 流式响应期间 |
| `agent` | `{"agent":"agent_code"}` | Agent 路由完成时 |
| `l2_board` | `{"status":"started/completed","taskCount":N}` | Ultra L2 黑板启动/完成 |
| `l2_task` | `{"taskId":"T0","agent":"code","status":"pending/running/done/failed","query":"..."}` | Ultra L2 任务状态变更 |
| `done` | `{"messageId":123,"latencyMs":456,"model":"..."}` | 响应完成 |
| `error` | `{"message":"..."}` | 发生错误 |
| `error_msg` | `string` (错误信息) | 业务错误 |

> **注意**: `l2_board` 和 `l2_task` 事件 **仅在 Ultra 流式接口** 中发送，用户端和管理端 AI 的 SSE 流不包含这些事件。

# Omnitrix AI 智能中台 —— 最终实施方案（续）

> 接 [ICH_OMNITRIX_FINAL_PLAN.md](./ICH_OMNITRIX_FINAL_PLAN.md)，本文档包含第八章至第十六章。

---

## 第八章 数据库设计（ai_service 库）

### 8.1 建表 DDL

```sql
CREATE DATABASE IF NOT EXISTS `ai_service`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;
USE `ai_service`;

-- 1. 对话表
CREATE TABLE `ai_conversation` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`       BIGINT       NOT NULL              COMMENT '用户ID',
    `session_id`    VARCHAR(64)  NOT NULL              COMMENT '会话唯一标识(UUID)',
    `title`         VARCHAR(100) DEFAULT '新对话'       COMMENT '对话标题(AI自动生成)',
    `message_count` INT          DEFAULT 0             COMMENT '消息数量',
    `summary`       TEXT                               COMMENT '对话摘要(LLM自动生成)',
    `status`        TINYINT      DEFAULT 1             COMMENT '0=已删除 1=正常 2=归档',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_id` (`session_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话表';

-- 2. 消息表
CREATE TABLE `ai_message` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `conversation_id` BIGINT       NOT NULL              COMMENT '所属对话ID',
    `session_id`      VARCHAR(64)  NOT NULL              COMMENT '会话标识(冗余加速查询)',
    `role`            VARCHAR(20)  NOT NULL              COMMENT 'system/user/assistant',
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

-- 3. 知识库表
CREATE TABLE `ai_knowledge_base` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `question`      VARCHAR(500) NOT NULL              COMMENT '问题',
    `answer`        TEXT         NOT NULL              COMMENT '答案',
    `category_id`   BIGINT                             COMMENT '关联非遗分类ID(可空)',
    `keywords`      VARCHAR(500)                       COMMENT '关键词(逗号分隔)',
    `hit_count`     INT          DEFAULT 0             COMMENT '命中次数',
    `status`        TINYINT      DEFAULT 1             COMMENT '0=禁用 1=启用',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_status` (`status`),
    FULLTEXT KEY `ft_question` (`question`, `keywords`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI知识库表';

-- 4. Prompt 配置表
CREATE TABLE `ai_prompt_config` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `prompt_key`    VARCHAR(100) NOT NULL              COMMENT 'Prompt唯一标识',
    `prompt_name`   VARCHAR(100) NOT NULL              COMMENT 'Prompt名称',
    `content`       TEXT         NOT NULL              COMMENT 'Prompt内容',
    `category`      VARCHAR(50)  DEFAULT 'system'      COMMENT 'system/agent/memory',
    `description`   VARCHAR(500)                       COMMENT '说明',
    `status`        TINYINT      DEFAULT 1             COMMENT '0=禁用 1=启用',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_prompt_key` (`prompt_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI Prompt配置表';

-- 5. 追踪日志表
CREATE TABLE `ai_trace_log` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `trace_id`        VARCHAR(64)  NOT NULL              COMMENT '追踪链路ID',
    `conversation_id` BIGINT                             COMMENT '关联对话ID',
    `user_id`         BIGINT                             COMMENT '用户ID',
    `user_query`      TEXT                               COMMENT '用户原始问题',
    `intent`          VARCHAR(50)                        COMMENT '识别到的意图',
    `sub_agent`       VARCHAR(50)                        COMMENT '实际调度的子代理',
    `model`           VARCHAR(50)                        COMMENT '使用的模型',
    `input_tokens`    INT          DEFAULT 0             COMMENT '输入token数',
    `output_tokens`   INT          DEFAULT 0             COMMENT '输出token数',
    `latency_ms`      INT          DEFAULT 0             COMMENT '总耗时(毫秒)',
    `self_score`      DECIMAL(3,1)                       COMMENT 'AI自评分(0.0-10.0)',
    `status`          VARCHAR(20)  DEFAULT 'success'     COMMENT 'success/error/timeout',
    `error_message`   TEXT                               COMMENT '错误信息',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_trace_id` (`trace_id`),
    KEY `idx_conversation_id` (`conversation_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI追踪日志表';

-- 6. 用户行为表
CREATE TABLE `ai_user_behavior` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`       BIGINT       NOT NULL              COMMENT '用户ID',
    `item_id`       BIGINT                             COMMENT '关联项目/商品ID',
    `item_type`     VARCHAR(30)  NOT NULL              COMMENT 'ich_item/product/activity/post',
    `behavior_type` TINYINT      NOT NULL              COMMENT '1=浏览 2=收藏 3=购买 4=搜索 5=问AI',
    `keywords`      VARCHAR(255)                       COMMENT '搜索/提问关键词',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_item` (`item_type`, `item_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户行为记录表';
```

### 8.2 ER 关系

```
ai_conversation (user_id) 1───N ai_message (conversation_id)
       │
       └───────────────────── ai_trace_log (conversation_id)

ai_knowledge_base (独立)
ai_prompt_config  (独立)
ai_user_behavior  (user_id) ──→ 关联外部 ich_item / product 表
```

---

## 第九章 核心组件详细设计

### 9.1 OrchestratorService 8步处理流程

```
chat(Long userId, String sessionId, String userMessage):

  Step 1: 对话管理
    根据 sessionId 查找或创建 ai_conversation
    构建 AgentContext(userId, sessionId, conversationId, userRole)

  Step 2: 加载对话历史
    ChatMemoryManager.loadMessages(sessionId)
    先查 Redis → 未命中则查 MySQL → 写回 Redis

  Step 3: 意图路由
    IntentRouter.route(userMessage) → 子代理编码
    SubAgentRegistry.get(agentCode) → 子代理实例

  Step 4: 执行子代理
    subAgent.execute(userMessage, context)
    子代理内部通过 Dubbo RPC 查业务数据
    返回 AgentQueryResult(status, data, agentCode)

  Step 5: 组装 Prompt
    PromptAssembler.assemble(context, agentCode, queryResult)
    三层回退取基础Prompt → 注入时间+用户+摘要+角色段+查询结果

  Step 6: 调用 LLM
    messages = [systemPrompt, ...历史消息..., userMessage]
    LlmClient.chat(messages) → aiResponse

  Step 7: 存储 & 记忆更新
    保存 user+assistant 消息到 MySQL
    更新 Redis 记忆
    异步: 消息数>20 → MemorySummarizer
    异步: 首次消息 → 生成对话标题

  Step 8: 追踪 & 评分（异步）
    TelemetryTracer.record(...)
    AiSelfEvaluator.evaluate(...) → 打分写入 ai_trace_log
```

### 9.2 IntentRouter 路由规则

```
按优先级从高到低:

优先级1 - 管理员专属（需 userRole=="admin"）:
  关键词: 统计, 数据, 管理, 通知, 审批, 低库存, 趋势, 角色, 用户列表
  → "admin_assistant"

优先级2 - 知识库命中:
  先查 ai_knowledge_base 是否有匹配
  命中 → "knowledge_assistant"

优先级3 - 非遗内容:
  关键词: 非遗, 传承人, 非物质文化, 京剧, 皮影, 剪纸, 刺绣, 活动,
         展览, 文化, 遗产, 项目, 民间, 手工艺, 戏曲, 技艺...
  → "content_assistant"

优先级4 - 商城/订单:
  关键词: 商品, 购买, 订单, 购物车, 发货, 价格, 库存, 文创,
         付款, 收货, 退货, 物流...
  → "commerce_assistant"

优先级5 - 用户服务:
  关键词: 我的信息, 地址, 资格认证, 修改密码, 个人, 收货地址...
  → "user_assistant"

优先级6 - 推荐:
  关键词: 推荐, 有什么好看的, 类似的, 相关的, 感兴趣...
  → "recommend_assistant"

优先级7 - 兜底:
  → "general_assistant"
```

### 9.3 LlmClient Ollama 调用规格

```
POST http://localhost:11434/v1/chat/completions

请求体:
{
    "model": "qwen2.5:3b",
    "messages": [
        {"role": "system", "content": "{完整SystemPrompt}"},
        {"role": "user", "content": "上轮用户消息"},
        {"role": "assistant", "content": "上轮AI回复"},
        {"role": "user", "content": "当前用户消息"}
    ],
    "temperature": 0.7,
    "max_tokens": 2048,
    "stream": false
}

响应体:
{
    "choices": [{"message": {"role": "assistant", "content": "AI回答"}}],
    "usage": {"prompt_tokens": 350, "completion_tokens": 200}
}

流式模式 (stream:true):
  每个chunk: data: {"choices":[{"delta":{"content":"京"}}]}
  结束: data: [DONE]
```

### 9.4 ChatMemoryManager 记忆管理

```
Redis 数据结构:
  chat:session:{sessionId}  → JSON List<Message>  TTL:24h
  chat:summary:{sessionId}  → String 摘要文本      TTL:24h

读取: Redis → 未命中 → MySQL ai_message → 写回Redis
写入: 追加Redis + 异步INSERT MySQL
摘要: 消息>20条 → 取前14条 → LLM生成摘要 → 替换为1条摘要消息
```

### 9.5 AiSelfEvaluator 自评分

```
异步执行，不阻塞用户响应。

评分Prompt:
  "请对以下AI回答的质量打分，满分10分。
   评估维度：准确性、完整性、自然度。
   只返回一个数字。
   用户问题: {userQuery}
   AI回答: {aiResponse}
   分数:"

流程:
  1. 调用 LLM (temperature=0.1)
  2. 正则提取数字
  3. 写入 ai_trace_log.self_score
  4. 解析失败记 null，不影响主流程
```

---

## 第十章 七个子代理详细设计

### 10.1 SubAgent 接口

```java
public interface SubAgent {
    String getCode();           // "content_assistant"
    String getName();           // "非遗内容助手"
    String getDescription();    // "回答非遗项目相关问题"
    String getAgentPrompt();    // 角色切换Prompt
    AgentQueryResult execute(String userQuery, AgentContext context);
}
```

### 10.2 ContentSubAgent

```
编码: content_assistant | 名称: 非遗内容助手

内部技能枚举:
  SEARCH_ITEM, GET_ITEM, SEARCH_HERITAGE_MAN, SEARCH_ACTIVITY, LIST_CATEGORIES, SEARCH_POST

Dubbo调用:
  contentService.listItems(1, 5, null, keyword, 1)      → 搜索项目
  contentService.getItemById(id)                          → 项目详情
  contentService.listHeritageManByKeyword(keyword)        → 搜索传承人
  contentService.listActivities(1, 5, null, keyword, 1)   → 搜索活动
  contentService.listCategoryTree()                       → 分类树

execute()逻辑:
  1. 提取关键词 → 2. 判断查项目/传承人/活动 → 3. Dubbo查询
  4. 格式化: "非遗项目:\n- 京剧(国家级,北京) - 描述..."
  5. 返回 AgentQueryResult(SUCCESS/EMPTY, data, code)
```

### 10.3 CommerceSubAgent

```
编码: commerce_assistant | 名称: 文创商城助手

Dubbo调用:
  productService.listProducts(1, 5, null, keyword, 1)  → 搜索商品
  productService.getProductById(id)                     → 商品详情
  orderService.listUserOrders(userId, status, 1, 5)     → 用户订单 (userId隔离)
  productService.listCartItems(userId)                  → 购物车 (userId隔离)

数据隔离: 订单和购物车传入userId，商品搜索无需userId
```

### 10.4 UserSubAgent

```
编码: user_assistant | 名称: 用户服务助手

Dubbo调用:
  userService.findById(userId)                          → 用户信息
  userService.listAddresses(userId)                     → 地址列表
  userService.getQualificationByUserId(userId)          → 资格认证
  userService.hasHeritageFlag(userId)                   → 传承人标志

数据隔离: 所有查询传入userId，不暴露敏感信息
```

### 10.5 AdminSubAgent

```
编码: admin_assistant | 名称: 管理运营助手

权限校验: context.userRole != "admin" → 返回 NO_AUTH

Dubbo调用(聚合):
  contentService.countItems()                  → 非遗项目数
  userService.countUsers()                     → 用户数
  productService.countProducts()               → 商品数
  orderService.countOrders()                   → 订单数
  productService.countLowStockProducts(10)     → 低库存数
  productService.listLowStockProducts(10)      → 低库存列表
  orderService.getWeeklyOrderCounts()          → 7天趋势
  orderService.listRecentOrders(5)             → 最近订单
```

### 10.6 KnowledgeSubAgent

```
编码: knowledge_assistant | 名称: 知识库问答助手
数据来源: ai_knowledge_base 表（本地Mapper）

execute():
  1. 提取关键词
  2. SELECT WHERE keywords LIKE '%关键词%' OR MATCH AGAINST LIMIT 3
  3. 命中 → hit_count+1 → 格式化返回 SUCCESS
  4. 未命中 → 返回 EMPTY → OrchestratorService降级到GeneralSubAgent
```

### 10.7 RecommendSubAgent

```
编码: recommend_assistant | 名称: 智能推荐助手

execute():
  1. 查 ai_user_behavior (近30天, GROUP BY, ORDER BY freq DESC LIMIT 10)
  2. 提取兴趣标签
  3. contentService.listItems / productService.listProducts 查相关内容
  4. 格式化推荐列表+推荐理由
  5. 无行为数据 → 降级热门推荐
```

### 10.8 GeneralSubAgent

```
编码: general_assistant | 名称: 通用问答助手
execute(): 直接返回 AgentQueryResult(SUCCESS, null, code)
不查数据，让LLM纯粹基于模型知识回答
```

---

## 第十一章 REST API 完整设计

### 11.1 用户端 AiChatController (/api/ai)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/ai/chat | 同步聊天 |
| GET | /api/ai/chat/stream | SSE流式聊天 |
| POST | /api/ai/conversation/create | 创建新对话 |
| GET | /api/ai/conversation/list | 用户对话列表 |
| GET | /api/ai/conversation/{id} | 对话详情(含消息) |
| DELETE | /api/ai/conversation/{id} | 删除对话 |
| GET | /api/ai/recommend | AI推荐内容 |

**POST /api/ai/chat 示例:**

```json
// 请求 (userId从JWT解析，不在body)
{ "sessionId": "sess_abc123", "message": "京剧有哪些流派？" }

// 响应
{ "code": 200, "data": {
    "messageId": 42,
    "sessionId": "sess_abc123",
    "content": "京剧主要有四大流派...",
    "subAgent": "content_assistant",
    "latencyMs": 2300
} }
```

### 11.2 管理端 AiAdminController (/api/admin/ai)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/admin/ai/dashboard | AI面板统计 |
| GET | /api/admin/ai/traces | 分页追踪日志 |
| GET | /api/admin/ai/conversations | 分页所有对话 |
| GET | /api/admin/ai/conversations/{id} | 对话详情 |

**GET /api/admin/ai/dashboard 响应:**

```json
{ "code": 200, "data": {
    "totalConversations": 156,
    "totalMessages": 1240,
    "todayMessages": 23,
    "avgScore": 7.8,
    "avgLatencyMs": 1850,
    "topAgents": [
      {"agent": "content_assistant", "count": 520},
      {"agent": "knowledge_assistant", "count": 340}
    ],
    "scoreDistribution": {"excellent": 45, "good": 67, "fair": 30, "poor": 14}
} }
```

### 11.3 配置管理 AiConfigController (/api/admin/ai/config)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/admin/ai/config/prompt/list | Prompt配置列表 |
| POST | /api/admin/ai/config/prompt/save | 新增/修改Prompt |
| DELETE | /api/admin/ai/config/prompt/{id} | 删除Prompt |
| GET | /api/admin/ai/config/knowledge/list | 分页知识库 |
| POST | /api/admin/ai/config/knowledge/save | 新增/修改知识 |
| DELETE | /api/admin/ai/config/knowledge/{id} | 删除知识 |

---

## 第十二章 配置文件

```yaml
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
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
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
omnitrix:
  llm:
    api-url: http://localhost:11434/v1/chat/completions
    model: qwen2.5:3b
    timeout-seconds: 60
    max-tokens: 2048
    temperature: 0.7
  memory:
    max-messages: 20
    redis-ttl-hours: 24
    summary-enabled: true
  evaluation:
    enabled: true
    async: true
  prompts:
    master-brain-system: ""
logging:
  level:
    root: info
    com.hyang.ich.omnitrix: debug
```

---

## 第十三章 Dubbo 服务消费

```java
@Configuration
public class DubboConsumerConfig {
    @DubboReference(check = false) private ContentService contentService;
    @DubboReference(check = false) private ProductService productService;
    @DubboReference(check = false) private UserService userService;
    @DubboReference(check = false) private OrderService orderService;
    @DubboReference(check = false) private SystemService systemService;

    @Bean public ContentService contentService() { return contentService; }
    @Bean public ProductService productService() { return productService; }
    @Bean public UserService userService()       { return userService; }
    @Bean public OrderService orderService()     { return orderService; }
    @Bean public SystemService systemService()   { return systemService; }
}
```

`check=false` 确保 Omnitrix 启动时即使业务 Provider 未启动也不报错。

---

## 第十四章 前端页面规划

### 14.1 用户端 (ich-user-web-vue)

```
src/views/ai/
├── AiChat.vue              # AI 聊天主页面
│   ├── 左侧: 对话列表
│   ├── 中间: 聊天消息区 (Markdown渲染)
│   └── 底部: 输入框+发送按钮

API调用:
  POST /api/ai/chat
  GET  /api/ai/chat/stream (SSE)
  POST /api/ai/conversation/create
  GET  /api/ai/conversation/list
  GET  /api/ai/conversation/{id}
  DELETE /api/ai/conversation/{id}
```

### 14.2 管理端 (ich-admin-web-vue)

```
src/views/ai/
├── AiDashboard.vue         # AI面板 (ECharts图表)
├── AiConversationList.vue  # 对话审计
├── AiKnowledgeList.vue     # 知识库管理
├── AiPromptList.vue        # Prompt配置管理
└── AiTraceList.vue         # 追踪日志
```

---

## 第十五章 分阶段实施路线

### Phase 1：最小闭环（最高优先级）

```
目标: POST /api/ai/chat 能跑通完整请求

任务清单:
  [ ] 创建 ai_service 数据库 + 6 张表
  [ ] LlmProperties + LlmClient (Ollama HTTP)
  [ ] LlmRequest + LlmResponse DTO
  [ ] PromptTemplate 代码常量
  [ ] PromptManager (Phase1 只走代码常量)
  [ ] PromptAssembler (Phase1 只加时间)
  [ ] AgentContext DTO
  [ ] SubAgent 接口 + AgentQueryResult DTO
  [ ] GeneralSubAgent
  [ ] SubAgentRegistry (Phase1 只有 General)
  [ ] IntentRouter (Phase1 全走 General)
  [ ] ResultWrapper
  [ ] AiConversation + AiMessage 实体
  [ ] AiConversationMapper + AiMessageMapper + XML
  [ ] ConversationService
  [ ] OrchestratorService (Phase1 不含异步评分/摘要)
  [ ] ChatRequest + ChatResponse DTO
  [ ] AiChatController
  [ ] WebMvcConfig (CORS)
  [ ] RedisConfig
  [ ] application.yml 更新

验收:
  ✓ POST /api/ai/chat 收到AI回复
  ✓ 对话和消息存入 MySQL
  ✓ Prompt 从代码常量加载
```

### Phase 2：子代理体系 + 知识库

```
目标: 意图路由到专业子代理，返回真实业务数据

任务清单:
  [ ] DubboConsumerConfig
  [ ] ContentSubAgent
  [ ] CommerceSubAgent
  [ ] UserSubAgent
  [ ] AdminSubAgent (含权限校验)
  [ ] IntentRouter 完善关键词规则
  [ ] SubAgentRegistry 注册全部子代理
  [ ] PromptAssembler 添加用户信息+子代理角色段
  [ ] AiKnowledgeBase 实体 + Mapper + XML
  [ ] KnowledgeService
  [ ] KnowledgeSubAgent
  [ ] AiConfigController (知识库CRUD接口)
  [ ] AiServiceImpl (Dubbo @DubboService)

验收:
  ✓ 问非遗问题 → ContentSubAgent → 真实数据回答
  ✓ 问商品问题 → CommerceSubAgent → 真实商品数据
  ✓ 问"我的订单" → CommerceSubAgent → 该用户订单
  ✓ 知识库命中优先使用FAQ答案
  ✓ 管理员问统计 → AdminSubAgent → 聚合数据
  ✓ 普通用户问统计 → NO_AUTH 提示
```

### Phase 3：记忆与流式

```
目标: 多轮对话 + SSE流式输出

任务清单:
  [ ] RedisChatMemoryStore
  [ ] ChatMemoryManager (Redis短期+MySQL长期)
  [ ] MemorySummarizer (摘要压缩)
  [ ] PromptAssembler 添加摘要注入
  [ ] LlmStreamHandler (SSE流式)
  [ ] SseEmitterManager
  [ ] AiChatController 添加 GET /chat/stream
  [ ] 对话标题自动生成 (异步LLM)

验收:
  ✓ 多轮对话保持上下文
  ✓ 超过20条消息自动摘要
  ✓ SSE流式逐字显示
  ✓ 首次消息后对话标题自动生成
```

### Phase 4：评分、运营与推荐

```
目标: AI质量追踪 + 管理后台 + 推荐

任务清单:
  [ ] AsyncConfig (@EnableAsync + 线程池)
  [ ] AiTraceLog 实体 + Mapper + XML
  [ ] TelemetryTracer
  [ ] AiSelfEvaluator
  [ ] AiPromptConfig 实体 + Mapper + XML
  [ ] PromptManager 完善 DB→yml→代码 三层回退
  [ ] AiConfigController (Prompt CRUD接口)
  [ ] AiUserBehavior 实体 + Mapper + XML
  [ ] RecommendService
  [ ] RecommendSubAgent
  [ ] DashboardStatsVO
  [ ] AiAdminController (Dashboard + 日志)
  [ ] AiChatController 添加 GET /recommend

验收:
  ✓ 每次AI回答自动打分记录
  ✓ Dashboard 显示统计数据
  ✓ 管理后台修改Prompt立即生效
  ✓ 推荐功能基于用户行为
  ✓ 追踪日志可分页查询
```

---

## 第十六章 提升分析：还能做什么

以下是在 Phase 1-4 完成后，可以考虑的提升方向。按投入产出比排序。

### 16.1 高价值低成本（推荐做）

#### ① 敏感词过滤

```
投入: 1个Service类 + 1张表
效果: 防止AI输出不当内容，答辩时体现安全意识

实现:
  - ai_sensitive_word 表存敏感词
  - SensitiveWordFilter 在AI回答返回前做替换/拦截
  - 管理后台可维护敏感词列表
  - 你的功能列表中已经列出"敏感词库管理与配置"
```

#### ② 热门问题统计

```
投入: 几个SQL查询
效果: Dashboard增加"热门问题TOP10"图表

实现:
  - 从 ai_message 表统计用户高频问题
  - 或从 ai_trace_log 统计 intent 分布
  - 管理员可据此优化知识库
```

#### ③ 对话标题智能生成优化

```
投入: 已在Phase3中，但可优化Prompt
效果: 对话列表更易识别

优化:
  标题生成Prompt:
  "用2-8个字概括以下对话的主题，只输出标题：
   用户: {首条消息}"
```

#### ④ 快捷问题/猜你想问

```
投入: 前端改动 + 1个接口
效果: 降低用户使用门槛

实现:
  - 新对话时显示 4-6 个预设问题按钮
  - 来源: 知识库热门条目 + 固定预设
  - GET /api/ai/suggestions → 返回推荐问题列表
```

### 16.2 中等价值中等成本（可选做）

#### ⑤ 用户反馈机制

```
投入: 1个接口 + 前端改动
效果: 用户可对AI回答点赞/踩，形成质量反馈闭环

实现:
  - ai_message 表增加 feedback 字段 (1=有用 -1=无用 0=未评价)
  - POST /api/ai/message/{id}/feedback
  - Dashboard 展示用户满意度
  - 与 AI 自评分对比，验证自评分可靠性
```

#### ⑥ 行为数据自动采集

```
投入: 前端埋点 + 1个接口
效果: RecommendSubAgent 数据更丰富

实现:
  - 用户浏览非遗项目详情 → 前端调 POST /api/ai/behavior/track
  - 用户收藏商品 → 同上
  - 存入 ai_user_behavior 表
  - 推荐质量提升
```

#### ⑦ 相关内容关联

```
投入: SubAgent返回值扩展
效果: AI回答非遗问题时，底部附带相关项目卡片

实现:
  - ChatResponse 增加 relatedItems 字段
  - ContentSubAgent 查到数据后，额外返回 relatedItems
  - 前端在AI回答下方渲染项目卡片（可点击跳转）
```

### 16.3 高价值高成本（答辩后可做）

#### ⑧ 多模态：图片识别非遗项目

```
投入: 需要支持图片的LLM模型
效果: 用户上传非遗图片，AI识别并介绍

当前限制: qwen2.5:3b 不支持图片输入
未来方案: 升级到支持视觉的模型 (如 qwen-vl)
```

#### ⑨ 多任务串联

```
投入: OrchestratorService 重构
效果: 支持"帮我查京剧传承人，然后推荐类似项目"

实现思路:
  - IntentRouter 返回多个子代理编码
  - OrchestratorService 按顺序执行，上一步结果传给下一步
  - 需要更强模型来整合多步结果
当前限制: 3B模型整合能力有限，暂不做
```

#### ⑩ 向量检索增强知识库

```
投入: 引入向量数据库 (Milvus/Chroma)
效果: 知识库搜索从关键词匹配升级到语义匹配

当前限制: 增加部署复杂度，毕设不需要
未来方案: 非遗项目描述向量化，实现语义搜索
```

### 16.4 提升优先级排序

```
Phase 4 完成后的推荐实施顺序:

必做 (答辩前):
  ① 敏感词过滤        ← 功能列表已列出，必须实现
  ④ 快捷问题/猜你想问  ← 大幅提升用户体验，前端改动小

建议做:
  ② 热门问题统计       ← Dashboard 锦上添花
  ③ 标题生成优化       ← 成本极低
  ⑤ 用户反馈机制       ← 质量闭环

可选做:
  ⑥ 行为数据自动采集
  ⑦ 相关内容关联

答辩后:
  ⑧⑨⑩
```

---

## 附录A：技术选型确认

| 层次 | 技术 | 说明 |
|------|------|------|
| LLM | Ollama + qwen2.5:3b | 本地部署，免费 |
| HTTP客户端 | Spring RestTemplate | 调用Ollama API |
| 流式输出 | Spring SseEmitter | 原生支持 |
| 缓存 | Spring Data Redis + Lettuce | pom.xml已有 |
| 数据库 | MySQL 8.0 + MyBatis | 现有技术栈 |
| 服务调用 | Apache Dubbo | 现有技术栈 |
| 注册中心 | ZooKeeper | 现有技术栈 |
| JSON | Jackson + Gson | pom.xml已有 |
| 异步 | Spring @Async | 自评分/摘要等 |

## 附录B：服务端口汇总

| 服务 | HTTP端口 | Dubbo端口 |
|------|----------|-----------|
| ich-user-provider | 8081 | 20881 |
| ich-content-provider | 8082 | 20882 |
| ich-omnitrix | 8083 | 20883 |
| ich-user-web | 8090 | - |
| ich-admin-web | 8091 | - |
| Ollama | 11434 | - |
| ZooKeeper | 2181 | - |
| Redis | 6379 | - |
| MySQL | 3306 | - |

## 附录C：与现有系统关系

```
ich_interface ── 所有Dubbo接口定义(含AiService)
     │
     ├── ich_user_provider ── 用户/订单/系统服务实现
     │      ↑ Dubbo RPC
     ├── ich_content_provider ── 内容/商品服务实现
     │      ↑ Dubbo RPC
     ├── ich_omnitrix ── AI智能中台 ◀── 本文档
     │      ├─ 消费 ContentService, ProductService,
     │      │       UserService, OrderService, SystemService
     │      ├─ 暴露 AiService (Dubbo, 给其他模块用)
     │      ├─ 暴露 REST API (给前端用)
     │      └─ 调用 Ollama LLM (HTTP)
     ├── ich_user_web ── 用户端Web (可调AiService)
     └── ich_admin_web ── 管理端Web (可调AiService)

前端:
  ich-user-web-vue ── AI聊天页面
  ich-admin-web-vue ── AI Dashboard/知识库/Prompt配置
```

---

> **本方案审核通过后，按 Phase 1 → 2 → 3 → 4 顺序执行。**
> **每个 Phase 有独立验收标准，通过后再进入下一阶段。**

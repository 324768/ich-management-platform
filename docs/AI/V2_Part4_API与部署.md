# Part 4: API、数据库、配置与部署

## 19. Ultra 超级管理员模式

### 19.1 认证流程

```
POST /api/ai/ultra/auth
Body: { "password": "xxx" }

→ UltraAuthService.verify(password)
→ 成功: 返回 { "ultraToken": "uuid-token", "expiresIn": 3600 }
→ 失败: 返回错误信息
→ 3次连续失败: 锁定30分钟

后续请求:
Header: X-Ultra-Token: {ultraToken}
→ AiUltraController 校验 token 有效性
```

### 19.2 Ultra AI访问控制

Ultra可以控制任意用户的AI使用权限:

```java
// OrchestratorService
private String checkUserAiAccess(Long userId) {
    AiUserAiConfig config = configMapper.selectByUserId(userId);
    if (config != null && config.getEnabled() == 0) {
        return "您的AI功能已被管理员禁用。原因: " + config.getDisableReason();
    }
    return null; // 允许访问
}
```

### 19.3 V1 Ultra L2黑板

Ultra使用两级黑板架构:
- **L2黑板**: UltraTaskDecomposer分解为元代理任务
- **元代理**(UserAiMetaAgent/AdminAiMetaAgent): 内部启动完整L1子流程
- 独立线程池 `l2BoardExecutor`(core=2,max=6)隔离

### 19.4 V2 Ultra

UltraTools(536行)合并了原7个Agent的全部功能为@Tool方法，由LLM自动路由，无需L2黑板。

---

## 20. Controller API 接口参考

### 20.1 AiChatController (`/api/ai`)

| Method | Path | 参数 | 描述 |
|--------|------|------|------|
| POST | /chat | userId, sessionId, message (Body) | 同步聊天 |
| GET | /chat/stream | userId, sessionId, message (Query) | SSE流式聊天 |
| GET | /chat/regenerate | userId, sessionId (Query) | 重新生成最后回复(SSE) |
| POST | /conversations | userId (Query), title (Body) | 创建对话 |
| GET | /conversations | userId (Query), page, size | 对话列表(分页) |
| GET | /conversations/{id} | userId (Query) | 对话详情+消息列表 |
| DELETE | /conversations/{id} | userId (Query) | 软删除对话 |
| PUT | /conversations/{id}/title | userId (Query), title (Body) | 修改标题 |
| GET | /conversation/{id}/export | userId (Query) | 导出Markdown格式 |

**用户ID解析**: 优先从请求参数获取，回退到JWT Token解析。

### 20.2 AiAdminController (`/api/admin/ai`)

| Method | Path | 参数 | 描述 |
|--------|------|------|------|
| POST | /chat | adminId, sessionId, message | 管理员同步聊天 |
| GET | /chat/stream | adminId, sessionId, message | 管理员流式聊天 |
| GET | /health | adminId | 健康检查 |
| GET | /dashboard/stats | — | 仪表盘统计 |

**健康检查返回**:
```json
{
  "circuitBreaker": "CLOSED",
  "rateLimiterRemaining": 12,
  "tokenBudgetRemaining": 45000,
  "isDegraded": false
}
```

### 20.3 AiUltraController (`/api/ai/ultra`)

| Method | Path | Header | 描述 |
|--------|------|--------|------|
| POST | /auth | — | Ultra密码认证 |
| POST | /chat | X-Ultra-Token | Ultra同步聊天 |
| GET | /chat/stream | X-Ultra-Token | Ultra流式聊天 |

### 20.4 AiSkillController (`/api/admin/ai/skills`)

| Method | Path | 描述 |
|--------|------|------|
| GET | / | 获取所有Skill列表 |
| PUT | /{id}/enable | 启用Skill |
| PUT | /{id}/disable | 禁用Skill |
| PUT | /batch | 批量更新Skill状态 |

### 20.5 AiAgentController (`/api/admin/ai/agents`)

| Method | Path | 描述 |
|--------|------|------|
| GET | / | Agent配置列表(分页) |
| GET | /{id} | Agent配置详情 |
| PUT | /{id} | 更新Agent配置 |
| PUT | /{id}/enable | 启用/禁用Agent |

### 20.6 AiConfigController (`/api/admin/ai/config`)

| Method | Path | 描述 |
|--------|------|------|
| GET | /prompts | Prompt配置列表(分页) |
| PUT | /prompts/{id} | 更新Prompt配置 |
| GET | /system-memory | 系统记忆列表 |
| POST | /system-memory | 新增系统记忆 |
| PUT | /system-memory/{id} | 更新系统记忆 |
| DELETE | /system-memory/{id} | 删除系统记忆 |

---

## 21. Entity 与数据库 Schema

### 21.1 ai_conversation (对话表)

```sql
CREATE TABLE ai_conversation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(64) NOT NULL UNIQUE COMMENT '会话ID(UUID)',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    title VARCHAR(100) DEFAULT '新对话' COMMENT '对话标题',
    summary TEXT COMMENT '对话摘要',
    message_count INT DEFAULT 0 COMMENT '消息数(原子递增)',
    status TINYINT DEFAULT 1 COMMENT '1有效 0已删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_session_id (session_id)
);
```

### 21.2 ai_message (消息表)

```sql
CREATE TABLE ai_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL COMMENT 'user/assistant/system',
    content TEXT NOT NULL,
    tokens INT DEFAULT 0 COMMENT '输出Token数',
    model VARCHAR(50) COMMENT '使用的模型名',
    agent_code VARCHAR(50) COMMENT '处理Agent标识',
    latency_ms INT COMMENT '响应延迟(ms)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conversation_id (conversation_id),
    INDEX idx_session_id_role (session_id, role)
);
```

**消息数原子递增** (保存消息时):
```sql
UPDATE ai_conversation SET message_count = message_count + 1 WHERE id = #{conversationId}
```

### 21.3 ai_user_memory (用户长期记忆表)

```sql
CREATE TABLE ai_user_memory (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    memory_type VARCHAR(20) NOT NULL COMMENT 'preference/interest/fact/decision/lesson',
    memory_key VARCHAR(100) NOT NULL COMMENT '记忆标题',
    memory_value VARCHAR(500) NOT NULL COMMENT '记忆内容',
    confidence DECIMAL(3,2) DEFAULT 0.50 COMMENT '置信度 0.00-1.00',
    source VARCHAR(20) DEFAULT 'conversation' COMMENT 'conversation/behavior/manual',
    hit_count INT DEFAULT 0 COMMENT '命中次数(用于衰减)',
    status TINYINT DEFAULT 1 COMMENT '1活跃 0停用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_key (user_id, memory_key),
    INDEX idx_user_id (user_id),
    INDEX idx_user_type (user_id, memory_type)
);
```

### 21.4 ai_user_behavior (用户行为表)

```sql
CREATE TABLE ai_user_behavior (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    behavior_type VARCHAR(30) COMMENT '行为类型',
    keywords VARCHAR(200) COMMENT '兴趣关键词',
    target_id BIGINT COMMENT '目标对象ID',
    target_type VARCHAR(30) COMMENT '目标类型',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at)
);
```

### 21.5 ai_user_ai_config (用户AI配置表)

```sql
CREATE TABLE ai_user_ai_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL UNIQUE,
    enabled TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
    disable_reason VARCHAR(200) COMMENT 'Ultra禁用原因',
    disabled_by BIGINT COMMENT '禁用操作人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### 21.6 ai_skill_config (Skill配置表)

```sql
CREATE TABLE ai_skill_config (
    id VARCHAR(50) PRIMARY KEY COMMENT 'Skill标识',
    name VARCHAR(50) NOT NULL,
    description VARCHAR(200),
    system_prompt TEXT COMMENT 'Skill系统提示词',
    keywords VARCHAR(500) COMMENT '触发关键词(逗号分隔)',
    enabled TINYINT DEFAULT 1,
    sort_order INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### 21.7 ai_knowledge_base (知识库表)

```sql
CREATE TABLE ai_knowledge_base (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    question VARCHAR(500) NOT NULL COMMENT '问题',
    answer TEXT NOT NULL COMMENT '答案',
    category VARCHAR(50) COMMENT '分类',
    keywords VARCHAR(200) COMMENT '关键词',
    hit_count INT DEFAULT 0,
    status TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### 21.8 ai_agent_config (Agent配置表)

```sql
CREATE TABLE ai_agent_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    agent_code VARCHAR(50) NOT NULL UNIQUE,
    agent_name VARCHAR(50),
    description VARCHAR(200),
    agent_prompt TEXT,
    enabled TINYINT DEFAULT 1,
    sort_order INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### 21.9 ai_prompt_config (Prompt配置表)

```sql
CREATE TABLE ai_prompt_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    prompt_key VARCHAR(50) NOT NULL UNIQUE,
    prompt_name VARCHAR(50),
    content TEXT NOT NULL,
    description VARCHAR(200),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### 21.10 ai_system_memory (系统记忆表)

```sql
CREATE TABLE ai_system_memory (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    memory_key VARCHAR(100) NOT NULL UNIQUE,
    memory_value TEXT NOT NULL,
    status TINYINT DEFAULT 1 COMMENT '1活跃 0停用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### 21.11 ai_trace_log (链路追踪表)

```sql
CREATE TABLE ai_trace_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    trace_id VARCHAR(64) NOT NULL UNIQUE,
    conversation_id BIGINT,
    user_id BIGINT,
    user_message TEXT,
    intent_code VARCHAR(50),
    agent_code VARCHAR(50),
    model VARCHAR(50),
    input_tokens INT DEFAULT 0,
    output_tokens INT DEFAULT 0,
    latency_ms INT DEFAULT 0,
    self_eval_score DECIMAL(3,1) COMMENT 'AI自评分(1-10)',
    cost DECIMAL(10,6) COMMENT '成本(元)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_conversation_id (conversation_id),
    INDEX idx_created_at (created_at)
);
```

### 21.12 ai_trace_span (追踪Span表)

```sql
CREATE TABLE ai_trace_span (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    trace_id VARCHAR(64) NOT NULL,
    span_type VARCHAR(20) COMMENT 'REQUEST/AGENT/LLM/TOOL',
    start_ms INT,
    end_ms INT,
    detail TEXT,
    INDEX idx_trace_id (trace_id)
);
```

---

## 22. 配置参考

### 22.1 application.yml 完整配置

```yaml
omnitrix:
  llm:
    # 单模型配置(向后兼容)
    api-url: https://api.siliconflow.cn/v1/chat/completions
    model: deepseek-ai/DeepSeek-V3
    api-key: ${OMNITRIX_API_KEY}
    timeout-seconds: 120
    max-tokens: 2048
    temperature: 0.7
    max-context-tokens: 3000
    langchain4j-enabled: false    # ★ V2开关

    # 双模型配置(推荐)
    primary:
      api-url: https://api.siliconflow.cn/v1/chat/completions
      model: deepseek-ai/DeepSeek-V3
      api-key: ${OMNITRIX_API_KEY}
      timeout-seconds: 120
      max-tokens: 2048
      temperature: 0.7
    auxiliary:
      api-url: https://api.siliconflow.cn/v1/chat/completions
      model: deepseek-ai/DeepSeek-V3
      api-key: ${OMNITRIX_API_KEY}
      timeout-seconds: 60
      max-tokens: 1024
      temperature: 0.3

spring:
  redis:
    host: localhost
    port: 6379
    database: 0
  datasource:
    url: jdbc:mysql://localhost:3306/ich_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: ${DB_PASSWORD}

dubbo:
  consumer:
    check: false
    timeout: 5000
    retries: 1
  registry:
    address: nacos://localhost:8848

mybatis:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.hyang.ich.omnitrix.entity
```

### 22.2 Redis Key 清单

| Key格式 | 用途 | TTL |
|---------|------|-----|
| `omnitrix:session:{sessionId}` | 会话消息(Hash) | 7天 |
| `omnitrix:list:{sessionId}` | 消息列表(List) | 7天 |
| `omnitrix:summary:{sessionId}` | 对话摘要 | 7天 |
| `omnitrix:last_agent:{sessionId}` | 最后Agent标识 | 30分钟 |
| `omnitrix:pending:{sessionId}` | 待确认操作 | 5分钟 |
| `omnitrix:user_memory:{userId}` | 用户画像缓存 | 30分钟 |
| `ai:rate:{userId}` | 限流计数器 | 60秒 |
| `ai:token_budget:{userId}:{date}` | Token日预算 | 25小时 |

### 22.3 线程池配置

| 线程池 | Core | Max | Queue | 策略 | 用途 |
|--------|------|-----|-------|------|------|
| aiAsyncExecutor | 4 | 10 | 100 | CallerRunsPolicy | 通用AI异步任务 |
| l2BoardExecutor | 2 | 6 | 20 | CallerRunsPolicy | Ultra L2黑板专用 |

---

## 23. 部署与运维指南

### 23.1 环境要求

- JDK 17+
- MySQL 8.x
- Redis 6.x+
- Nacos (Dubbo注册中心)
- Maven 3.8+

### 23.2 构建命令

```bash
cd ich_parent
mvn clean package -pl ich_omnitrix -am -DskipTests
```

### 23.3 启动命令

```bash
java -jar ich_omnitrix/target/ich_omnitrix.jar \
  --spring.profiles.active=prod \
  --OMNITRIX_API_KEY=sk-xxx \
  --DB_PASSWORD=xxx
```

### 23.4 数据库初始化

1. 执行 `resources/db/V1__init.sql` 创建基础表
2. 执行 `resources/db/V2__add_knowledge_base.sql`
3. 执行 `resources/db/V3__add_trace_tables.sql`
4. 执行 `resources/db/V4__add_user_memory.sql`

### 23.5 默认Skill初始化

系统首次启动时，`SkillPromptConfig` 的 `@PostConstruct` 会检测DB是否为空，为空则自动插入7个默认Skill。

### 23.6 健康监控

- `GET /api/admin/ai/health` 监控熔断器/限流/预算状态
- 日志关键字监控: `限流`, `熔断`, `Token预算耗尽`, `降级`
- Redis监控: 关注 `ai:rate:*` 和 `ai:token_budget:*` key数量

### 23.7 V1→V2 切换步骤

1. 确保所有@Tool类和ToolRegistrationConfig已部署
2. 修改配置: `omnitrix.llm.langchain4j-enabled: true`
3. 重启服务
4. 监控日志中 `langchain4j_user/admin/ultra` agentCode
5. 如出现问题: 改回 `false` 并重启即可回滚

---

## 24. 架构分析与最佳实践

### 24.1 架构优势

| 优势 | 说明 |
|------|------|
| **双模式渐进升级** | V1/V2 Feature Flag切换，零风险回滚 |
| **企业级可靠性** | 熔断器+限流+预算+安全护栏+主辅降级 |
| **多层记忆体系** | L1会话→L2摘要→L3画像→L4系统，对话质量逐步提升 |
| **角色隔离** | User/Admin/Ultra 三套完整路径，权限清晰 |
| **工具动态注册** | ToolRegistry按角色注册，新增工具只需添加@Tool类 |
| **Skill热插拔** | DB配置Skill，无需重启即可启用/禁用 |
| **全链路遥测** | TraceLog+Span+自评+成本追踪 |
| **原子化Redis操作** | RPUSH追加+MULTI/EXEC替换+Lua限流，防数据丢失 |

### 24.2 架构劣势与改进方向

| 劣势 | 改进方向 |
|------|---------|
| OrchestratorService过大(1758行) | 拆分为ChatOrchestrator+StreamOrchestrator+PostProcessor |
| V2流式为伪流式(分句推送) | 使用LangChain4j TokenStream实现真流式 |
| Token估算粗略 | 接入tiktoken精确计算 |
| 单节点限流 | 升级为Redis Cluster分布式限流 |
| 无A/B测试框架 | 添加实验分组，对比V1/V2效果 |
| Ultra密码认证简单 | 升级为TOTP/OAuth2 |
| 记忆无衰减清理 | 添加定时任务清理低hit_count记忆 |

### 24.3 性能优化建议

1. **LLM调用**: 开启响应缓存(已实现5min TTL)，高频问题命中率可达15-20%
2. **Redis**: 使用Pipeline批量操作，减少RTT
3. **Dubbo超时**: 已设置5s超时+1次重试，生产环境可按服务调整
4. **线程池**: 监控queue使用率，超过80%考虑扩容
5. **Token预算**: 按用户等级差异化配置(VIP用户10万/天)

### 24.4 适用场景

- 垂直领域企业级AI助手(非遗/文旅/电商)
- 需要多角色权限隔离的AI系统
- 需要多Agent协作的复杂任务场景
- 需要渐进式从传统架构迁移到Function Calling的系统

### 24.5 复现指南

1. 创建Maven多模块项目，定义ich_interface(Dubbo接口)和ich_omnitrix(AI模块)
2. 按第3节文件清单创建所有包和类
3. 按第21节创建数据库表
4. 按第22节配置application.yml(关键: LLM API Key)
5. 先实现V1路径: SubAgent → IntentRouter → OrchestratorService(doChatSync)
6. 再实现基础设施: LlmClient → GuardrailsFilter → RateLimiter → RedisChatMemoryStore
7. 最后实现V2路径: @Tool类 → ToolRegistry → MasterBrainFactory → doChatSyncV2
8. 设置 `langchain4j-enabled: true` 启用V2
9. 按第23节部署运行

---

**文档版本**: V2.0
**最后更新**: 2025年
**适用模块**: ich_omnitrix (Omnitrix AI Module)

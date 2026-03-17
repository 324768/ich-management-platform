# Part 2: Agent 与 Tool 系统

## 5. V1 SubAgent 系统

### 5.1 SubAgent 接口

```java
public interface SubAgent {
    String getCode();
    String getName();
    String getDescription();
    String getAgentPrompt();
    AgentQueryResult execute(String userQuery, AgentContext context);
}
```

### 5.2 AgentContext

```java
public class AgentContext {
    private Long userId;
    private String sessionId;
    private Long conversationId;
    private String role;            // "user"/"admin"/"ultra"
    private String userMessage;
    private long agentDurationMs;
    private String l2TaskQuery;     // Ultra L2黑板扩展
    private String l2TaskId;
    private String l2AgentCode;
}
```

### 5.3 AgentQueryResult

```java
public class AgentQueryResult {
    public enum Status { SUCCESS, EMPTY, ERROR, ACTION_PROPOSED }
    private Status status;
    private String data;
    private String agentCode;
    private PendingAction action;

    public String toPromptInjection() {
        return "## 查询结果\n" + data;
    }
}
```

### 5.4 SubAgentRegistry

自动注入所有SubAgent实现，`getOrDefault(code)` 降级到 `general_assistant`。

### 5.5 Agent 实现列表

**用户端 (7个)**:

| Code | 类 | 功能 |
|------|---|------|
| content_assistant | ContentSubAgent(23KB) | 非遗项目/传承人/活动搜索+互动 |
| commerce_assistant | CommerceSubAgent(24KB) | 商品/购物车/订单 |
| user_assistant | UserSubAgent(12KB) | 用户信息/地址/通知 |
| knowledge_assistant | KnowledgeSubAgent(2.5KB) | 知识库FAQ(RAG+Reranking) |
| recommend_assistant | RecommendSubAgent(6KB) | 个性化推荐 |
| browse_history_agent | BrowseHistorySubAgent(11KB) | 浏览历史 |
| general_assistant | GeneralSubAgent(0.8KB) | 通用兜底 |

**管理端 (2个)**:

| Code | 类 | 功能 |
|------|---|------|
| admin_data_agent | AdminSubAgent(15KB) | 数据查询 |
| admin_action_agent | AdminActionAgent(15KB) | 管理操作(需确认) |

**Ultra专属 (9个)**:

| Code | 类 | 功能 |
|------|---|------|
| user_ai_meta | UserAiMetaAgent | 元代理:用户端AI |
| admin_ai_meta | AdminAiMetaAgent | 元代理:管理端AI |
| ultra_system | UltraSystemAgent | 系统级用户管理 |
| ultra_user_control | UltraUserControlAgent | 用户AI开关 |
| ultra_cross_user | UltraCrossUserAgent | 代替用户操作 |
| ultra_analytics | UltraAnalyticsAgent | 用户画像分析 |
| ultra_security | UltraSecurityAgent | 安全审计 |
| ultra_browse_history | UltraBrowseHistoryAgent | 任意用户浏览记录 |
| ultra_skill_control | UltraSkillControlAgent | Skill管理 |

### 5.6 ToolSelector (V1 LLM工具选择)

```java
public ToolCallResult select(String userQuery, List<AgentTool> tools) {
    // 格式化工具列表为Prompt → 调用辅助LLM → 返回 {"tool":"xxx","param":"xxx"}
    // 关键词匹配作为LLM失败时的降级
}
```

---

## 6. V2 LangChain4j MasterBrain + @Tool

### 6.1 设计原理

1. `@SystemMessage` + `@UserMessage` 定义AiService接口
2. `@Tool` 标记Java方法暴露给LLM
3. `AiServices.builder()` 组装 模型+工具+记忆
4. `brain.chat()` 时LLM自动决定调用哪个工具

### 6.2 AiRequestContext (ThreadLocal)

@Tool方法无法接收自定义参数，使用ThreadLocal传递userId/sessionId:

```java
public class AiRequestContext {
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> SESSION_ID = new ThreadLocal<>();
    public static void set(Long userId, String sessionId) { USER_ID.set(userId); SESSION_ID.set(sessionId); }
    public static Long getUserId() { return USER_ID.get(); }
    public static String getSessionId() { return SESSION_ID.get(); }
    public static void clear() { USER_ID.remove(); SESSION_ID.remove(); }
}
```

---

## 7. Brain 接口与 MasterBrainFactory

### 7.1 UserMasterBrain

```java
public interface UserMasterBrain {
    @SystemMessage("""
        你是"Omnitrix 智能助手"，服务于非遗文化传承平台的用户端。
        【核心职责】帮助用户探索非遗文化、使用文创商城、管理个人信息、个性化推荐
        【工具使用规则】需要查询数据时必须调用工具，不编造数据，写操作需确认，一次最多3个工具
        {{skills}} {{userProfile}}
        """)
    String chat(@UserMessage String userMessage, @V("skills") String skills, @V("userProfile") String userProfile);
    TokenStream chatStream(@UserMessage String userMessage, @V("skills") String skills, @V("userProfile") String userProfile);
}
```

### 7.2 AdminMasterBrain

```java
public interface AdminMasterBrain {
    @SystemMessage("""
        你是"非遗管理助手"，服务于管理后台。
        【核心职责】运营数据查询、内容管理、用户管理、日常运营辅助
        【安全准则】管理操作需确认，禁止危险操作，敏感数据脱敏
        {{skills}}
        """)
    String chat(@UserMessage String msg, @V("skills") String skills);
}
```

### 7.3 UltraMasterBrain (brain包)

```java
public interface UltraMasterBrain {
    @SystemMessage("""
        你是"非遗平台超级管理助手"(Ultra)，拥有最高权限。
        【核心职责】跨用户数据管理、系统级用户管理、安全审计、AI系统管理、代替用户操作
        【安全准则】用户修改需二次确认，所有操作记录审计日志
        {{skills}}
        """)
    String chat(@UserMessage String msg, @V("skills") String skills);
}
```

### 7.4 MasterBrainFactory

```java
@Component
public class MasterBrainFactory {
    private final ChatLanguageModel chatLanguageModel;
    private final StreamingChatLanguageModel streamingChatLanguageModel;
    private final ToolRegistry toolRegistry;
    private final SkillPromptConfig skillPromptConfig;
    private final UserMemoryService userMemoryService;

    public UserMasterBrain buildUserBrain(Long userId, String sessionId) {
        List<Object> tools = toolRegistry.getToolsForRole("user");
        ChatMemory memory = MessageWindowChatMemory.builder().id(sessionId).maxMessages(20).build();
        return AiServices.builder(UserMasterBrain.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(memory).tools(tools).build();
    }

    public AdminMasterBrain buildAdminBrain(String sessionId) {
        List<Object> tools = toolRegistry.getToolsForRole("admin");
        ChatMemory memory = MessageWindowChatMemory.builder().id(sessionId).maxMessages(20).build();
        return AiServices.builder(AdminMasterBrain.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(memory).tools(tools).build();
    }

    public UltraMasterBrain buildUltraBrain(String sessionId) {
        List<Object> tools = toolRegistry.getToolsForRole("ultra");
        ChatMemory memory = MessageWindowChatMemory.builder().id(sessionId).maxMessages(20).build();
        return AiServices.builder(UltraMasterBrain.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(memory).tools(tools).build();
    }

    // 流式版本使用 streamingChatLanguageModel

    public String buildSkillsPrompt() {
        List<Skill> enabled = skillPromptConfig.getAllEnabledSkills();
        return enabled.stream().map(Skill::getSystemPrompt).collect(Collectors.joining("\n\n"));
    }

    public String buildUserProfile(Long userId) {
        return userMemoryService.buildUserProfile(userId);
    }
}
```

---

## 8. Tool 工具系统详解

### 8.1 ToolRegistry

```java
@Component
public class ToolRegistry {
    private final Map<String, List<Object>> roleTools = new LinkedHashMap<>();
    public void register(String role, Object toolProvider) {
        roleTools.computeIfAbsent(role, k -> new ArrayList<>()).add(toolProvider);
    }
    public List<Object> getToolsForRole(String role) {
        return roleTools.getOrDefault(role, Collections.emptyList());
    }
}
```

### 8.2 ToolRegistrationConfig 角色映射

```java
@PostConstruct
public void registerAll() {
    // User: ContentTools, CommerceTools, UserTools, KnowledgeTools, RecommendTools, BrowseHistoryTools
    // Admin: ContentTools, CommerceTools, KnowledgeTools, AdminTools
    // Ultra: ContentTools, CommerceTools, KnowledgeTools, AdminTools, UltraTools
}
```

### 8.3 ContentTools (271行)

依赖: `ContentService`, `ActionExecutor`

```java
@Tool("搜索非遗项目、非遗文化、传统技艺。参数: 搜索关键词")
public String searchItems(String keyword) {
    PageResult<IchItemDTO> items = contentService.listItems(1, 5, null, keyword, 1);
    // 格式化返回 "非遗项目搜索结果:\n- 项目名 - 简介\n..."
}

@Tool("搜索传承人、非遗大师、手艺人。参数: 搜索关键词")
public String searchHeritageMen(String keyword) { ... }

@Tool("搜索非遗活动、展览、体验活动。参数: 搜索关键词")
public String searchActivities(String keyword) { ... }

@Tool("报名参加非遗活动。参数: 活动关键词")
public String registerActivity(String keyword) {
    // 搜索活动 → 创建PendingAction → 保存到Redis → 返回确认提示
}

@Tool("点赞动态。参数: 动态关键词")
public String likePost(String keyword) { /* PendingAction */ }

@Tool("收藏动态。参数: 动态关键词")
public String favoritePost(String keyword) { /* PendingAction */ }
```

### 8.4 CommerceTools (271行)

依赖: `ProductService`, `OrderService`, `ActionExecutor`

内置 `PRICE_PATTERN` 正则支持自然语言价格范围解析("100元以内"/"50-200块")。

```java
@Tool("搜索文创商品、查看商品价格库存。参数: 搜索关键词（可包含价格范围如'100元以内'）")
public String searchProducts(String query) {
    // 正则提取价格范围 → productService.listProducts() → 格式化
}

@Tool("将商品加入购物车。参数: 商品关键词")
public String addToCart(String keyword) {
    // 搜索商品 → PendingAction("add_to_cart") → 确认提示
}

@Tool("查看购物车内容")
public String viewCart() {
    Long userId = AiRequestContext.getUserId();
    List<CartDTO> carts = productService.getCartList(userId);
    // 格式化
}

@Tool("查询订单列表。参数: 状态筛选(可选)")
public String queryOrders(String statusFilter) { ... }

@Tool("取消订单。参数: 订单编号")
public String cancelOrder(String orderId) { /* PendingAction */ }
```

### 8.5 UserTools (162行)

依赖: `UserService`, `SystemService`, `ActionExecutor`

```java
@Tool("查询当前用户基本信息、个人资料、用户名、手机号")
public String queryProfile() {
    Long userId = AiRequestContext.getUserId();
    UserDTO user = userService.findById(userId);
    // 手机号脱敏: 1xx****xxxx
}

@Tool("查询当前用户的收货地址列表")
public String queryAddresses() { ... }

@Tool("设置默认收货地址。参数: 地址关键词（收件人名或地址内容）")
public String setDefaultAddress(String keyword) { /* PendingAction */ }

@Tool("查看最近通知消息")
public String queryNotifications() { /* 最近10条 */ }

@Tool("查询传承人认证状态")
public String queryQualification() { ... }
```

### 8.6 AdminTools (321行)

依赖: `ContentService`, `ProductService`, `OrderService`, `UserService`, `SystemService`, `ActionExecutor`

```java
// 只读查询
@Tool("查询平台综合运营数据概览：用户数、商品数、订单数、非遗项目数、低库存预警等")
public String queryOverview() { ... }

@Tool("查询最近订单列表")
public String queryRecentOrders() { ... }

@Tool("搜索和管理商品")
public String queryProducts(String keyword) { ... }

// 写操作(需确认)
@Tool("商品上下架操作。参数: '商品名 上架' 或 '商品名 下架'")
public String updateProductStatus(String params) { /* PendingAction */ }

@Tool("发送系统通知。参数: '用户ID 通知内容' 或 '全部 通知内容'")
public String sendNotification(String params) { /* PendingAction */ }
```

### 8.7 KnowledgeTools (44行)

```java
@Tool("搜索知识库FAQ，回答平台常见问题。参数: 用户问题关键词")
public String searchKnowledge(String query) {
    List<AiKnowledgeBase> results = knowledgeService.searchAndRerank(query);
    // RAG搜索 + LLM Reranking，incrementHitCount
}
```

### 8.8 RecommendTools (91行)

```java
@Tool("为当前用户推荐非遗项目和文创商品（基于用户兴趣）")
public String recommend() {
    Long userId = AiRequestContext.getUserId();
    // 1. 用户兴趣分析 (recommendService.getUserInterests)
    // 2. 推荐非遗项目 (contentService.listItems)
    // 3. 推荐文创商品 (productService.listProducts)
}
```

### 8.9 BrowseHistoryTools (166行)

```java
@Tool("查看最近几天的浏览历史。参数: 天数")
public String queryRecentHistory(String daysStr) { ... }

@Tool("按类型查看浏览历史。参数: 类型(非遗/商品/传承人/活动)")
public String queryHistoryByType(String typeStr) { ... }

@Tool("查看指定日期的浏览记录。参数: 日期(yyyy-MM-dd)")
public String queryHistoryByDate(String dateStr) { ... }

@Tool("查看有浏览记录的日期列表")
public String listBrowseDates() { ... }
```

类型映射: `非遗→ich_item`, `传承人→heritage_man`, `活动→activity`, `商品→product`, `知识→knowledge`

### 8.10 UltraTools (536行)

替代原7个Ultra Agent，合并全部Ultra功能:

```java
// === 系统管理 (原UltraSystemAgent) ===
@Tool("查看平台用户列表。参数: 页码")
public String listAllUsers(String pageStr) { ... }

@Tool("搜索用户。参数: 用户名或ID")
public String searchUser(String keyword) { ... }

@Tool("封禁用户。参数: '用户ID 原因'")
public String banUser(String params) { /* PendingAction */ }

// === 用户AI控制 (原UltraUserControlAgent) ===
@Tool("查询指定用户的AI使用状态。参数: 用户ID")
public String queryUserAiStatus(String userIdStr) { ... }

@Tool("禁用指定用户的AI功能。参数: '用户ID 原因'")
public String disableUserAi(String params) { /* PendingAction */ }

// === 跨用户操作 (原UltraCrossUserAgent) ===
@Tool("查看指定用户的购物车。参数: 用户ID")
public String viewUserCart(String userIdStr) { ... }

// === 用户分析 (原UltraAnalyticsAgent) ===
@Tool("查看指定用户的AI画像和长期记忆。参数: 用户ID")
public String queryUserProfile(String userIdStr) {
    // userMemoryService.buildUserProfile + memoryMapper查询
}

// === 安全审计 (原UltraSecurityAgent) ===
@Tool("查询指定用户的操作日志。参数: '用户ID 天数'")
public String queryOperationLogs(String params) { ... }

// === 浏览记录 (原UltraBrowseHistoryAgent) ===
@Tool("查看指定用户的浏览历史。参数: '用户ID 天数'")
public String queryUserBrowseHistory(String params) { ... }

// === Skill管理 (原UltraSkillControlAgent) ===
@Tool("查看所有AI Skills的列表和状态")
public String listAllSkills() { ... }

@Tool("启用指定Skill。参数: Skill ID")
public String enableSkill(String skillId) { ... }

@Tool("禁用指定Skill。参数: Skill ID")
public String disableSkill(String skillId) { ... }
```

---

## 9. 意图路由系统 (V1)

### 9.1 IntentRouter

多层匹配: **跟随查询复用 → 关键词匹配 → 知识库命中 → LLM兜底**

```java
public String route(String userQuery, String lastAgentCode) {
    // 1. 短查询+有上轮Agent → 复用(上下文感知)
    if (lastAgentCode != null && isFollowUpQuery(userQuery)) return lastAgentCode;

    // 2. 关键词匹配(低成本,优先执行)
    String query = userQuery.toLowerCase();
    for (Map.Entry<String, String[]> entry : ROUTE_RULES.entrySet()) {
        for (String keyword : entry.getValue()) {
            if (query.contains(keyword)) return entry.getKey();
        }
    }

    // 3. 知识库命中检测(DB查询,在关键词之后)
    if (knowledgeService.hasMatch(userQuery)) return "knowledge_assistant";

    // 4. LLM意图分类(兜底,最高成本)
    if (llmFallbackEnabled) {
        String llmIntent = classifyByLlm(userQuery);
        if (llmIntent != null) return llmIntent;
    }

    return "general_assistant";
}
```

### 9.2 AdminIntentRouter

独立路由，默认 `admin_data_agent`。管理员只能访问管理员Agent。

### 9.3 UltraIntentRouter

可路由到所有Agent，支持Ultra专有关键词检测。

---

## 10. 黑板协作架构 (V1)

### 10.1 TaskBoard

```java
public class TaskBoard {
    private final Map<String, TaskNode> nodes;
    private final List<String> executionOrder;

    public TaskNode create(String agentCode, String taskQuery, List<String> requiredSkills) {
        String taskId = "task_" + (++idCounter);
        TaskNode node = TaskNode.create(taskId, agentCode, taskQuery, requiredSkills);
        nodes.put(taskId, node); executionOrder.add(taskId);
        return node;
    }

    public List<TaskNode> ready() {
        // 返回依赖已满足(PENDING→READY)的任务
        for (TaskNode n : nodes.values()) {
            if (n.getStatus() == PENDING && n.areDependenciesMet(nodes)) n.setStatus(READY);
        }
        return executionOrder.stream().map(nodes::get)
                .filter(n -> n.getStatus() == READY).collect(toList());
    }

    public void close(String taskId, String result, long latencyMs) { ... }
    public void fail(String taskId, String errorMsg) { ... }
    public boolean isAllDone() { ... }
    public String collectResults() { ... }
}
```

### 10.2 TaskNode 状态机

`PENDING → READY → RUNNING → DONE / FAILED`

每个TaskNode包含: taskId, agentCode, taskQuery, requiredSkills, dependencies[], result, status

### 10.3 多轮执行与再规划

```java
for (int round = 0; round < BLACKBOARD_MAX_ROUNDS && !board.isAllDone(); round++) {
    List<TaskNode> readyTasks = board.ready();
    if (readyTasks.isEmpty()) {
        // 再规划(最多1次)
        if (replanCount < MAX_REPLANS) {
            ReplanResult replan = masterBrain.replan(userMessage, board.getCompletedSummary());
            if (replan.isNeedReplan()) { /* 添加新任务到黑板 */ }
        }
        break;
    }
    // 并行执行所有就绪任务
    List<CompletableFuture<Void>> futures = readyTasks.stream()
        .map(task -> CompletableFuture.runAsync(() -> {
            task.setStatus(RUNNING);
            SubAgent agent = subAgentRegistry.getOrDefault(task.getAgentCode());
            AgentQueryResult result = executeAgent(agent, task.getTaskQuery(), context);
            if (result.getStatus() == ERROR) board.fail(task.getId(), result.getData());
            else board.close(task.getId(), result.getData(), latency);
        }, agentExecutor)).collect(toList());
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .get(AGENT_TIMEOUT_SECONDS * 2L, TimeUnit.SECONDS);
}
```

### 10.4 MasterBrain V1 决策

```java
// blackboard/MasterBrain.java
public boolean shouldUseLlmBrain(String userQuery) {
    return isComplexQuery(userQuery);  // 跨域关键词+多步骤连接词("然后","接着"等)
}

public Decision decide(String userQuery, boolean isAdmin) {
    // LLM JSON决策: {path:"quick_path/blackboard/direct_skill", agent:"xxx", skills:[...]}
}
```

### 10.5 Ultra L2 黑板

Ultra使用两级黑板: L2分解为元代理任务 → 每个元代理(user_ai_meta/admin_ai_meta)内部启动L1子流程。使用独立线程池 `l2BoardExecutor`（core=2,max=6）隔离。

---

## 11. Skill 动态增强系统

### 11.1 Skill 实体

```java
public class Skill {
    private String id;           // heritage_master
    private String name;         // 非遗文化大师
    private String description;
    private String systemPrompt; // 注入到System Prompt的专业能力描述
    private String[] keywords;
    private boolean enabled;
}
```

### 11.2 SkillPromptConfig

数据库+代码默认双轨制，`@PostConstruct` 从DB加载，DB空则初始化默认。

### 11.3 内置7个默认Skill

| ID | 名称 | 功能 |
|----|------|------|
| heritage_master | 非遗文化大师 | 非遗项目/传承人/传统文化知识 |
| shopping_advisor | 购物顾问 | 商品推荐/选购建议 |
| customer_service | 客服话术师 | 投诉/售后/退换货 |
| knowledge_expert | 知识百科达人 | 平台FAQ/操作指南 |
| recommend_expert | 推荐解读者 | 推荐逻辑/兴趣分析 |
| security_audit | 安全审核员 | 内容安全/敏感过滤 |
| quality_evaluator | 质量评估师 | AI回答质量评估 |

### 11.4 HeritageSkillPrompt 核心内容

```
【角色定位】你是"非遗传统文化大师"，中华非物质文化遗产的守护者与传播者。
【核心能力】
- 传统技艺：陶瓷、青瓷、建盏、紫砂、玉雕、刺绣、剪纸、漆器、景泰蓝
- 传统音乐：古琴、南音、昆曲、京剧、豫剧、越剧、黄梅戏
- 民俗活动：龙舞、狮舞、秧歌、傩舞、高跷
- 传统节日：春节、元宵、清明、端午、七夕、中秋、重阳
【回答框架】一句话定义→历史沿革→核心特点→代表作品→当代价值
```

### 11.5 V1 中Skill注入流程

1. MasterBrain分析问题决定所需Skills → 写入TaskNode
2. PromptAssembler从TaskBoard获取Skills → 注入Prompt层级1b
3. LLM基于增强Prompt生成回答

### 11.6 V2 中Skill注入

`MasterBrainFactory.buildSkillsPrompt()` 拼接所有启用Skill的systemPrompt，通过 `@V("skills")` 注入到Brain的 `@SystemMessage` 模板中。

### 11.7 SkillConfigService

```java
@Service
public class SkillConfigService {
    List<AiSkillConfig> getAllSkills();
    List<AiSkillConfig> getEnabledSkills();
    boolean enableSkill(String skillId);
    boolean disableSkill(String skillId);
    int batchUpdateEnabled(List<String> skillIds, Integer enabled);
}
```

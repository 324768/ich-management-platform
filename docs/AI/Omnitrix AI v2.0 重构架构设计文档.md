
  ---
  Omnitrix AI v2.0 重构架构设计文档

  目录

  1. #1-重构目标与核心理念
  2. #2-当前架构问题诊断
  3. #3-重构后的整体架构
  4. #4-核心架构图
  5. #5-关键组件设计
  6. #6-请求处理流程
  7. #7-langchain4j-集成方案
  8. #8-三套体系的实现
  9. #9-subagent-vs-tool-的区分
  10. #10-skill-系统重构
  11. #11-删除的组件清单
  12. #12-保留的组件清单
  13. #13-新增的组件清单
  14. #14-性能对比
  15. #15-迁移路径

  ---
  1. 重构目标与核心理念

  1.1 重构目标

  1. 消除冗余决策层：删除 IntentRouter、TaskDecomposer、ToolSelector 三层 LLM 调用，统一到 MasterBrain
  2. 采用 LangChain4j 纯编排模式：利用 LLM 原生 Function Calling 替代手写路由逻辑
  3. SubAgent 结果直推用户：借鉴 Kortex AI 的 show_result_to_user 机制，减少 Token 消耗
  4. 保留三套体系：用户端/管理员端/Ultra 通过权限过滤和 prompt 差异化实现，而非复制粘贴代码
  5. 简化 Tool 注册：简单功能（知识库、推荐、浏览记录）降级为 Tool，复杂功能保留 SubAgent

  1.2 核心理念

  从"多层决策"到"单一编排"

  当前架构（4层决策）:
    MasterBrain.shouldUseLlmBrain() [关键词判断]
      → MasterBrain.decide() [LLM决策 #1]
      → IntentRouter.route() [LLM兜底 #2]
      → ToolSelector.select() [LLM选工具 #3]
      → LlmClient.chat() [LLM生成回复 #4]

  重构后（2层决策）:
    MasterBrain.chatStream() [LLM编排 #1: 自动选Tool + 循环调用]
      → LlmClient.chat() [LLM生成回复 #2]

  从"结果回传整合"到"结果直推用户"

  当前架构:
    SubAgent 返回完整数据 → 塞入 System Prompt → LLM 重新组织语言 → 输出
    问题: 大量 Token 消耗，用户等待时间长

  重构后:
    SubAgent 返回完整数据 → SSE 直推用户（原始数据）
    SubAgent 返回摘要+Hint → MasterBrain（决策用）
    MasterBrain 最终输出简短点评/总结

  ---
  2. 当前架构问题诊断

  2.1 问题1: 三层决策冗余

  ┌─────────────────────────────────┬─────────────────────────────────────────┬─────────┬─────────────────────────────────────────┐
  │              组件               │                  职责                   │ LLM调用 │                  问题                   │
  ├─────────────────────────────────┼─────────────────────────────────────────┼─────────┼─────────────────────────────────────────┤
  │ MasterBrain.shouldUseLlmBrain() │ 关键词判断是否复杂                      │ 0       │ 和 TaskDecomposer 重复                  │
  ├─────────────────────────────────┼─────────────────────────────────────────┼─────────┼─────────────────────────────────────────┤
  │ MasterBrain.decide()            │ LLM决定                                 │ 1       │ 已包含路由能力，IntentRouter 多余       │
  │                                 │ quick_path/blackboard/direct_skill      │         │                                         │
  ├─────────────────────────────────┼─────────────────────────────────────────┼─────────┼─────────────────────────────────────────┤
  │ TaskDecomposer.isComplexQuery() │ 关键词判断是否复杂                      │ 0       │ 和 MasterBrain 的 CROSS_DOMAIN_HINTS    │
  │                                 │                                         │         │ 完全一样                                │
  ├─────────────────────────────────┼─────────────────────────────────────────┼─────────┼─────────────────────────────────────────┤
  │ TaskDecomposer.decompose()      │ LLM分解任务                             │ 1       │ 可合并到 MasterBrain                    │
  ├─────────────────────────────────┼─────────────────────────────────────────┼─────────┼─────────────────────────────────────────┤
  │ IntentRouter.route()            │ 关键词 + LLM兜底意图分类                │ 0~1     │ MasterBrain 已输出 agent，这层多余      │
  ├─────────────────────────────────┼─────────────────────────────────────────┼─────────┼─────────────────────────────────────────┤
  │ ToolSelector.select()           │ LLM选择工具                             │ 1       │ 每个 SubAgent 都调一次，浪费            │
  └─────────────────────────────────┴─────────────────────────────────────────┴─────────┴─────────────────────────────────────────┘

  结论: 一个简单请求最少 2~3 次 LLM 调用，复杂请求 4~5 次。IntentRouter 和 TaskDecomposer 的功能 MasterBrain 已经包含。

  2.2 问题2: SubAgent 不是真正的 Agent

  看 CommerceSubAgent 和 RecommendSubAgent，它们的 execute() 方法做的事情是：
  1. 用 ToolSelector（又一次 LLM 调用）选择一个内部工具
  2. 调用业务 Service 获取数据
  3. 把数据格式化成字符串返回

  这不是 Agent 的行为模式。Agent 应该有自主决策能力、能多轮推理。你的 SubAgent 实际上就是 Tool（工具）——
  接收输入，执行确定性操作，返回结果。

  问题:
  - 每个 SubAgent 内部又嵌套了一个 ToolSelector（LLM 调用），形成了 "Agent 里套 Agent" 的结构
  - SubAgent 的 getAgentPrompt() 返回的 prompt 被注入到最终 LLM 调用中，但 SubAgent 自己并不调用 LLM 做推理
  - RecommendSubAgent 和 BrowseHistorySubAgent 只是简单的数据查询，完全不需要包装成 SubAgent

  2.3 问题3: Skill 系统和 SubAgent 系统割裂

  Skill 是纯 prompt 注入（systemPrompt 字段），SubAgent 是执行逻辑。但两者的选择机制完全独立：
  - SkillSelector 用 LLM 选 Skill
  - MasterBrain 的 DECISION_PROMPT 里也列出了 Skills
  - IntentRouter 完全不知道 Skills 的存在

  这导致 Skill 的选择路径不一致，有时由 MasterBrain 决定，有时由 SkillSelector 决定。

  2.4 问题4: OrchestratorService 是 God Class

  构造函数注入了 28
  个依赖。这个类同时处理：同步聊天、流式聊天、管理员聊天、Ultra聊天、黑板执行、快速路径执行、待确认操作、消息存储、后处理……每种还有
  sync/stream 两个版本。

  2.5 问题5: Ultra 体系是整个架构的复制粘贴

  UltraMasterBrain ≈ MasterBrain 的复制
  UltraTaskDecomposer ≈ TaskDecomposer 的复制
  UltraIntentRouter ≈ IntentRouter 的复制

  三套几乎相同的决策逻辑，只是 prompt 和 agent 列表不同。

  ---
  3. 重构后的整体架构

  3.1 架构分层

  ┌─────────────────────────────────────────────────────────────────┐
  │                        客户端层                                  │
  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐       │
  │  │ 用户端App │  │ 管理后台  │  │ Ultra管理 │  │ API调用  │       │
  │  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘       │
  └───────┼─────────────┼─────────────┼─────────────┼──────────────┘
          │             │             │             │
          ▼             ▼             ▼             ▼
  ┌─────────────────────────────────────────────────────────────────┐
  │                   OrchestratorService (精简)                     │
  │              统一入口、安全检查、后处理、SSE管理                   │
  └───────────────────────────┬─────────────────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          │                   │                   │
          ▼                   ▼                   ▼
  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
  │UserMasterBrain│  │AdminMasterBrain│ │UltraMasterBrain│
  │(LangChain4j) │  │(LangChain4j)  │  │(LangChain4j)  │
  │              │  │               │  │               │
  │ AiService    │  │ AiService     │  │ AiService     │
  └──────┬───────┘  └──────┬────────┘  └──────┬───────┘
         │                 │                   │
         │ 自动 Function Calling (LLM 原生)    │
         │                 │                   │
         ▼                 ▼                   ▼
  ┌─────────────────────────────────────────────────────────────────┐
  │                  CompositeToolProvider                          │
  │         (根据 role 过滤可用 Tool + SubAgent)                     │
  │  ┌──────────────────────┐  ┌──────────────────────┐           │
  │  │SystemSubAgentToolProvider│ │SimpleToolProvider   │           │
  │  │(真正的 SubAgent)      │  │(简单 Tool)           │           │
  │  └──────────────────────┘  └──────────────────────┘           │
  └───────────────────────────┬─────────────────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          │                   │                   │
          ▼                   ▼                   ▼
  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
  │SystemSubAgent│  │SystemSubAgent│  │  Simple Tool │
  │content_      │  │commerce_     │  │knowledge_    │
  │assistant     │  │assistant     │  │search        │
  │              │  │              │  │              │
  │内部有13个工具 │  │内部有11个工具 │  │单一功能      │
  │自主决策      │  │自主决策      │  │直接返回      │
  └──────────────┘  └──────────────┘  └──────────────┘

  3.2 核心变化

  ┌───────────────┬─────────────────────────────────────────────┬──────────────────────────────────────────────────────┐
  │     维度      │                  当前架构                   │                        重构后                        │
  ├───────────────┼─────────────────────────────────────────────┼──────────────────────────────────────────────────────┤
  │ 决策入口      │ IntentRouter + MasterBrain + TaskDecomposer │ MasterBrain (LangChain4j AiService)                  │
  ├───────────────┼─────────────────────────────────────────────┼──────────────────────────────────────────────────────┤
  │ 工具选择      │ ToolSelector (每个 SubAgent 调一次 LLM)     │ LLM Function Calling (一次搞定)                      │
  ├───────────────┼─────────────────────────────────────────────┼──────────────────────────────────────────────────────┤
  │ SubAgent 定位 │ 所有功能都包装成 SubAgent                   │ 真正需要自主决策的才是 SubAgent，简单功能降级为 Tool │
  ├───────────────┼─────────────────────────────────────────────┼──────────────────────────────────────────────────────┤
  │ 结果处理      │ SubAgent 结果塞回 MasterBrain → LLM 整合    │ SubAgent 结果 SSE 直推用户，MasterBrain 只看摘要     │
  ├───────────────┼─────────────────────────────────────────────┼──────────────────────────────────────────────────────┤
  │ 三套体系      │ 复制粘贴三份代码                            │ MasterBrainFactory + ToolRegistry 权限过滤           │
  ├───────────────┼─────────────────────────────────────────────┼──────────────────────────────────────────────────────┤
  │ Skill 注入    │ 注入到 SubAgent prompt                      │ 注入到 MasterBrain System Message                    │
  ├───────────────┼─────────────────────────────────────────────┼──────────────────────────────────────────────────────┤
  │ 黑板架构      │ 手写 TaskBoard + TaskDecomposer             │ LLM 上下文就是隐式黑板，Tool 循环替代任务分解        │
  └───────────────┴─────────────────────────────────────────────┴──────────────────────────────────────────────────────┘

  ---
  4. 核心架构图

  4.1 请求处理流程图

  用户请求
    │
    ▼
  ┌─────────────────────────────────────────────────────────────┐
  │ OrchestratorService.chatStream()                            │
  │  - 安全检查 (RateLimiter, GuardrailsFilter, TokenBudget)   │
  │  - 加载历史对话 (ChatMemoryManager)                         │
  │  - 加载用户画像 (UserMemoryService)                         │
  └───────────────────────────┬─────────────────────────────────┘
                              │
                              ▼
  ┌─────────────────────────────────────────────────────────────┐
  │ MasterBrainFactory.buildBrain(userId, role)                 │
  │  - 根据 role 选择 Tool 集合 (ToolRegistry)                  │
  │  - 动态注入 Skill prompt (SkillPromptConfig)               │
  │  - 动态注入用户画像 (UserMemoryService)                     │
  │  - 构建 LangChain4j AiService                               │
  └───────────────────────────┬─────────────────────────────────┘
                              │
                              ▼
  ┌─────────────────────────────────────────────────────────────┐
  │ MasterBrain.chatStream(memoryId, userMessage)              │
  │  [LangChain4j AiService - 单次持续对话]                     │
  │                                                             │
  │  LLM 看到:                                                  │
  │  - System Prompt (基础 + Skill + 用户画像)                 │
  │  - 所有可用 Tool 的定义 (ToolSpecification)                │
  │  - 历史对话 (ChatMemory)                                    │
  │  - 用户当前问题                                             │
  │                                                             │
  │  LLM 自主决策:                                              │
  │  ┌─────────────────────────────────────────────────────┐  │
  │  │ 1. 需要调用哪个 Tool?                                │  │
  │  │ 2. 传什么参数?                                       │  │
  │  │ 3. 调用几次?                                         │  │
  │  │ 4. 什么时候停止?                                     │  │
  │  └─────────────────────────────────────────────────────┘  │
  └───────────────────────────┬─────────────────────────────────┘
                              │
                              ▼
  ┌─────────────────────────────────────────────────────────────┐
  │ Tool 执行循环 (LangChain4j 自动管理)                        │
  │                                                             │
  │  ┌──────────────────────────────────────────────────┐     │
  │  │ LLM 决定: 调用 content_assistant("搜索剪纸活动")  │     │
  │  └────────────────────┬─────────────────────────────┘     │
  │                       │                                     │
  │                       ▼                                     │
  │  ┌──────────────────────────────────────────────────┐     │
  │  │ SystemSubAgentTool.execute()                     │     │
  │  │  - 调用 ContentSystemSubAgent.execute()          │     │
  │  │  - SubAgent 内部也是 LangChain4j AiService       │     │
  │  │  - SubAgent 内部 LLM 选择工具: search_activities │     │
  │  │  - 执行业务逻辑: contentService.listActivities() │     │
  │  │  - 格式化结果                                     │     │
  │  └────────────────────┬─────────────────────────────┘     │
  │                       │                                     │
  │                       ▼                                     │
  │  ┌──────────────────────────────────────────────────┐     │
  │  │ SubAgentResultWrapper.wrap()                     │     │
  │  │  - 原始数据 → SSE 直推用户 (show_result_to_user) │     │
  │  │  - 生成摘要 (summary)                            │     │
  │  │  - 生成 Hint (ACTION/REASON/DO_NOT)             │     │
  │  │  - 返回给 MasterBrain                            │     │
  │  └────────────────────┬─────────────────────────────┘     │
  │                       │                                     │
  │                       ▼                                     │
  │  ┌──────────────────────────────────────────────────┐     │
  │  │ MasterBrain 收到 Tool 返回结果                   │     │
  │  │  - 看到摘要: "找到3个剪纸活动"                   │     │
  │  │  - 看到 Hint: "ACTION: task_completeness_review" │     │
  │  │  - LLM 决定: 是否继续调用其他 Tool?             │     │
  │  └────────────────────┬─────────────────────────────┘     │
  │                       │                                     │
  │                       ▼                                     │
  │  ┌──────────────────────────────────────────────────┐     │
  │  │ LLM 决定: 不再调用 Tool，输出最终总结            │     │
  │  │ "已为您找到3个剪纸活动，详情见上方列表"          │     │
  │  └──────────────────────────────────────────────────┘     │
  │                                                             │
  └───────────────────────────┬─────────────────────────────────┘
                              │
                              ▼
  ┌─────────────────────────────────────────────────────────────┐
  │ OrchestratorService.saveAndPostProcess()                    │
  │  - 保存对话消息 (ConversationService)                       │
  │  - 更新会话记忆 (ChatMemoryManager)                         │
  │  - 提取用户画像 (MemoryExtractor)                           │
  │  - 生成对话标题 (TitleGenerator)                            │
  │  - 记录成本 (CostTracker)                                   │
  │  - 遥测追踪 (TelemetryTracer)                               │
  └─────────────────────────────────────────────────────────────┘

  4.2 MasterBrain 内部结构图

  ┌─────────────────────────────────────────────────────────────┐
  │ UserMasterBrain (LangChain4j AiService Interface)          │
  │                                                             │
  │ @SystemMessage(...)                                         │
  │ TokenStream chatStream(@MemoryId String id,                │
  │                        @UserMessage String message);        │
  └───────────────────────────┬─────────────────────────────────┘
                              │
                              │ 构建时注入
                              │
                              ▼
  ┌─────────────────────────────────────────────────────────────┐
  │ AiServices.builder(UserMasterBrain.class)                   │
  │  .streamingChatLanguageModel(primaryModel)                  │
  │  .systemMessageProvider(id -> buildSystemPrompt())          │
  │  .tools(compositeToolProvider)  ← 关键：注册所有 Tool       │
  │  .chatMemory(chatMemoryStore)                               │
  │  .listeners(observabilityListener, metricsListener)         │
  │  .build()                                                   │
  └───────────────────────────┬─────────────────────────────────┘
                              │
                              ▼
  ┌─────────────────────────────────────────────────────────────┐
  │ CompositeToolProvider (根据 role 过滤)                      │
  │                                                             │
  │  用户端可用 Tool:                                           │
  │  ┌────────────────────────────────────────────────────┐   │
  │  │ SystemSubAgent (真正的 Agent)                      │   │
  │  │  - content_assistant (13个内部工具)                │   │
  │  │  - commerce_assistant (11个内部工具)               │   │
  │  │  - user_assistant (用户信息操作)                   │   │
  │  │                                                     │   │
  │  │ Simple Tool (单一功能)                             │   │
  │  │  - knowledge_search (知识库搜索)                   │   │
  │  │  - recommend (推荐)                                │   │
  │  │  - browse_history (浏览记录)                       │   │
  │  │  - general_chat (通用闲聊)                         │   │
  │  └────────────────────────────────────────────────────┘   │
  │                                                             │
  │  管理员端可用 Tool:                                         │
  │  ┌────────────────────────────────────────────────────┐   │
  │  │ SystemSubAgent                                      │   │
  │  │  - admin_action_agent (管理操作)                   │   │
  │  │                                                     │   │
  │  │ Simple Tool                                        │   │
  │  │  - admin_data_query (数据查询)                     │   │
  │  │  - admin_notification (通知管理)                   │   │
  │  │  - knowledge_search (知识库)                       │   │
  │  └────────────────────────────────────────────────────┘   │
  │                                                             │
  │  Ultra 可用 Tool:                                           │
  │  ┌────────────────────────────────────────────────────┐   │
  │  │ SystemSubAgent                                      │   │
  │  │  - user_ai_meta_agent (用户端AI元管理)             │   │
  │  │  - admin_ai_meta_agent (管理端AI元管理)            │   │
  │  │  - ultra_cross_user_agent (跨用户操作)             │   │
  │  │                                                     │   │
  │  │ Simple Tool                                        │   │
  │  │  - ultra_user_control (用户控制)                   │   │
  │  │  - ultra_system (系统管理)                         │   │
  │  │  - ultra_analytics (数据分析)                      │   │
  │  │  - ultra_security (安全审核)                       │   │
  │  │  - ultra_skill_control (Skill管理)                 │   │
  │  └────────────────────────────────────────────────────┘   │
  └─────────────────────────────────────────────────────────────┘

  ---
  5. 关键组件设计

  5.1 MasterBrain 接口（LangChain4j AiService）

  /**
   * 用户端主脑 - LangChain4j AiService 接口
   * LangChain4j 会自动实现这个接口，处理 Tool 调用循环
   */
  public interface UserMasterBrain {

      /**
       * 流式聊天
       * @param memoryId 会话ID（用于 ChatMemory）
       * @param userMessage 用户消息
       * @return Token 流
       */
      @SystemMessage("""
          你是非遗文化传承平台的智能助手，专注于帮助用户探索非遗文化、购买文创商品、参与非遗活动。

          ## 核心能力
          - 搜索非遗项目、传承人、活动
          - 推荐文创商品、个性化内容
          - 管理购物车、订单
          - 回答非遗知识、平台使用问题

          ## 回复风格
          - 热情友好，富有文化底蕴
          - 精确引用数据（价格、库存、订单号等）
          - 主动引导用户探索更多内容

          ## 重要规则
          - 写操作（加购、下单、支付等）需要用户明确确认
          - 不要推测价格或库存，没有的就说没有
          - 优先使用工具获取实时数据，而非依赖记忆

          {{skills}}
          {{userProfile}}
          """)
      TokenStream chatStream(@MemoryId String memoryId,
                            @UserMessage String userMessage);
  }

  5.2 MasterBrainFactory（构建器）

  @Component
  public class MasterBrainFactory {

      private final StreamingChatLanguageModel primaryModel;
      private final StreamingChatLanguageModel auxiliaryModel;
      private final ToolRegistry toolRegistry;
      private final SkillPromptConfig skillPromptConfig;
      private final UserMemoryService userMemoryService;
      private final ChatMemoryStore chatMemoryStore;

      /**
       * 构建用户端 MasterBrain
       */
      public UserMasterBrain buildUserBrain(Long userId, String sessionId, SseEmitter emitter) {
          // 1. 获取用户端可用 Tool
          CompositeToolProvider toolProvider = toolRegistry.getToolProviderForRole("user");

          // 2. 构建 System Prompt
          String basePrompt = loadUserBasePrompt();
          String skillPrompts = skillPromptConfig.getEnabledSkillPrompts();
          String userProfile = userMemoryService.buildUserProfile(userId);

          // 3. 构建 LangChain4j AiService
          return AiServices.builder(UserMasterBrain.class)
              .streamingChatLanguageModel(primaryModel)
              .systemMessageProvider(memoryId -> {
                  return basePrompt
                      .replace("{{skills}}", skillPrompts)
                      .replace("{{userProfile}}", userProfile);
              })
              .tools(toolProvider)
              .chatMemory(MessageWindowChatMemory.builder()
                  .id(sessionId)
                  .maxMessages(20)
                  .chatMemoryStore(chatMemoryStore)
                  .build())
              .listeners(List.of(
                  new ObservabilityListener(emitter),
                  new MetricsListener()
              ))
              .build();
      }

      /**
       * 构建管理员端 MasterBrain
       */
      public AdminMasterBrain buildAdminBrain(Long adminId, String sessionId, SseEmitter emitter) {
          CompositeToolProvider toolProvider = toolRegistry.getToolProviderForRole("admin");
          String basePrompt = loadAdminBasePrompt();
          // ... 类似逻辑
      }

      /**
       * 构建 Ultra MasterBrain
       */
      public UltraMasterBrain buildUltraBrain(Long adminId, String sessionId, SseEmitter emitter) {
          CompositeToolProvider toolProvider = toolRegistry.getToolProviderForRole("ultra");
          String basePrompt = loadUltraBasePrompt();
          // ... 类似逻辑
      }
  }

  5.3 ToolRegistry（工具注册中心）

  @Component
  public class ToolRegistry {

      private final List<SystemSubAgent> systemSubAgents;
      private final List<SimpleTool> simpleTools;

      /**
       * 获取用户端 Tool Provider
       */
      public CompositeToolProvider getToolProviderForRole(String role) {
          switch (role) {
              case "user":
                  return new CompositeToolProvider(
                      new SystemSubAgentToolProvider(
                          filterSubAgentsByDomain("content", "commerce", "user")
                      ),
                      new SimpleToolProvider(
                          filterSimpleToolsByDomain("knowledge", "recommend", "browse_history", "general")
                      )
                  );
              case "admin":
                  return new CompositeToolProvider(
                      new SystemSubAgentToolProvider(
                          filterSubAgentsByDomain("admin_action")
                      ),
                      new SimpleToolProvider(
                          filterSimpleToolsByDomain("admin_data", "admin_notification", "knowledge")
                      )
                  );
              case "ultra":
                  return new CompositeToolProvider(
                      new SystemSubAgentToolProvider(
                          filterSubAgentsByDomain("user_ai_meta", "admin_ai_meta", "ultra_cross_user")
                      ),
                      new SimpleToolProvider(
                          filterSimpleToolsByDomain("ultra_user_control", "ultra_system",
                              "ultra_analytics", "ultra_security", "ultra_skill_control")
                      )
                  );
              default:
                  throw new IllegalArgumentException("Unknown role: " + role);
          }
      }

      private List<SystemSubAgent> filterSubAgentsByDomain(String... domains) {
          Set<String> domainSet = Set.of(domains);
          return systemSubAgents.stream()
              .filter(agent -> domainSet.contains(agent.getDomain()))
              .collect(Collectors.toList());
      }

      private List<SimpleTool> filterSimpleToolsByDomain(String... domains) {
          Set<String> domainSet = Set.of(domains);
          return simpleTools.stream()
              .filter(tool -> domainSet.contains(tool.

✻ Sautéed for 1m 48s

> 继续

● .filter(tool -> domainSet.contains(tool.getDomain()))
              .collect(Collectors.toList());
      }
  }

  5.4 SystemSubAgent 接口（真正的 Agent）

  /**
   * 系统级 SubAgent - 有自主决策能力的 Agent
   * 内部有多个工具，由内部 LLM 自主编排
   */
  public interface SystemSubAgent {

      /**
       * Agent 唯一标识
       */
      String getAgentCode();

      /**
       * Agent 名称
       */
      String getName();

      /**
       * Agent 描述（给 MasterBrain 看的）
       */
      String getDescription();

      /**
       * Agent 所属域（用于权限过滤）
       */
      String getDomain();

      /**
       * 执行 Agent 任务
       * @param userQuery 用户查询
       * @param context Agent 上下文
       * @param showResultToUser 是否直接推送结果给用户（SSE）
       * @return Agent 执行结果（包含原始数据 + 摘要 + Hint）
       */
      SubAgentResult execute(String userQuery, AgentContext context, boolean showResultToUser);
  }

  5.5 ContentSystemSubAgent 实现（重构后）

  @Component
  public class ContentSystemSubAgent implements SystemSubAgent {

      private final ContentService contentService;
      private final SseEmitterManager sseEmitterManager;
      private final StreamingChatLanguageModel model;

      // 内部 Agent Runtime（LangChain4j AiService）
      private ContentAgentRuntime agentRuntime;

      @PostConstruct
      public void init() {
          // 构建内部 Agent Runtime
          agentRuntime = AiServices.builder(ContentAgentRuntime.class)
              .streamingChatLanguageModel(model)
              .tools(
                  new SearchItemsTool(contentService),
                  new SearchHeritageManTool(contentService),
                  new SearchActivitiesTool(contentService),
                  new RegisterActivityTool(contentService),
                  new LikePostTool(contentService),
                  new FavoritePostTool(contentService),
                  new CommentPostTool(contentService),
                  // ... 其他 13 个工具
              )
              .build();
      }

      @Override
      public String getAgentCode() {
          return "content_assistant";
      }

      @Override
      public String getName() {
          return "非遗内容助手";
      }

      @Override
      public String getDescription() {
          return "搜索非遗项目、传承人、活动，报名活动，点赞/收藏/评论动态";
      }

      @Override
      public String getDomain() {
          return "content";
      }

      @Override
      public SubAgentResult execute(String userQuery, AgentContext context, boolean showResultToUser) {
          try {
              // 1. 调用内部 Agent Runtime（LangChain4j 自动处理工具选择和循环）
              StringBuilder fullResult = new StringBuilder();
              agentRuntime.chat(userQuery)
                  .onNext(token -> fullResult.append(token))
                  .onComplete(response -> {
                      // 2. 如果需要直推用户，通过 SSE 发送
                      if (showResultToUser) {
                          SseEmitter emitter = sseEmitterManager.getEmitter(context.getSessionId());
                          if (emitter != null) {
                              try {
                                  emitter.send(SseEmitter.event()
                                      .name("agent_result")
                                      .data(Map.of(
                                          "agent", getAgentCode(),
                                          "content", fullResult.toString()
                                      )));
                              } catch (IOException e) {
                                  log.error("SSE 推送失败", e);
                              }
                          }
                      }
                  })
                  .start();

              // 3. 生成摘要和 Hint（给 MasterBrain 看）
              String summary = generateSummary(fullResult.toString());
              String hint = generateHint(fullResult.toString(), context);

              // 4. 返回结构化结果
              return SubAgentResult.builder()
                  .agentCode(getAgentCode())
                  .rawData(fullResult.toString())
                  .summary(summary)
                  .hint(hint)
                  .status(determineStatus(fullResult.toString()))
                  .build();

          } catch (Exception e) {
              log.error("ContentSystemSubAgent 执行异常", e);
              return SubAgentResult.error(getAgentCode(), e.getMessage());
          }
      }

      /**
       * 生成摘要（给 MasterBrain 看）
       */
      private String generateSummary(String rawData) {
          if (rawData.isEmpty()) {
              return "未找到相关内容";
          }
          // 提取关键信息
          int itemCount = countItems(rawData);
          if (itemCount > 0) {
              return String.format("找到 %d 个非遗项目/活动", itemCount);
          }
          return "已完成操作";
      }

      /**
       * 生成 Hint（引导 MasterBrain 决策）
       */
      private String generateHint(String rawData, AgentContext context) {
          if (rawData.isEmpty()) {
              return "ACTION: change_strategy_or_stop\n" +
                     "REASON: 未找到相关内容\n" +
                     "DO_NOT: 不要用相同关键词重试";
          }

          return "ACTION: task_completeness_review\n" +
                 "INSTRUCTION: 检查用户原始请求是否已完全满足\n" +
                 "DO_NOT: 不要重复调用已成功的工具";
      }

      private String determineStatus(String rawData) {
          if (rawData.isEmpty()) return "empty";
          if (rawData.contains("异常") || rawData.contains("失败")) return "error";
          return "success";
      }

      private int countItems(String rawData) {
          // 简单计数逻辑
          return (int) rawData.lines().filter(line -> line.startsWith("- ")).count();
      }
  }

  /**
   * 内部 Agent Runtime 接口
   */
  interface ContentAgentRuntime {
      @SystemMessage("""
          你是非遗内容助手，专注于帮助用户搜索非遗项目、传承人、活动，以及管理动态互动。

          ## 可用工具
          - search_items: 搜索非遗项目
          - search_heritage_man: 搜索传承人
          - search_activities: 搜索活动
          - register_activity: 报名活动
          - like_post: 点赞动态
          - favorite_post: 收藏动态
          - comment_post: 评论动态

          ## 执行规则
          - 优先使用工具获取实时数据
          - 写操作（报名、点赞、收藏、评论）需要明确的用户意图
          - 返回结果要结构化、易读
          """)
      TokenStream chat(@UserMessage String userQuery);
  }

  5.6 SimpleTool 接口（简单工具）

  /**
   * 简单工具 - 单一功能，无需自主决策
   */
  public interface SimpleTool {

      /**
       * 工具名称
       */
      String getName();

      /**
       * 工具描述（给 MasterBrain 看的）
       */
      String getDescription();

      /**
       * 工具所属域（用于权限过滤）
       */
      String getDomain();

      /**
       * 执行工具
       */
      String execute(String parameter, AgentContext context);
  }

  5.7 KnowledgeSearchTool 实现（简单工具示例）

  @Component
  public class KnowledgeSearchTool implements SimpleTool {

      private final KnowledgeService knowledgeService;

      @Override
      public String getName() {
          return "knowledge_search";
      }

      @Override
      public String getDescription() {
          return "搜索知识库，回答非遗百科、平台使用指南等问题";
      }

      @Override
      public String getDomain() {
          return "knowledge";
      }

      @Tool("搜索知识库，回答非遗百科、平台使用指南等问题")
      public String execute(@P("搜索关键词") String keyword, AgentContext context) {
          List<AiKnowledgeBase> results = knowledgeService.search(keyword);
          if (results == null || results.isEmpty()) {
              return "知识库中未找到相关内容";
          }

          StringBuilder sb = new StringBuilder("知识库搜索结果:\n");
          for (AiKnowledgeBase kb : results) {
              sb.append("Q: ").append(kb.getQuestion()).append("\n");
              sb.append("A: ").append(kb.getAnswer()).append("\n\n");
          }
          return sb.toString();
      }
  }

  5.8 SubAgentResultWrapper（结果包装器）

  /**
   * SubAgent 结果包装器
   * 借鉴 Kortex AI 的结构化 Hint 机制
   */
  @Data
  @Builder
  public class SubAgentResult {

      /** Agent 代码 */
      private String agentCode;

      /** 原始数据（已通过 SSE 推送给用户） */
      private String rawData;

      /** 摘要（给 MasterBrain 看） */
      private String summary;

      /** Hint（引导 MasterBrain 决策） */
      private String hint;

      /** 状态：success / empty / error */
      private String status;

      /**
       * 格式化为 XML（给 LLM 看）
       */
      public String toXml() {
          return String.format(
              "<agent_result status=\"%s\" agent=\"%s\">\n" +
              "  <summary>%s</summary>\n" +
              "  <hint>%s</hint>\n" +
              "</agent_result>",
              status, agentCode, summary, hint
          );
      }

      public static SubAgentResult error(String agentCode, String errorMessage) {
          return SubAgentResult.builder()
              .agentCode(agentCode)
              .rawData("")
              .summary("执行失败: " + errorMessage)
              .hint("ACTION: stop\nREASON: Agent 执行异常\nDO_NOT: 不要重试")
              .status("error")
              .build();
      }
  }

  5.9 SystemSubAgentToolProvider（SubAgent 注册为 Tool）

  /**
   * 将 SystemSubAgent 注册为 LangChain4j Tool
   */
  public class SystemSubAgentToolProvider implements ToolProvider {

      private final List<SystemSubAgent> subAgents;
      private final SseEmitterManager sseEmitterManager;

      @Override
      public List<ToolSpecification> provideTools() {
          return subAgents.stream()
              .map(agent -> ToolSpecification.builder()
                  .name(agent.getAgentCode())
                  .description(agent.getDescription())
                  .addParameter("query", JsonSchemaProperty.STRING,
                      JsonSchemaProperty.description("用户查询内容"))
                  .build())
              .collect(Collectors.toList());
      }

      @Override
      public ToolExecutionResult executeTool(ToolExecutionRequest request, Object memoryId) {
          String agentCode = request.name();
          String query = request.arguments().get("query");

          // 找到对应的 SubAgent
          SystemSubAgent agent = subAgents.stream()
              .filter(a -> a.getAgentCode().equals(agentCode))
              .findFirst()
              .orElseThrow(() -> new IllegalArgumentException("Unknown agent: " + agentCode));

          // 构建上下文
          AgentContext context = buildContext(memoryId);

          // 执行 SubAgent（show_result_to_user=true）
          SubAgentResult result = agent.execute(query, context, true);

          // 返回结构化结果（XML 格式）给 MasterBrain
          return ToolExecutionResult.builder()
              .toolName(agentCode)
              .result(result.toXml())
              .build();
      }

      private AgentContext buildContext(Object memoryId) {
          // 从 memoryId 解析出 userId, sessionId 等
          // ...
      }
  }

  5.10 SimpleToolProvider（简单工具注册）

  /**
   * 将 SimpleTool 注册为 LangChain4j Tool
   */
  public class SimpleToolProvider implements ToolProvider {

      private final List<SimpleTool> simpleTools;

      @Override
      public List<ToolSpecification> provideTools() {
          return simpleTools.stream()
              .map(tool -> ToolSpecification.builder()
                  .name(tool.getName())
                  .description(tool.getDescription())
                  .addParameter("parameter", JsonSchemaProperty.STRING,
                      JsonSchemaProperty.description("工具参数"))
                  .build())
              .collect(Collectors.toList());
      }

      @Override
      public ToolExecutionResult executeTool(ToolExecutionRequest request, Object memoryId) {
          String toolName = request.name();
          String parameter = request.arguments().get("parameter");

          // 找到对应的 Tool
          SimpleTool tool = simpleTools.stream()
              .filter(t -> t.getName().equals(toolName))
              .findFirst()
              .orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + toolName));

          // 构建上下文
          AgentContext context = buildContext(memoryId);

          // 执行工具
          String result = tool.execute(parameter, context);

          // 返回结果
          return ToolExecutionResult.builder()
              .toolName(toolName)
              .result(result)
              .build();
      }
  }

  ---
  6. 请求处理流程

  6.1 用户端聊天流程（完整示例）

  @Service
  public class OrchestratorService {

      private final MasterBrainFactory masterBrainFactory;
      private final RateLimiter rateLimiter;
      private final GuardrailsFilter guardrailsFilter;
      private final TokenBudget tokenBudget;
      private final ConversationService conversationService;
      private final ChatMemoryManager memoryManager;
      private final UserMemoryService userMemoryService;
      private final TelemetryTracer telemetryTracer;

      /**
       * 用户端流式聊天
       */
      public void userChatStream(Long userId, String sessionId, String userMessage, SseEmitter emitter) {
          long startTime = System.currentTimeMillis();

          try {
              // 1. 安全检查
              if (!rateLimiter.tryAcquire(userId)) {
                  emitter.send(SseEmitter.event().name("error")
                      .data("您的提问过于频繁，请稍后再试"));
                  emitter.complete();
                  return;
              }

              String inputRejection = guardrailsFilter.validateInput(userMessage);
              if (inputRejection != null) {
                  emitter.send(SseEmitter.event().name("error").data(inputRejection));
                  emitter.complete();
                  return;
              }

              if (!tokenBudget.hasRemaining(userId)) {
                  emitter.send(SseEmitter.event().name("error")
                      .data("您今日的AI对话额度已用完，请明天再试"));
                  emitter.complete();
                  return;
              }

              // 2. 加载或创建会话
              AiConversation conversation = conversationService.getOrCreateConversation(userId, sessionId);

              // 3. 构建 MasterBrain
              UserMasterBrain brain = masterBrainFactory.buildUserBrain(userId, sessionId, emitter);

              // 4. 调用 MasterBrain（LangChain4j 自动处理 Tool 循环）
              StringBuilder fullResponse = new StringBuilder();
              brain.chatStream(sessionId, userMessage)
                  .onNext(token -> {
                      try {
                          // 流式推送给用户
                          emitter.send(SseEmitter.event().name("token").data(token));
                          fullResponse.append(token);
                      } catch (IOException e) {
                          log.error("SSE 推送失败", e);
                      }
                  })
                  .onComplete(response -> {
                      try {
                          // 5. 后处理
                          saveAndPostProcess(userId, sessionId, userMessage, fullResponse.toString(),
                              conversation, startTime, response.tokenUsage());

                          // 6. 发送完成信号
                          emitter.send(SseEmitter.event().name("done").data(""));
                          emitter.complete();

                      } catch (Exception e) {
                          log.error("后处理失败", e);
                          emitter.completeWithError(e);
                      }
                  })
                  .onError(error -> {
                      log.error("MasterBrain 执行失败", error);
                      try {
                          emitter.send(SseEmitter.event().name("error")
                              .data("AI 服务暂时不可用，请稍后再试"));
                          emitter.complete();
                      } catch (IOException e) {
                          log.error("错误推送失败", e);
                      }
                  })
                  .start();

          } catch (Exception e) {
              log.error("聊天流程异常", e);
              try {
                  emitter.send(SseEmitter.event().name("error").data("系统异常"));
                  emitter.complete();
              } catch (IOException ex) {
                  log.error("异常推送失败", ex);
              }
          }
      }

      /**
       * 后处理：保存消息、更新记忆、遥测追踪
       */
      private void saveAndPostProcess(Long userId, String sessionId, String userMessage,
                                       String aiResponse, AiConversation conversation,
                                       long startTime, TokenUsage tokenUsage) {
          // 1. 保存消息
          conversationService.saveMessage(conversation.getId(), sessionId, "user", userMessage, null);
          conversationService.saveMessage(conversation.getId(), sessionId, "assistant", aiResponse, null);

          // 2. 更新会话记忆
          memoryManager.appendMessage(sessionId, "user", userMessage);
          memoryManager.appendMessage(sessionId, "assistant", aiResponse);

          // 3. 生成标题（首次对话）
          if (conversation.getMessageCount() == 0) {
              titleGenerator.generateTitle(conversation.getId(), userMessage);
          }

          // 4. 摘要与记忆提取（异步）
          memorySummarizer.summarizeIfNeeded(sessionId, conversation.getId());
          memoryExtractor.extractAndSave(userId, userMessage, aiResponse);

          // 5. Token 消费
          tokenBudget.consume(userId, tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

          // 6. 遥测追踪
          long duration = System.currentTimeMillis() - startTime;
          String traceId = telemetryTracer.recordAndReturnTraceId(
              userId, conversation.getId(), userMessage, aiResponse, duration, tokenUsage);

          // 7. 成本记录
          costTracker.recordCost(traceId, "deepseek-v3",
              tokenUsage.inputTokenCount(), tokenUsage.outputTokenCount());
      }
  }

  6.2 Tool 调用流程示例

  用户请求: "帮我搜索剪纸活动"

  Step 1: MasterBrain 收到请求
    System Prompt:
      - 基础 prompt（你是非遗文化传承平台的智能助手...）
      - Skill prompt（heritage_master: 你是非遗文化大师...）
      - 用户画像（该用户关注: 剪纸、刺绣...）

    可用 Tool:
      - content_assistant: 搜索非遗项目、传承人、活动...
      - commerce_assistant: 搜索文创商品、管理订单...
      - knowledge_search: 搜索知识库...
      - recommend: 推荐内容...
      - browse_history: 查看浏览记录...

    用户消息: "帮我搜索剪纸活动"

  Step 2: LLM 决策（Function Calling）
    LLM 分析: 用户想搜索活动 → 需要调用 content_assistant

    Tool Call:
      {
        "name": "content_assistant",
        "arguments": {
          "query": "搜索剪纸活动"
        }
      }

  Step 3: SystemSubAgentToolProvider 执行
    1. 找到 ContentSystemSubAgent
    2. 调用 agent.execute("搜索剪纸活动", context, showResultToUser=true)

  Step 4: ContentSystemSubAgent 内部执行
    1. 内部 Agent Runtime（LangChain4j）收到 "搜索剪纸活动"
    2. 内部 LLM 决策: 需要调用 search_activities 工具
    3. 执行 SearchActivitiesTool.execute("剪纸")
    4. 调用 contentService.listActivities(keyword="剪纸")
    5. 格式化结果:
       ```
       活动搜索结果:
       - 剪纸艺术体验课 - 2024-03-20 - 北京
       - 传统剪纸工作坊 - 2024-03-25 - 上海
       - 剪纸文化节 - 2024-04-01 - 杭州
       ```
    6. SSE 直推用户（show_result_to_user=true）
    7. 生成摘要: "找到 3 个剪纸活动"
    8. 生成 Hint: "ACTION: task_completeness_review..."
    9. 返回 SubAgentResult

  Step 5: MasterBrain 收到 Tool 返回结果
    Tool 返回:
      <agent_result status="success" agent="content_assistant">
        <summary>找到 3 个剪纸活动</summary>
        <hint>
          ACTION: task_completeness_review
          INSTRUCTION: 检查用户原始请求是否已完全满足
          DO_NOT: 不要重复调用已成功的工具
        </hint>
      </agent_result>

    LLM 看到:
      - 用户原始请求: "帮我搜索剪纸活动"
      - Tool 返回摘要: "找到 3 个剪纸活动"
      - Hint: 任务已完成，不要重复调用

    LLM 决策: 任务已完成，输出最终总结

  Step 6: MasterBrain 输出最终回复
    "已为您找到 3 个剪纸活动，详情见上方列表。您可以点击活动名称查看详情或报名参加。"

    （注意: 活动列表已在 Step 4 通过 SSE 推送给用户，这里只是简短点评）

  Step 7: 后处理
    - 保存对话消息
    - 更新会话记忆
    - 提取用户画像（用户关注剪纸）
    - 记录成本和遥测

  ---
  7. LangChain4j 集成方案

  7.1 依赖配置

  <!-- pom.xml -->
  <dependencies>
      <!-- LangChain4j 核心 -->
      <dependency>
          <groupId>dev.langchain4j</groupId>
          <artifactId>langchain4j</artifactId>
          <version>0.35.0</version>
      </dependency>

      <!-- LangChain4j Spring Boot Starter -->
      <dependency>
          <groupId>dev.langchain4j</groupId>
          <artifactId>langchain4j-spring-boot-starter</artifactId>
          <version>0.35.0</version>
      </dependency>

      <!-- LangChain4j OpenAI 兼容模型（SiliconFlow 使用 OpenAI 协议） -->
      <dependency>
          <groupId>dev.langchain4j</groupId>
          <artifactId>langchain4j-open-ai</artifactId>
          <version>0.35.0</version>
      </dependency>

      <!-- LangChain4j 内存存储（Redis） -->
      <dependency>
          <groupId>dev.langchain4j</groupId>
          <artifactId>langchain4j-redis</artifactId>
          <version>0.35.0</version>
      </dependency>
  </dependencies>

  7.2 配置文件

  # application.yml
  langchain4j:
    open-ai:
      chat-model:
        # SiliconFlow API（OpenAI 兼容）
        base-url: https://api.siliconflow.cn/v1
        api-key: ${SILICONFLOW_API_KEY}
        model-name: deepseek-ai/DeepSeek-V3
        temperature: 0.7
        max-tokens: 4096
        timeout: 60s

      streaming-chat-model:
        base-url: https://api.siliconflow.cn/v1
        api-key: ${SILICONFLOW_API_KEY}
        model-name: deepseek-ai/DeepSeek-V3
        temperature: 0.7
        max-tokens: 4096
        timeout: 120s

    chat-memory:
      # Redis 存储
      redis:
        host: ${REDIS_HOST:localhost}
        port: ${REDIS_PORT:6379}
        password: ${REDIS_PASSWORD:}
        database: 1

  7.3 模型配置类

  @Configuration
  public class LangChain4jConfig {

      @Value("${langchain4j.open-ai.chat-model.base-url}")
      private String baseUrl;

      @Value("${langchain4j.open-ai.chat-model.api-key}")
      private String apiKey;

      @Value("${langchain4j.open-ai.chat-model.model-name}")
      private String modelName;

      /**
       * 主模型（流式）
       */
      @Bean
      @Primary
      public StreamingChatLanguageModel primaryStreamingModel() {
          return OpenAiStreamingChatModel.builder()
              .baseUrl(baseUrl)
              .apiKey(apiKey)
              .modelName(modelName)
              .temperature(0.7)
              .maxTokens(4096)
              .timeout(Duration.ofSeconds(120))
              .logRequests(true)
              .logResponses(true)
              .build();
      }

      /**
       * 辅助模型（流式，用于降级）
       */
      @Bean
      @Qualifier("auxiliary")
      public StreamingChatLanguageModel auxiliaryStreamingModel() {
          return OpenAiStreamingChatModel.builder()
              .baseUrl(baseUrl)
              .apiKey(apiKey)
              .modelName("deepseek-ai/DeepSeek-V3") // 可以换成更便宜的模型
              .temperature(0.7)
              .maxTokens(2048)
              .timeout(Duration.ofSeconds(60))
              .build();
      }

      /**
       * Chat Memory Store（Redis）
       */
      @Bean
      public ChatMemoryStore chatMemoryStore(RedisTemplate<String, Object> redisTemplate) {
          return new RedisChatMemoryStore(redisTemplate);
      }
  }

  ---
  8. 三套体系的实现

  8.1 用户端 MasterBrain

  public interface UserMasterBrain {

      @SystemMessage("""
          你是非遗文化传承平台的智能助手，专注于帮助用户探索非遗文化、购买文创商品、参与非遗活动。

          ## 核心能力
          - 搜索非遗项目、传承人、活动
          - 推荐文创商品、个性化内容
          - 管理购物车、订单
          - 回答非遗知识、平台使用问题

          ## 可用工具
          - content_assistant: 搜索非遗项目、传承人、活动，报名活动，点赞/收藏/评论动态
          - commerce_assistant: 搜索文创商品、管理购物车、订单
          - user_assistant: 查看和修改用户信息
          - knowledge_search: 搜索知识库
          - recommend: 获取个性化推荐
          - browse_history: 查看浏览记录

          ## 回复风格
          - 热情友好，富有文化底蕴
          - 精确引用数据（价格、库存、订单号等）
          - 主动引导用户探索更多内容

          ## 重要规则
          - 写操作（加购、下单、支付等）需要用户明确确认
          - 不要推测价格或库存，没有的就说没有
          - 优先使用工具获取实时数据，而非依赖记忆
          - 工具返回的数据已直接展示给用户，你只需

✻ Crunched for 1m 29s

> 继续

● 输出简短的点评或总结
          - 如果工具返回 Hint 建议停止，不要继续调用工具

      {{skills}}
      {{userProfile}}
      """)
  TokenStream chatStream(@MemoryId String memoryId, @UserMessage String userMessage);
  }

  ### 8.2 管理员端 MasterBrain

  ```java
  public interface AdminMasterBrain {

      @SystemMessage("""
          你是非遗文化传承平台的管理后台AI助手，专注于帮助管理员进行平台运营管理。

          ## 核心能力
          - 查看平台运营数据（用户数、订单数、内容数等）
          - 管理非遗内容（审核、上下架）
          - 管理商品和订单
          - 管理用户和通知
          - 查询知识库

          ## 可用工具
          - admin_action_agent: 执行管理操作（审核内容、管理商品、处理订单等）
          - admin_data_query: 查询平台运营数据
          - admin_notification: 发送通知
          - knowledge_search: 搜索知识库

          ## 回复风格
          - 专业、简洁、数据驱动
          - 操作结果要明确（成功/失败/原因）
          - 涉及批量操作时列出影响范围

          ## 重要规则
          - 所有写操作必须用户确认后才执行
          - 涉及用户数据的操作要特别谨慎
          - 批量操作前必须告知影响范围
          - 不能执行超出管理员权限的操作

          {{skills}}
          """)
      TokenStream chatStream(@MemoryId String memoryId, @UserMessage String userMessage);
  }

  8.3 Ultra MasterBrain

  public interface UltraMasterBrain {

      @SystemMessage("""
          你是非遗文化传承平台的超级管理员AI助手（Ultra模式）。
          你拥有最高权限，可以管理整个AI系统、跨用户操作、安全审核。

          ## 核心能力
          - 管理AI系统配置（Agent启用/禁用、Skill管理、Prompt调整）
          - 跨用户操作（查看任意用户的对话、行为、画像）
          - 安全审核（内容安全、用户行为分析）
          - 系统级数据分析（AI使用统计、成本分析、性能监控）
          - 同时操控用户端和管理员端AI

          ## 可用工具
          - user_ai_meta_agent: 管理用户端AI（调整Agent、Skill、Prompt）
          - admin_ai_meta_agent: 管理管理端AI
          - ultra_cross_user_agent: 跨用户操作（查看对话、行为、画像）
          - ultra_user_control: 用户控制（封禁、解封、调整额度）
          - ultra_system: 系统管理（配置、缓存、日志）
          - ultra_analytics: 数据分析（使用统计、成本分析）
          - ultra_security: 安全审核（内容安全、行为分析）
          - ultra_skill_control: Skill管理（启用/禁用/配置）

          ## 回复风格
          - 精确、技术化、数据驱动
          - 操作结果要包含技术细节
          - 安全相关操作要记录审计日志

          ## 重要规则
          - Ultra 操作不可逆，必须二次确认
          - 跨用户操作必须记录审计日志
          - 系统级修改要评估影响范围
          - 安全审核结果要给出置信度

          {{skills}}
          """)
      TokenStream chatStream(@MemoryId String memoryId, @UserMessage String userMessage);
  }

  8.4 三套体系对比

  ┌─────────────────────────────────────────────────────────────────────────┐
  │                        三套体系差异化实现                                │
  ├──────────────┬──────────────────┬──────────────────┬───────────────────┤
  │              │ 用户端            │ 管理员端          │ Ultra             │
  ├──────────────┼──────────────────┼──────────────────┼───────────────────┤
  │ MasterBrain  │ UserMasterBrain  │ AdminMasterBrain │ UltraMasterBrain │
  │ 接口         │                  │                  │                   │
  ├──────────────┼──────────────────┼──────────────────┼───────────────────┤
  │ System       │ 热情友好         │ 专业简洁          │ 精确技术化        │
  │ Prompt       │ 文化底蕴         │ 数据驱动          │ 安全审计          │
  ├──────────────┼──────────────────┼──────────────────┼───────────────────┤
  │ SubAgent     │ content_assistant│ admin_action_    │ user_ai_meta_     │
  │              │ commerce_        │   agent          │   agent           │
  │              │   assistant      │                  │ admin_ai_meta_    │
  │              │ user_assistant   │                  │   agent           │
  │              │                  │                  │ ultra_cross_user  │
  ├──────────────┼──────────────────┼──────────────────┼───────────────────┤
  │ Simple Tool  │ knowledge_search │ admin_data_query │ ultra_user_control│
  │              │ recommend        │ admin_notification│ ultra_system     │
  │              │ browse_history   │ knowledge_search │ ultra_analytics   │
  │              │ general_chat     │                  │ ultra_security    │
  │              │                  │                  │ ultra_skill_ctrl  │
  ├──────────────┼──────────────────┼──────────────────┼───────────────────┤
  │ Skill 注入   │ heritage_master  │ 管理员专属 Skill  │ 全部 Skill        │
  │              │ shopping_advisor │                  │                   │
  │              │ customer_service │                  │                   │
  │              │ knowledge_expert │                  │                   │
  │              │ recommend_expert │                  │                   │
  ├──────────────┼──────────────────┼──────────────────┼───────────────────┤
  │ 共享基础设施  │ ← ChatMemoryManager, SSE, 安全护栏, 遥测, 成本追踪 →   │
  └──────────────┴──────────────────┴──────────────────┴───────────────────┘

  ---
  9. SubAgent vs Tool 的区分

  9.1 判断标准

  问题: 这个功能需要自主决策吗？

    ├── 是（内部有多个工具，需要 LLM 决定调用哪个、调用几次）
    │   → SystemSubAgent（真正的 Agent）
    │   例: content_assistant（13个工具）、commerce_assistant（11个工具）
    │
    └── 否（单一功能，接收输入 → 执行 → 返回结果）
        → SimpleTool（简单工具）
        例: knowledge_search、recommend、browse_history

  9.2 当前 SubAgent 分类

  ┌─────────────────────────┬─────────────────────────┬───────────────────────────────────────────────────────────────────────────┐
  │        当前组件         │       重构后类型        │                                   原因                                    │
  ├─────────────────────────┼─────────────────────────┼───────────────────────────────────────────────────────────────────────────┤
  │ ContentSubAgent         │ SystemSubAgent          │ 内部 13                                                                   │
  │                         │                         │ 个工具（搜索项目/传承人/活动、报名、点赞、收藏、评论等），需要自主决策    │
  ├─────────────────────────┼─────────────────────────┼───────────────────────────────────────────────────────────────────────────┤
  │ CommerceSubAgent        │ SystemSubAgent          │ 内部 11 个工具（搜索商品、加购、下单、支付、取消等），需要自主决策，且有  │
  │                         │                         │ PendingAction                                                             │
  ├─────────────────────────┼─────────────────────────┼───────────────────────────────────────────────────────────────────────────┤
  │ UserSubAgent            │ SystemSubAgent          │ 用户信息操作（查看/修改个人信息、偏好设置等），有多个工具                 │
  ├─────────────────────────┼─────────────────────────┼───────────────────────────────────────────────────────────────────────────┤
  │ AdminSubAgent           │ SystemSubAgent          │ 管理操作（审核、上下架、处理订单等），有多个工具                          │
  ├─────────────────────────┼─────────────────────────┼───────────────────────────────────────────────────────────────────────────┤
  │ AdminActionAgent        │ SystemSubAgent          │ 管理写操作，有 PendingAction                                              │
  ├─────────────────────────┼─────────────────────────┼───────────────────────────────────────────────────────────────────────────┤
  │ KnowledgeSubAgent       │ SimpleTool →            │ 单一功能：搜索知识库，返回结果                                            │
  │                         │ knowledge_search        │                                                                           │
  ├─────────────────────────┼─────────────────────────┼───────────────────────────────────────────────────────────────────────────┤
  │ RecommendSubAgent       │ SimpleTool → recommend  │ 单一功能：获取推荐列表                                                    │
  ├─────────────────────────┼─────────────────────────┼───────────────────────────────────────────────────────────────────────────┤
  │ BrowseHistorySubAgent   │ SimpleTool →            │ 单一功能：查询浏览记录                                                    │
  │                         │ browse_history          │                                                                           │
  ├─────────────────────────┼─────────────────────────┼───────────────────────────────────────────────────────────────────────────┤
  │ GeneralSubAgent         │ SimpleTool →            │ 单一功能：通用闲聊（不需要工具）                                          │
  │                         │ general_chat            │                                                                           │
  ├─────────────────────────┼─────────────────────────┼───────────────────────────────────────────────────────────────────────────┤
  │ DynamicSubAgent         │ SimpleTool（动态注册）  │ 从数据库配置构建，纯 prompt 驱动                                          │
  ├─────────────────────────┼─────────────────────────┼───────────────────────────────────────────────────────────────────────────┤
  │ UserAiMetaAgent         │ SystemSubAgent          │ Ultra 专属，管理用户端 AI                                                 │
  ├─────────────────────────┼─────────────────────────┼───────────────────────────────────────────────────────────────────────────┤
  │ AdminAiMetaAgent        │ SystemSubAgent          │ Ultra 专属，管理管理端 AI                                                 │
  ├─────────────────────────┼─────────────────────────┼───────────────────────────────────────────────────────────────────────────┤
  │ UltraCrossUserAgent     │ SystemSubAgent          │ Ultra 专属，跨用户操作                                                    │
  ├─────────────────────────┼─────────────────────────┼───────────────────────────────────────────────────────────────────────────┤
  │ UltraBrowseHistoryAgent │ SimpleTool              │ Ultra 专属，查看任意用户浏览记录                                          │
  ├─────────────────────────┼─────────────────────────┼───────────────────────────────────────────────────────────────────────────┤
  │ UltraAnalyticsAgent     │ SimpleTool              │ Ultra 专属，数据分析                                                      │
  ├─────────────────────────┼─────────────────────────┼───────────────────────────────────────────────────────────────────────────┤
  │ UltraSecurityAgent      │ SimpleTool              │ Ultra 专属，安全审核                                                      │
  ├─────────────────────────┼─────────────────────────┼───────────────────────────────────────────────────────────────────────────┤
  │ UltraSkillControlAgent  │ SimpleTool              │ Ultra 专属，Skill 管理                                                    │
  ├─────────────────────────┼─────────────────────────┼───────────────────────────────────────────────────────────────────────────┤
  │ UltraSystemAgent        │ SimpleTool              │ Ultra 专属，系统管理                                                      │
  ├─────────────────────────┼─────────────────────────┼───────────────────────────────────────────────────────────────────────────┤
  │ UltraUserControlAgent   │ SimpleTool              │ Ultra 专属，用户控制                                                      │
  └─────────────────────────┴─────────────────────────┴───────────────────────────────────────────────────────────────────────────┘

  9.3 SubAgent 内部的 Tool 拆分

  以 CommerceSubAgent 为例，当前的 11 个 switch-case 拆成 11 个独立的 @Tool 方法：

  /**
   * CommerceSystemSubAgent 的内部工具集
   */
  @Component
  public class CommerceTools {

      private final ProductService productService;
      private final OrderService orderService;

      @Tool("查询用户的订单列表、订单状态、物流信息")
      public String queryOrders(@P("用户ID") Long userId) {
          PageResult<OrderDTO> orders = orderService.listUserOrders(userId, null, 1, 5);
          // ... 格式化返回
      }

      @Tool("查询用户的购物车商品")
      public String queryCart(@P("用户ID") Long userId) {
          List<CartDTO> cartItems = productService.listCartItems(userId);
          // ... 格式化返回
      }

      @Tool("搜索文创商品、查看商品价格库存")
      public String searchProducts(@P("搜索关键词") String keyword) {
          PageResult<ProductDTO> products = productService.listProducts(1, 10, null, keyword, 1);
          // ... 格式化返回
      }

      @Tool("将商品加入购物车")
      public String addToCart(@P("商品关键词") String keyword, @P("用户ID") Long userId) {
          // 搜索商品 → 构建 PendingAction → 返回确认提示
      }

      @Tool("从购物车移除商品")
      public String removeFromCart(@P("商品关键词") String keyword, @P("用户ID") Long userId) {
          // ...
      }

      @Tool("下单、提交订单、购买购物车商品")
      public String createOrder(@P("用户ID") Long userId) {
          // ...
      }

      @Tool("支付订单")
      public String payOrder(@P("订单号") String orderNo, @P("用户ID") Long userId) {
          // ...
      }

      @Tool("取消订单")
      public String cancelOrder(@P("订单号") String orderNo, @P("用户ID") Long userId) {
          // ...
      }

      @Tool("确认收货")
      public String confirmReceive(@P("订单号") String orderNo, @P("用户ID") Long userId) {
          // ...
      }

      @Tool("清空购物车")
      public String clearCart(@P("用户ID") Long userId) {
          // ...
      }

      @Tool("修改购物车商品数量")
      public String updateCartQuantity(@P("商品关键词") String keyword,
                                        @P("数量") int quantity,
                                        @P("用户ID") Long userId) {
          // ...
      }
  }

  ---
  10. Skill 系统重构

  10.1 当前问题

  - Skill prompt 注入到 SubAgent 的 System Message → 每个 SubAgent 调用都要带上 Skill prompt → Token 浪费
  - SkillSelector 用 LLM 选 Skill → 又多一次 LLM 调用
  - MasterBrain 的 DECISION_PROMPT 里也列出了 Skills → 选择路径不一致

  10.2 重构方案

  Skill prompt 统一注入到 MasterBrain 的 System Message 中，因为最终生成回复的是 MasterBrain，Skill 影响的是回复的风格和专业度。

  当前:
    SkillSelector (LLM调用) → 选择 Skill → 注入到 SubAgent prompt

  重构后:
    MasterBrainFactory 构建时 → 读取所有启用的 Skill → 注入到 MasterBrain System Message
    （零额外 LLM 调用）

  10.3 Skill 注入方式

  // MasterBrainFactory.java
  private String buildSystemPrompt(String basePrompt, String role) {
      // 1. 获取该角色可用的 Skill
      List<Skill> enabledSkills = skillPromptConfig.getEnabledSkillsForRole(role);

      // 2. 组合 Skill prompt
      StringBuilder skillSection = new StringBuilder();
      if (!enabledSkills.isEmpty()) {
          skillSection.append("\n\n## 专业技能增强\n");
          skillSection.append("以下是你的专业技能，请在回答相关问题时运用这些知识：\n\n");
          for (Skill skill : enabledSkills) {
              skillSection.append("### ").append(skill.getName()).append("\n");
              skillSection.append(skill.getSystemPrompt()).append("\n\n");
          }
      }

      // 3. 替换模板变量
      return basePrompt.replace("{{skills}}", skillSection.toString());
  }

  10.4 Skill 动态开关（Ultra 管理）

  Ultra 可以通过 ultra_skill_control 工具动态启用/禁用 Skill。修改后，下次构建 MasterBrain 时会自动生效（因为每次请求都会重新构建
  System Prompt）。

  @Component
  public class UltraSkillControlTool implements SimpleTool {

      private final SkillConfigService skillConfigService;

      @Override
      public String getName() { return "ultra_skill_control"; }

      @Override
      public String getDescription() { return "管理Skill的启用/禁用/配置"; }

      @Override
      public String getDomain() { return "ultra_skill_control"; }

      @Tool("启用或禁用一个Skill")
      public String toggleSkill(@P("Skill ID") String skillId,
                                 @P("启用/禁用") boolean enabled) {
          skillConfigService.toggleSkill(skillId, enabled);
          return String.format("Skill [%s] 已%s", skillId, enabled ? "启用" : "禁用");
      }

      @Tool("查看所有Skill的状态")
      public String listSkills() {
          List<AiSkillConfig> skills = skillConfigService.listAll();
          // ... 格式化返回
      }
  }

  ---
  11. 删除的组件清单

  ┌────────────────────────┬────────────────────────────────────────────┬──────────────────────────────────────────────────┐
  │          组件          │                    路径                    │                     删除原因                     │
  ├────────────────────────┼────────────────────────────────────────────┼──────────────────────────────────────────────────┤
  │ IntentRouter           │ orchestrator/IntentRouter.java             │ MasterBrain 的 Function Calling 天然包含路由能力 │
  ├────────────────────────┼────────────────────────────────────────────┼──────────────────────────────────────────────────┤
  │ AdminIntentRouter      │ orchestrator/AdminIntentRouter.java        │ 同上                                             │
  ├────────────────────────┼────────────────────────────────────────────┼──────────────────────────────────────────────────┤
  │ UltraIntentRouter      │ orchestrator/UltraIntentRouter.java        │ 同上                                             │
  ├────────────────────────┼────────────────────────────────────────────┼──────────────────────────────────────────────────┤
  │ TaskDecomposer         │ blackboard/TaskDecomposer.java             │ LangChain4j 的 Tool 循环替代了任务分解           │
  ├────────────────────────┼────────────────────────────────────────────┼──────────────────────────────────────────────────┤
  │ UltraTaskDecomposer    │ blackboard/UltraTaskDecomposer.java        │ 同上                                             │
  ├────────────────────────┼────────────────────────────────────────────┼──────────────────────────────────────────────────┤
  │ TaskBoard              │ blackboard/TaskBoard.java                  │ LLM 上下文就是隐式黑板                           │
  ├────────────────────────┼────────────────────────────────────────────┼──────────────────────────────────────────────────┤
  │ TaskNode               │ blackboard/TaskNode.java                   │ 同上                                             │
  ├────────────────────────┼────────────────────────────────────────────┼──────────────────────────────────────────────────┤
  │ MasterBrain（旧）      │ blackboard/MasterBrain.java                │ 被 LangChain4j AiService 替代                    │
  ├────────────────────────┼────────────────────────────────────────────┼──────────────────────────────────────────────────┤
  │ UltraMasterBrain（旧） │ blackboard/UltraMasterBrain.java           │ 同上                                             │
  ├────────────────────────┼────────────────────────────────────────────┼──────────────────────────────────────────────────┤
  │ ToolSelector           │ agent/tool/ToolSelector.java               │ LangChain4j 的 Function Calling 替代             │
  ├────────────────────────┼────────────────────────────────────────────┼──────────────────────────────────────────────────┤
  │ SkillSelector          │ infrastructure/skill/SkillSelector.java    │ Skill prompt 直接注入 MasterBrain System Message │
  ├────────────────────────┼────────────────────────────────────────────┼──────────────────────────────────────────────────┤
  │ PromptAssembler        │ infrastructure/prompt/PromptAssembler.java │ MasterBrain 的 @SystemMessage 替代               │
  ├────────────────────────┼────────────────────────────────────────────┼──────────────────────────────────────────────────┤
  │ PromptManager          │ infrastructure/prompt/PromptManager.java   │ 同上                                             │
  ├────────────────────────┼────────────────────────────────────────────┼──────────────────────────────────────────────────┤
  │ PromptTemplate         │ infrastructure/prompt/PromptTemplate.java  │ 同上                                             │
  ├────────────────────────┼────────────────────────────────────────────┼──────────────────────────────────────────────────┤
  │ LlmMessageBuilder      │ infrastructure/llm/LlmMessageBuilder.java  │ LangChain4j 自动管理消息构建                     │
  └────────────────────────┴────────────────────────────────────────────┴──────────────────────────────────────────────────┘

  ---
  12. 保留的组件清单

  ┌──────────────────────────────────────────────┬────────────────────────────┬───────────────────────────────────────┐
  │                     组件                     │            路径            │               保留原因                │
  ├──────────────────────────────────────────────┼────────────────────────────┼───────────────────────────────────────┤
  │ ContentSubAgent → ContentSystemSubAgent      │ agent/impl/                │ 真正的 SubAgent，13 个内部工具        │
  ├──────────────────────────────────────────────┼────────────────────────────┼───────────────────────────────────────┤
  │ CommerceSubAgent → CommerceSystemSubAgent    │ agent/impl/                │ 真正的 SubAgent，11 个内部工具        │
  ├──────────────────────────────────────────────┼────────────────────────────┼───────────────────────────────────────┤
  │ UserSubAgent → UserSystemSubAgent            │ agent/impl/                │ 用户信息操作                          │
  ├──────────────────────────────────────────────┼────────────────────────────┼───────────────────────────────────────┤
  │ AdminSubAgent → AdminSystemSubAgent          │ agent/impl/                │ 管理操作                              │
  ├──────────────────────────────────────────────┼────────────────────────────┼───────────────────────────────────────┤
  │ AdminActionAgent → AdminActionSystemSubAgent │ agent/impl/                │ 管理写操作                            │
  ├──────────────────────────────────────────────┼────────────────────────────┼───────────────────────────────────────┤
  │ Ultra 系列 Agent                             │ agent/impl/Ultra*.java     │ Ultra 专属能力                        │
  ├──────────────────────────────────────────────┼────────────────────────────┼───────────────────────────────────────┤
  │ DynamicSubAgent                              │ agent/impl/                │ 数据库动态配置 Agent                  │
  ├──────────────────────────────────────────────┼────────────────────────────┼───────────────────────────────────────┤
  │ Skill + SkillPromptConfig                    │ infrastructure/skill/      │ 保留 Skill 概念，改为注入 MasterBrain │
  ├──────────────────────────────────────────────┼────────────────────────────┼───────────────────────────────────────┤
  │ PendingAction + ActionExecutor               │ orchestrator/ + dto/       │ 写操作确认机制                        │
  ├──────────────────────────────────────────────┼────────────────────────────┼───────────────────────────────────────┤
  │ AgentContext                                 │ agent/                     │ 上下文传递                            │
  ├──────────────────────────────────────────────┼────────────────────────────┼───────────────────────────────────────┤
  │ HintGenerator → SubAgentResultWrapper        │ agent/tool/                │ 升级为结构化 Hint                     │
  ├──────────────────────────────────────────────┼────────────────────────────┼───────────────────────────────────────┤
  │ LlmClient                                    │ infrastructure/llm/        │ 保留作为降级方案                      │
  ├──────────────────────────────────────────────┼────────────────────────────┼───────────────────────────────────────┤
  │ LlmCircuitBreaker                            │ infrastructure/llm/        │ 熔断器                                │
  ├──────────────────────────────────────────────┼────────────────────────────┼───────────────────────────────────────┤
  │ ChatMemoryManager                            │ infrastructure/memory/     │ 会话记忆                              │
  ├──────────────────────────────────────────────┼────────────────────────────┼───────────────────────────────────────┤
  │ MemoryExtractor                              │ infrastructure/memory/     │ 用户画像提取                          │
  ├──────────────────────────────────────────────┼────────────────────────────┼───────────────────────────────────────┤
  │ MemorySummarizer                             │ infrastructure/memory/     │ 对话摘要                              │
  ├──────────────────────────────────────────────┼────────────────────────────┼───────────────────────────────────────┤
  │ RedisChatMemoryStore                         │ infrastructure/memory/     │ Redis 存储                            │
  ├──────────────────────────────────────────────┼────────────────────────────┼───────────────────────────────────────┤
  │ SseEmitterManager                            │ infrastructure/sse/        │ SSE 管理                              │
  ├──────────────────────────────────────────────┼────────────────────────────┼───────────────────────────────────────┤
  │ GuardrailsFilter                             │ infrastructure/guardrails/ │ 安全护栏                              │
  ├──────────────────────────────────────────────┼────────────────────────────┼───────────────────────────────────────┤
  │ RateLimiter                                  │ infrastructure/guardrails/ │ 限流                                  │
  ├──────────────────────────────────────────────┼────────────────────────────┼───────────────────────────────────────┤
  │ TokenBudget                                  │ infrastructure/guardrails/ │ Token 预算                            │
  ├──────────────────────────────────────────────┼────────────────────────────┼───────────────────────────────────────┤
  │ TelemetryTracer                              │ infrastructure/telemetry/  │ 遥测追踪                              │
  ├──────────────────────────────────────────────┼────────────────────────────┼───────────────────────────────────────┤
  │ CostTracker                                  │ infrastructure/telemetry/  │ 成本追踪                              │
  ├──────────────────────────────────────────────┼────────────────────────────┼───────────────────────────────────────┤
  │ AiSelfEvaluator                              │ infrastructure/telemetry/  │ 自评估                                │
  ├──────────────────────────────────────────────┼────────────────────────────┼───────────────────────────────────────┤
  │ TitleGenerator                               │ infrastructure/telemetry/  │ 标题生成                              │
  ├──────────────────────────────────────────────┼────────────────────────────┼───────────────────────────────────────┤
  │ 所有 Entity/Mapper/Service                   │ entity/, mapper/, service/ │ 数据层不变                            │
  ├──────────────────────────────────────────────┼────────────────────────────┼───────────────────────────────────────┤
  │ 所有 Controller                              │ controller/                │ API 层不变                            │
  └──────────────────────────────────────────────┴────────────────────────────┴───────────────────────────────────────┘

  ---
  13. 新增的组件清单

  ┌────────────────────────────┬─────────────────────────────────────────────────────┬─────────────────────────────────────────────┐
  │            组件            │                        路径                         │                    作用                     │
  ├────────────────────────────┼─────────────────────────────────────────────────────┼─────────────────────────────────────────────┤
  │ UserMasterBrain            │ brain/UserMasterBrain.java                          │ 用户端 MasterBrain 接口 (LangChain4j        │
  │                            │                                                     │ AiService)                                  │
  ├────────────────────────────┼─────────────────────────────────────────────────────┼─────────────────────────────────────────────┤
  │ AdminMasterBrain           │ brain/AdminMasterBrain.java                         │ 管理员端 MasterBrain 接口                   │
  ├────────────────────────────┼─────────────────────────────────────────────────────┼─────────────────────────────────────────────┤
  │ UltraMasterBrain（新）     │ brain/UltraMasterBrain.java                         │ Ultra MasterBrain 接口                      │
  ├────────────────────────────┼─────────────────────────────────────────────────────┼─────────────────────────────────────────────┤
  │ MasterBrainFactory         │ brain/MasterBrainFactory.java                       │ 根据 role 构建 MasterBrain 实例             │
  ├────────────────────────────┼─────────────────────────────────────────────────────┼─────────────────────────────────────────────┤
  │ SystemSubAgent             │ agent/SystemSubAgent.java                           │ 真正的 SubAgent 接口                        │
  ├────────────────────────────┼─────────────────────────────────────────────────────┼─────────────────────────────────────────────┤
  │ SimpleTool                 │ agent/tool/SimpleTool.java                          │ 简单工具接口                                │
  ├────────────────────────────┼─────────────────────────────────────────────────────┼─────────────────────────────────────────────┤
  │ ToolRegistry               │ agent/tool/ToolRegistry.java                        │ 工具注册中心，按 role 过滤                  │
  ├────────────────────────────┼─────────────────────────────────────────────────────┼─────────────────────────────────────────────┤
  │ SystemSubAgentToolProvider │ agent/tool/SystemSubAgentToolProvider.java          │ 将 SubAgent 注册为 LLM Tool                 │
  ├────────────────────────────┼─────────────────────────────────────────────────────┼─────────────────────────────────────────────┤
  │ SimpleToolProvider         │ agent/tool/SimpleToolProvider.java                  │ 将简单 Tool 注册为 LLM Tool                 │
  ├────────────────────────────┼─────────────────────────────────────────────────────┼─────────────────────────────────────────────┤
  │ CompositeToolProvider      │ agent/tool/CompositeToolProvider.java               │ 合并两类 Tool Provider                      │
  ├────────────────────────────┼─────────────────────────────────────────────────────┼─────────────────────────────────────────────┤
  │ SubAgentResult             │ dto/SubAgentResult.java                             │ SubAgent 结果（原始数据 + 摘要 + Hint）     │
  ├────────────────────────────┼─────────────────────────────────────────────────────┼─────────────────────────────────────────────┤
  │ LangChain4jConfig          │ config/LangChain4jConfig.java                       │ LangChain4j 配置                            │
  ├────────────────────────────┼─────────────────────────────────────────────────────┼─────────────────────────────────────────────┤
  │ ObservabilityListener      │ infrastructure/telemetry/ObservabilityListener.java │ LangChain4j 可观测性监听器                  │
  ├────────────────────────────┼─────────────────────────────────────────────────────┼─────────────────────────────────────────────┤
  │ ContentTools               │ agent/tool/impl/ContentTools.java                   │ ContentSubAgent 内部工具集                  │
  ├────────────────────────────┼─────────────────────────────────────────────────────┼─────────────────────────────────────────────┤
  │ CommerceTools              │ agent/tool/impl/CommerceTools.java                  │ CommerceSubAgent 内部工具集                 │
  └────────────────────────────┴─────────────────────────────────────────────────────┴─────────────────────────────────────────────┘

  ---
  14. 性能对比

  14.1 LLM 调用次数

  ┌────────────────────────────────────────┬────────────────────────────────────┬────────────────────────────────────────┬────────┐
  │                  场景                  │              当前架构              │                 重构后                 │  节省  │
  ├────────────────────────────────────────┼────────────────────────────────────┼────────────────────────────────────────┼────────┤
  │ "什么是昆曲"（纯知识）                 │ IntentRouter(0~1) +                │ MasterBrain(1次，直接回答不调Tool) =   │ 50~67% │
  │                                        │ ToolSelector(1) + LLM(1) = 2~3次   │ 1次                                    │        │
  ├────────────────────────────────────────┼────────────────────────────────────┼────────────────────────────────────────┼────────┤
  │ "搜索剪纸活动"（单工具）               │ IntentRouter(0~1) +                │ MasterBrain(1) + SubAgent内部(1) = 2次 │ 0~33%  │
  │                                        │ ToolSelector(1) + LLM(1) = 2~3次   │                                        │        │
  ├────────────────────────────────────────┼────────────────────────────────────┼────────────────────────────────────────┼────────┤
  │ "搜索剪纸活动然后加购物车"（多工具）   │ MasterBrain(1) + 2×ToolSelector(2) │ MasterBrain(1) + SubAgent×2(2) = 3次   │ 25~40% │
  │                                        │  + LLM(1) + replan(0~1) = 4~5次    │                                        │        │
  ├────────────────────────────────────────┼────────────────────────────────────┼────────────────────────────────────────┼────────┤
  │ "帮我查订单然后确认收货"（同域多工具） │ MasterBrain(1) + ToolSelector(1) + │ MasterBrain(1) +                       │ 33%    │
  │                                        │  LLM(1) = 3次                      │ CommerceSubAgent内部(1) = 2次          │        │
  └────────────────────────────────────────┴────────────────────────────────────┴────────────────────────────────────────┴────────┘

  14.2 Token 消耗

  ┌───────────────────────┬─────────────────────────────────────┬──────────────────────────────┬──────┐
  │         维度          │              当前架构               │            重构后            │ 节省 │
  ├───────────────────────┼─────────────────────────────────────┼──────────────────────────────┼──────┤
  │ IntentRouter prompt   │ ~500 tokens/次                      │ 0（删除）                    │ 100% │
  ├───────────────────────┼─────────────────────────────────────┼──────────────────────────────┼──────┤
  │ ToolSelector prompt   │ ~300 tokens/次/SubAgent             │ 0（删除）                    │ 100% │
  ├───────────────────────┼─────────────────────────────────────┼──────────────────────────────┼──────┤
  │ SkillSelector prompt  │ ~400 tokens/次                      │ 0（删除）                    │ 100% │
  ├───────────────────────┼─────────────────────────────────────┼──────────────────────────────┼──────┤
  │ SubAgent 结果回传     │ ~1000 tokens（完整数据塞入 prompt） │ ~100 tokens（只传摘要+Hint） │ 90%  │
  ├───────────────────────┼─────────────────────────────────────┼──────────────────────────────┼──────┤
  │ TaskDecomposer prompt │ ~600 tokens/次                      │ 0（删除）                    │ 100% │
  └───────────────────────┴─────────────────────────────────────┴──────────────────────────────┴──────┘

  14.3 延迟

  ┌────────────┬─────────────────────────────────────────────────────────────┬──────────────────────────────────────────┬──────┐
  │    场景    │                          当前架构                           │                  重构后                  │ 节省 │
  ├────────────┼─────────────────────────────────────────────────────────────┼──────────────────────────────────────────┼──────┤
  │ 简单查询   │ IntentRouter(~1s) + ToolSelector(~1s) + LLM(~2s) = ~4s      │ MasterBrain(~2s) + SubAgent(~1s) = ~3s   │ 25%  │
  ├────────────┼─────────────────────────────────────────────────────────────┼──────────────────────────────────────────┼──────┤
  │ 复杂查询   │ MasterBrain(~1.5s) + 2×ToolSelector(~2s) + LLM(~2s) = ~5.5s │ MasterBrain(~2s) + SubAgent×2(~2s) = ~4s │ 27%  │
  ├────────────┼─────────────────────────────────────────────────────────────┼──────────────────────────────────────────┼──────┤
  │ 纯知识问答 │ IntentRouter(~1s) + SkillSelector(~1s) + LLM(~2s) = ~4s     │ MasterBrain(~2s，直接回答) = ~2s         │ 50%  │
  └────────────┴─────────────────────────────────────────────────────────────┴──────────────────────────────────────────┴──────┘

  ---
  15. 迁移路径

  Phase 1: 引入 LangChain4j 基础设施（1~2天）

  1. 添加 LangChain4j Maven 依赖
  2. 配置 LangChain4jConfig（SiliconFlow OpenAI 兼容模型）
  3. 验证 LangChain4j 能正常调用 DeepSeek-V3
  4. 验证 Function Calling 是否正常工作

  ▎ 风险点: DeepSeek-V3 通过 SiliconFlow 的 Function Calling 支持程度。如果不支持，需要用 LangChain4j 的 ToolCallingMode.FORCED
  或自定义 ToolCallParser。

  Phase 2: 构建新的 MasterBrain 体系（2~3天）

  1. 创建 UserMasterBrain / AdminMasterBrain / UltraMasterBrain 接口
  2. 创建 MasterBrainFactory
  3. 创建 ToolRegistry
  4. 创建 SystemSubAgentToolProvider + SimpleToolProvider + CompositeToolProvider
  5. 新旧并行：在 OrchestratorService 中添加开关，可以切换新旧架构

  Phase 3: 迁移 SubAgent 和 Tool（3~5天）

  1. 将 KnowledgeSubAgent → KnowledgeSearchTool（SimpleTool）
  2. 将 RecommendSubAgent → RecommendTool（SimpleTool）
  3. 将 BrowseHistorySubAgent → BrowseHistoryTool（SimpleTool）
  4. 将 GeneralSubAgent → GeneralChatTool（SimpleTool）
  5. 将 ContentSubAgent → ContentSystemSubAgent（内部用 LangChain4j）
  6. 将 CommerceSubAgent → CommerceSystemSubAgent（内部用 LangChain4j）
  7. 迁移 Ultra 系列 Agent

  Phase 4: 实现 SubAgent 结果直推（1~2天）

  1. 创建 SubAgentResult 数据结构
  2. 实现 SubAgentResultWrapper（摘要 + Hint 生成）
  3. 在 SystemSubAgentToolProvider 中实现 SSE 直推
  4. 修改前端适配新的 SSE 事件格式

  Phase 5: 删除旧组件 + 测试（2~3天）

  1. 删除 IntentRouter、TaskDecomposer、ToolSelector、SkillSelector 等
  2. 精简 OrchestratorService
  3. 全链路测试
  4. 性能对比测试
  5. 灰度发布

  总计: 约

✻ Sautéed for 2m 29s

> 继续

● 9~15天

  迁移风险与应对

  ┌──────────────────────────────────┬────────────────────┬───────────────────────────────────────────────────────────────────────┐
  │               风险               │        影响        │                               应对方案                                │
  ├──────────────────────────────────┼────────────────────┼───────────────────────────────────────────────────────────────────────┤
  │ DeepSeek-V3 Function Calling     │ 核心功能不可用     │ Phase 1 优先验证；备选方案：用 LangChain4j 的 AiServices 内置的 JSON  │
  │ 支持不完善                       │                    │ 模式解析 Tool Call，不依赖模型原生 Function Calling                   │
  ├──────────────────────────────────┼────────────────────┼───────────────────────────────────────────────────────────────────────┤
  │ SiliconFlow API 与 LangChain4j   │ 请求失败           │ 测试所有 API 端点；备选方案：自定义 ChatLanguageModel 实现            │
  │ OpenAI 模块兼容性                │                    │                                                                       │
  ├──────────────────────────────────┼────────────────────┼───────────────────────────────────────────────────────────────────────┤
  │ SubAgent 内部 LLM 调用增加总延迟 │ 响应变慢           │ SubAgent 内部用更轻量的模型；对简单 SubAgent 用规则匹配替代 LLM       │
  ├──────────────────────────────────┼────────────────────┼───────────────────────────────────────────────────────────────────────┤
  │ PendingAction 机制与新架构的集成 │ 写操作确认流程中断 │ SubAgent 内部的 Tool 返回 PendingAction 结构，MasterBrain 识别后暂停  │
  │                                  │                    │ Tool 循环                                                             │
  ├──────────────────────────────────┼────────────────────┼───────────────────────────────────────────────────────────────────────┤
  │ 前端 SSE 事件格式变化            │ 前端显示异常       │ 新增 agent_result 事件类型，保持 token 和 done 事件不变               │
  └──────────────────────────────────┴────────────────────┴───────────────────────────────────────────────────────────────────────┘

  ---
  附录 A: 重构前后包结构对比

  重构前:
  com.hyang.ich.omnitrix/
  ├── agent/
  │   ├── AgentContext.java
  │   ├── AgentUtils.java
  │   ├── SubAgent.java                    ← 删除（被 SystemSubAgent + SimpleTool 替代）
  │   ├── SubAgentRegistry.java            ← 删除（被 ToolRegistry 替代）
  │   ├── impl/
  │   │   ├── ContentSubAgent.java         ← 重构为 ContentSystemSubAgent
  │   │   ├── CommerceSubAgent.java        ← 重构为 CommerceSystemSubAgent
  │   │   ├── UserSubAgent.java            ← 重构为 UserSystemSubAgent
  │   │   ├── KnowledgeSubAgent.java       ← 降级为 SimpleTool
  │   │   ├── RecommendSubAgent.java       ← 降级为 SimpleTool
  │   │   ├── BrowseHistorySubAgent.java   ← 降级为 SimpleTool
  │   │   ├── GeneralSubAgent.java         ← 降级为 SimpleTool
  │   │   ├── DynamicSubAgent.java         ← 降级为 SimpleTool（动态）
  │   │   ├── AdminSubAgent.java           ← 重构为 AdminSystemSubAgent
  │   │   ├── AdminActionAgent.java        ← 重构为 AdminActionSystemSubAgent
  │   │   ├── AdminAiMetaAgent.java        ← 重构为 SystemSubAgent
  │   │   ├── UserAiMetaAgent.java         ← 重构为 SystemSubAgent
  │   │   ├── Ultra*.java                  ← 按类型分为 SystemSubAgent 或 SimpleTool
  │   │   └── ...
  │   └── tool/
  │       ├── AgentTool.java               ← 删除
  │       ├── ToolCallResult.java          ← 删除
  │       ├── ToolSelector.java            ← 删除
  │       └── HintGenerator.java           ← 升级为 SubAgentResultWrapper
  ├── blackboard/
  │   ├── MasterBrain.java                 ← 删除
  │   ├── UltraMasterBrain.java            ← 删除
  │   ├── TaskBoard.java                   ← 删除
  │   ├── TaskNode.java                    ← 删除
  │   ├── TaskDecomposer.java              ← 删除
  │   └── UltraTaskDecomposer.java         ← 删除
  ├── orchestrator/
  │   ├── OrchestratorService.java         ← 大幅精简
  │   ├── ActionExecutor.java              ← 保留
  │   ├── IntentRouter.java                ← 删除
  │   ├── AdminIntentRouter.java           ← 删除
  │   └── UltraIntentRouter.java           ← 删除
  ├── infrastructure/
  │   ├── skill/
  │   │   ├── Skill.java                   ← 保留
  │   │   ├── SkillPromptConfig.java       ← 保留
  │   │   └── SkillSelector.java           ← 删除
  │   ├── prompt/
  │   │   ├── PromptAssembler.java         ← 删除
  │   │   ├── PromptManager.java           ← 删除
  │   │   └── PromptTemplate.java          ← 删除
  │   └── ... (其余保留)
  └── ...

  重构后:
  com.hyang.ich.omnitrix/
  ├── brain/                               ← 新增
  │   ├── UserMasterBrain.java             ← LangChain4j AiService 接口
  │   ├── AdminMasterBrain.java            ← LangChain4j AiService 接口
  │   ├── UltraMasterBrain.java            ← LangChain4j AiService 接口
  │   └── MasterBrainFactory.java          ← 根据 role 构建 MasterBrain
  ├── agent/
  │   ├── AgentContext.java                ← 保留
  │   ├── AgentUtils.java                  ← 保留
  │   ├── SystemSubAgent.java              ← 新接口（真正的 Agent）
  │   ├── impl/
  │   │   ├── ContentSystemSubAgent.java   ← 重构（内部用 LangChain4j）
  │   │   ├── CommerceSystemSubAgent.java  ← 重构（内部用 LangChain4j）
  │   │   ├── UserSystemSubAgent.java      ← 重构
  │   │   ├── AdminSystemSubAgent.java     ← 重构
  │   │   ├── AdminActionSystemSubAgent.java ← 重构
  │   │   ├── UserAiMetaSystemSubAgent.java  ← 重构
  │   │   ├── AdminAiMetaSystemSubAgent.java ← 重构
  │   │   └── UltraCrossUserSystemSubAgent.java ← 重构
  │   └── tool/
  │       ├── SimpleTool.java              ← 新接口（简单工具）
  │       ├── ToolRegistry.java            ← 新增（工具注册中心）
  │       ├── SystemSubAgentToolProvider.java ← 新增（SubAgent → LLM Tool）
  │       ├── SimpleToolProvider.java      ← 新增（SimpleTool → LLM Tool）
  │       ├── CompositeToolProvider.java   ← 新增（合并 Provider）
  │       └── impl/
  │           ├── ContentTools.java        ← 新增（ContentSubAgent 内部工具集）
  │           ├── CommerceTools.java       ← 新增（CommerceSubAgent 内部工具集）
  │           ├── KnowledgeSearchTool.java ← 从 SubAgent 降级
  │           ├── RecommendTool.java       ← 从 SubAgent 降级
  │           ├── BrowseHistoryTool.java   ← 从 SubAgent 降级
  │           ├── GeneralChatTool.java     ← 从 SubAgent 降级
  │           ├── AdminDataQueryTool.java  ← 从 SubAgent 降级
  │           ├── UltraUserControlTool.java ← 从 SubAgent 降级
  │           ├── UltraSystemTool.java     ← 从 SubAgent 降级
  │           ├── UltraAnalyticsTool.java  ← 从 SubAgent 降级
  │           ├── UltraSecurityTool.java   ← 从 SubAgent 降级
  │           └── UltraSkillControlTool.java ← 从 SubAgent 降级
  ├── dto/
  │   ├── SubAgentResult.java              ← 新增（摘要 + Hint）
  │   ├── PendingAction.java               ← 保留
  │   └── ... (其余保留)
  ├── orchestrator/
  │   ├── OrchestratorService.java         ← 大幅精简（~200行 vs 当前~1200行）
  │   └── ActionExecutor.java              ← 保留
  ├── config/
  │   ├── LangChain4jConfig.java           ← 新增
  │   └── ... (其余保留)
  ├── infrastructure/
  │   ├── skill/
  │   │   ├── Skill.java                   ← 保留
  │   │   └── SkillPromptConfig.java       ← 保留（删除 SkillSelector）
  │   ├── llm/                             ← 保留（作为降级方案）
  │   ├── memory/                          ← 保留
  │   ├── sse/                             ← 保留
  │   ├── guardrails/                      ← 保留
  │   └── telemetry/
  │       ├── ObservabilityListener.java   ← 新增（LangChain4j 监听器）
  │       └── ... (其余保留)
  ├── entity/                              ← 保留
  ├── mapper/                              ← 保留
  ├── service/                             ← 保留
  └── controller/                          ← 保留

  ---
  附录 B: PendingAction 在新架构中的处理

  PendingAction（写操作确认）是你的业务特色，在新架构中需要特殊处理。

  当前流程

  CommerceSubAgent.proposeAddToCart()
    → 返回 AgentQueryResult.actionProposed(data, agentCode, pendingAction)
    → OrchestratorService 检测到 pendingAction
    → 缓存到 Redis
    → 返回确认提示给用户
    → 用户回复"确认"
    → ActionExecutor.execute(pendingAction)

  重构后流程

  MasterBrain 调用 commerce_assistant("把剪纸加入购物车")
    → CommerceSystemSubAgent 内部 LLM 调用 addToCart Tool
    → addToCart Tool 返回 PendingAction 结构（不直接执行）
    → SubAgent 检测到 PendingAction → 包装为特殊结果
    → SSE 推送确认提示给用户
    → 返回给 MasterBrain:
      <agent_result status="pending_action" agent="commerce_assistant">
        <summary>需要用户确认: 将「剪纸套装」加入购物车</summary>
        <hint>
          ACTION: wait_for_user_confirmation
          REASON: 写操作需要用户确认
          DO_NOT: 不要自动执行，不要调用其他工具
        </hint>
        <pending_action>
          {"type":"add_to_cart","productId":"123","productName":"剪纸套装","quantity":"1"}
        </pending_action>
      </agent_result>

    → MasterBrain 看到 Hint: wait_for_user_confirmation
    → MasterBrain 输出: "已为您找到「剪纸套装」，请确认是否加入购物车？"
    → 等待用户回复

    → 用户回复"确认"
    → MasterBrain 新一轮对话
    → MasterBrain 识别到这是确认操作
    → 调用 ActionExecutor.execute(pendingAction)
    → 返回执行结果

  ---
  附录 C: 与 Kortex AI 的最终对比

  ┌───────────────┬───────────────────────────────────────┬─────────────────────────────────────────────┐
  │     维度      │               Kortex AI               │              Omnitrix AI v2.0               │
  ├───────────────┼───────────────────────────────────────┼─────────────────────────────────────────────┤
  │ 框架          │ LangChain4j                           │ LangChain4j                                 │
  ├───────────────┼───────────────────────────────────────┼─────────────────────────────────────────────┤
  │ MasterBrain   │ 单一接口                              │ 三套接口（User/Admin/Ultra）                │
  ├───────────────┼───────────────────────────────────────┼─────────────────────────────────────────────┤
  │ SubAgent      │ SystemSubAgent（邮件、文档等）        │ SystemSubAgent（内容、商城、管理等）        │
  ├───────────────┼───────────────────────────────────────┼─────────────────────────────────────────────┤
  │ Simple Tool   │ 内置工具                              │ 知识库、推荐、浏览记录等                    │
  ├───────────────┼───────────────────────────────────────┼─────────────────────────────────────────────┤
  │ 结果直推      │ show_result_to_user                   │ show_result_to_user（借鉴）                 │
  ├───────────────┼───────────────────────────────────────┼─────────────────────────────────────────────┤
  │ Hint 机制     │ SubAgentResultWrapper（XML + 三元组） │ SubAgentResult（XML + 三元组，借鉴）        │
  ├───────────────┼───────────────────────────────────────┼─────────────────────────────────────────────┤
  │ Skill 系统    │ 无独立 Skill 概念                     │ 保留 Skill，注入 MasterBrain prompt         │
  ├───────────────┼───────────────────────────────────────┼─────────────────────────────────────────────┤
  │ PendingAction │ 无                                    │ 保留（写操作确认）                          │
  ├───────────────┼───────────────────────────────────────┼─────────────────────────────────────────────┤
  │ 权限体系      │ 单一角色                              │ 三层权限（用户/管理员/Ultra）               │
  ├───────────────┼───────────────────────────────────────┼─────────────────────────────────────────────┤
  │ 记忆系统      │ Langfuse                              │ 自建（Redis + MySQL）                       │
  ├───────────────┼───────────────────────────────────────┼─────────────────────────────────────────────┤
  │ 可观测性      │ Langfuse                              │ 自建 TelemetryTracer + LangChain4j Listener │
  ├───────────────┼───────────────────────────────────────┼─────────────────────────────────────────────┤
  │ 模型          │ 未知                                  │ DeepSeek-V3 via SiliconFlow                 │
  └───────────────┴───────────────────────────────────────┴─────────────────────────────────────────────┘

  ---
  风险与建议

  16.1 前置条件: Java 17 + Spring Boot 3.x 升级

  当前项目使用 Java 8 + Spring Boot 2.7.18 + Dubbo 2.7.8。
  LangChain4j 要求 Java 17+，因此需要先完成全项目升级：

  升级清单:
  ┌──────────────────────┬──────────────────────────┬──────────────────────────┐
  │         组件         │          升级前          │          升级后          │
  ├──────────────────────┼──────────────────────────┼──────────────────────────┤
  │ Java                 │ 1.8                      │ 17                       │
  │ Spring Boot          │ 2.7.18                   │ 3.2.x                    │
  │ Dubbo                │ 2.7.8                    │ 3.2.x                    │
  │ MyBatis Starter      │ 2.2.2                    │ 3.0.3                    │
  │ javax.*              │ javax.servlet 等         │ jakarta.servlet 等       │
  │ maven-compiler       │ source/target 1.8        │ source/target 17         │
  └──────────────────────┴──────────────────────────┴──────────────────────────┘

  影响范围: 全部 7 个模块（ich_common, ich_interface, ich_user_provider,
  ich_content_provider, ich_user_web, ich_admin_web, ich_omnitrix）

  16.2 风险: PendingAction 在新架构中的集成

  MasterBrain 是 LangChain4j AiService，无法在 Tool 循环中间"暂停等待用户确认"。

  应对方案: 保留 OrchestratorService 入口处的 PendingAction 检查（在调 MasterBrain 之前），
  不依赖 LLM 识别确认意图。这比让 LLM 隐式识别更可靠。

  16.3 风险: OrchestratorService 精简幅度

  文档预期从 ~1200 行精简到 ~200 行。实际仍需处理：安全检查、PendingAction 入口、
  MasterBrainFactory 调用、SSE 回调、saveAndPostProcess、user/admin/ultra 三个入口。
  更现实的估计: 400-500 行。仍然是巨大改进。

  16.4 风险: 黑板架构删除后的依赖任务处理

  删除 TaskBoard + TaskDecomposer 后，任务间依赖关系（先搜索→拿到ID→再报名）
  完全依赖 LLM 的 Tool 循环隐式处理。
  LangChain4j 的 AiService Tool 循环天然支持这种多步推理，但需要验证复杂场景。

  应对方案: 如果 Tool 循环处理依赖场景不稳定，可在 MasterBrain 的 SystemMessage 中
  添加"多步任务处理指引"，显式引导 LLM 先获取数据再执行操作。

  16.5 从 Kortex 借鉴的优先级建议

  ┌──────┬──────────────────────────────────┬─────────────────────────────────────┐
  │ 优先 │              借鉴项              │                理由                 │
  ├──────┼──────────────────────────────────┼─────────────────────────────────────┤
  │ P0   │ context 清洗                     │ 防 prompt injection，安全必须       │
  │ P0   │ shown_to_user 标记               │ 避免 MasterBrain 重复输出已推送数据 │
  │ P0   │ 连续重试计数                     │ 防止 Tool 循环失控                  │
  │ P1   │ show_result_to_user 由 LLM 决定  │ 灵活性提升                          │
  │ P1   │ 工具结果压缩                     │ Token 节省显著                      │
  │ P1   │ AskClarificationTool             │ 用户体验                            │
  │ P2   │ agent_dispatch SSE 事件           │ 前端体验                            │
  │ P2   │ 一致性自愈                       │ 稳定性                              │
  └──────┴──────────────────────────────────┴─────────────────────────────────────┘

  ---
  这就是完整的重构架构文档。核心变化总结：

  1. 删除 15 个组件（IntentRouter×3、TaskDecomposer×2、MasterBrain×2、TaskBoard、TaskNode、ToolSelector、SkillSelector、PromptAssemble
  r、PromptManager、PromptTemplate、LlmMessageBuilder）
  2. 新增 15 个组件（3个 MasterBrain 接口、MasterBrainFactory、SystemSubAgent 接口、SimpleTool 接口、ToolRegistry、3个
  ToolProvider、SubAgentResult、LangChain4jConfig、ObservabilityListener、ContentTools、CommerceTools）
  3. OrchestratorService 从 ~1200 行精简到 ~200 行
  4. LLM 调用次数减少 33%~67%
  5. Token 消耗减少约 50%


> 只需要回答我，我这一份架构是否适合rag向量化

● 适合，而且你的架构天然就有 RAG 的接入点。

  你已经有 KnowledgeSearchTool（SimpleTool），它就是 RAG 的入口。当前它大概率是关键词搜索或数据库查询，要升级为向量化
  RAG 只需要在这个 Tool 内部替换检索实现：

  用户提问 → MasterBrain 调用 KnowledgeSearchTool
    → 当前：关键词/SQL 查询
    → 升级后：Embedding → 向量数据库检索 → Rerank → 返回 Top-K 文档片段

  具体来说：

  1. 你的 DynamicSubAgent 已经支持绑定知识库（kbLibraryId），这个设计直接对应 RAG 的"每个 Agent 绑定不同知识库"的模式
  2. LangChain4j 原生支持 RAG 全链路：DocumentSplitter → EmbeddingModel → EmbeddingStore → ContentRetriever →
  RetrievalAugmentor，你不需要自己造轮子
  3. 你的三套 MasterBrain 体系不受影响 — RAG 是 Tool 层面的事，MasterBrain 只管调用，不关心 Tool
  内部是关键词搜索还是向量检索
  4. 你的 Skill 系统（heritage_master、shopping_advisor 等）也可以受益 — 给每个 Skill
  绑定专属向量知识库，回答质量会大幅提升

  唯一需要额外引入的是：
  - 向量数据库（Milvus / Qdrant / PgVector，推荐 PgVector 因为你已经用了 PostgreSQL/MySQL）
  - Embedding 模型（通义千问 text-embedding-v3 或 BGE-M3）

  架构不需要改，只需要在 Tool 实现层加一层向量检索。

✻ Worked for 2m 19s

> 你看看这段分析如何，只需要参考，这段分析不一定需要全部接受：关于 LangChain4j RAG 链路
你提到的 RetrievalAugmentor 是对的，但 LangChain4j 的 RAG 有两种接入方式，选哪种对 Omnitrix 影响不同：
EasyRAG（简单模式）直接在 AiService 上注解 @SystemMessage + ContentRetriever，MasterBrain 每次调用都自动检索。这对
Omnitrix 不合适——三套 MasterBrain 各自绑知识库，检索时机不受控，容易在不需要 RAG 的对话里也触发检索，浪费 Token。
AdvancedRAG（手动模式）在 Tool 内部手动调用 RetrievalAugmentor，检索只在 KnowledgeSearchTool.execute() 被 MasterBrain
 主动调用时才触发。这才是适合 Omnitrix 的方式，和你现有的 Tool 调用链完全吻合。

关于向量数据库选型
PgVector 的建议要加一个前提——你的知识库数据量级。百万级以下文档片段 PgVector 完全够用，运维成本低。但如果 Ultra
体系涉及多租户知识库隔离，PgVector 的 namespace 管理没有 Qdrant 方便，而且 Qdrant 对 Payload Filter（按 agentCode 或
tenantId 过滤检索范围）的支持更成熟——正好对应你 kbLibraryId 的设计。

一个你们容易忽略的问题：Rerank 的位置
Rerank 不能放在 KnowledgeSearchTool 返回之后、MasterBrain 整合之前——那时候 MasterBrain 已经拿到原始 Top-K 了，Rerank
应该在 Tool 内部完成，返回给 MasterBrain 的已经是精排后的结果。否则 SubAgentResultWrapper 拿到大量低质量片段，Hint
生成会乱。
推荐的链路：
KnowledgeSearchTool.execute()
  → Embedding(用户query)
  → VectorDB 召回 Top-20
  → BGE-Reranker / 通义 Rerank 精排
  → 截断到 Top-3~5（防 Token 溢出）
  → 格式化为 <knowledge_result> XML 返回
最后一步格式化成 XML 很重要——SubAgentResultWrapper 的状态检测认识 <search_result> 这类标签，你加一个
<knowledge_result> 并在 Wrapper 里补充对应的状态判断，RAG 结果就能被正确识别为 success 而不是 empty。

关于 Skill 绑定知识库
这个思路好，但要注意 Omnitrix 的 Skill 当前是纯 prompt 注入，没有执行能力。如果给 Skill 绑向量知识库，需要决定：是
Skill 激活时自动触发检索注入 System Prompt，还是 Skill 对应的 SubAgent
持有知识库引用。前者简单但不可控，后者更干净，和现有 AgentRuntime 绑 kbLibraryId 的模式一致。
建议选后者——Skill 只管 prompt 差异化，知识库归属到对应的 SubAgent，职责更清晰。

● 这段分析质量很高，核心判断都是对的。逐条说下：

  AdvancedRAG vs EasyRAG — 完全正确。EasyRAG 在 AiService 层自动检索对 Omnitrix 是灾难性的，你的 MasterBrain
  处理大量不需要 RAG 的对话（闲聊、管理操作、购物车操作），每次都触发向量检索纯属浪费。Tool
  内部手动调用才是正解，和你现有的 KnowledgeSearchTool 作为 SimpleTool 被 MasterBrain 按需调用的模式完全一致。

  向量数据库选型 — PgVector 百万级以下够用这个判断没问题。但他提到的 Qdrant Payload Filter 按 kbLibraryId
  过滤检索范围，这个点确实比 PgVector 的 WHERE 过滤更优雅。不过你当前阶段数据量不大，PgVector
  先跑起来，后面数据量上来再迁 Qdrant 也不迟。别过早优化。

  Rerank 位置 — 这是最有价值的一条。Rerank 必须在 Tool 内部完成，返回给 MasterBrain 的必须是精排后的
  Top-3~5。这个判断完全正确，原因他也说清楚了：SubAgentResultWrapper 拿到大量低质量片段会导致状态检测和 Hint
  生成混乱。推荐的链路 Embedding → Top-20 → Rerank → Top-3~5 → XML 可以直接采用。

  <knowledge_result> XML 标签 — 好建议。你的 SubAgentResultWrapper 通过 XML 标签检测结构化数据来判断 success
  状态，加一个 <knowledge_result> 标签并在 Wrapper 里补充识别逻辑，成本很低但能让 RAG 结果被正确分类。

  Skill 绑定知识库 — 选后者（知识库归属 SubAgent，Skill 只管 prompt 差异化）是对的。这和你现有的 AgentRuntime 绑
  kbLibraryId 的设计一致，不需要改架构。如果选前者（Skill 激活时自动检索注入 System Prompt），会让 Skill 从纯 prompt
  注入变成有副作用的执行单元，职责混乱。

  总结：这段分析可以全部接受，没有需要反驳的点。唯一的补充是向量数据库不用一步到位选 Qdrant，PgVector
  先上，后面按需迁移。

  > 你看看这段分析如何，只需要参考，不一定要全部接受：记忆系统有工具结果压缩逻辑（已消费的大工具返回值压缩节省
Token）。RAG 返回的 <knowledge_result> 里有 <source> 引用信息，压缩后溯源就丢了。建议在压缩逻辑里加一个豁免标记：
java// SummarizingTokenWindowChatMemory 压缩时
if (toolResult.contains("<knowledge_result>")) {
    // 保留 source，只压缩 content
    return compressContentOnly(toolResult);
}
不然用户问"你刚才说的来自哪篇文档"，系统答不上来。

● 这个分析点很好，但需要分两种情况看：

  对 Kortex 的 SummarizingTokenWindowChatMemory 来说 — 这个问题确实存在。Kortex 的工具结果压缩是在记忆窗口 Token
  超阈值时自动触发的，它会把已消费的大工具返回值压缩为摘要。如果 RAG 返回的 <knowledge_result> 被压缩了，<source>
  引用信息确实会丢失。

  但对你的 Omnitrix v2 来说 — 这个问题不严重，原因是你当前没有 Kortex 那套工具结果压缩逻辑。你的记忆系统是
  MemorySummarizer（对话摘要）+ MemoryExtractor（用户画像提取），不会动工具返回值本身。

  不过如果你未来借鉴 Kortex 的工具结果压缩（之前建议的 P1 优先级），那这个问题就会出现。到时候有两种处理方式：

  1. 他建议的豁免标记方式可以用，但粒度太粗 — 整个 <knowledge_result> 不压缩意味着大段文档片段一直占着 Token 窗口
  2. 更好的做法是：压缩 content，保留 source 元数据

  压缩前:
  <knowledge_result>
    <item source="《中国剪纸艺术》第3章" score="0.92">
      剪纸是中国最古老的民间艺术之一，距今已有1500多年历史...（500字）
    </item>
  </knowledge_result>

  压缩后:
  <knowledge_result compressed="true">
    <item source="《中国剪纸艺术》第3章" score="0.92">
      [已引用: 剪纸历史1500年，民间艺术]
    </item>
  </knowledge_result>

  这样用户问"来自哪篇文档"时 source 还在，但 content 已经被压缩成关键词摘要，Token 占用从 500+ 降到 20~30。

  总结：分析方向对，但解法可以更精细。不是"豁免不压缩"，而是"只压缩 content，保留 source"。
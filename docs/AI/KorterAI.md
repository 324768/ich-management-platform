Kortex AI 模块完整架构分析报告
一、模块总览
kortex-ai 是一个基于 LangChain4j 框架构建的企业级 AI 编排模块，核心采用 Master Brain（主脑）+ Sub-Agent（子代理） 的分层架构。底层 LLM 为 通义千问 (Qwen)，通过 DashScope API 接入。

顶层目录结构
com.kortex.ai
├── KortexAiApplication.java          # Spring Boot 启动类
├── dubbo/                             # Dubbo RPC 服务暴露
├── features/                          # 业务功能层
│   ├── agent/                         # 🧠 核心：Agent 编排框架
│   ├── common/                        # 通用工具（Artifact、网络搜索等）
│   ├── document/                      # 文档领域
│   ├── mail/                          # 邮件领域
│   └── matter/                        # 事项分析领域
└── infrastructure/                    # 基础设施层
    ├── core/                          # 上下文、工具注解
    ├── llm/                           # LLM 模型配置
    ├── mcp/                           # MCP 协议集成
    ├── memory/                        # 记忆管理（Redis + 摘要）
    ├── monitor/                       # 🔍 监控：Langfuse 追踪 + Prometheus 指标
    ├── remote/                        # Python 远程服务
    ├── scheduler/                     # 定时任务
    └── sse/                           # SSE 实时推送
二、核心架构：Master Brain + Sub-Agent
这是整个 AI 模块的灵魂设计。采用「纯编排模式」——主脑不直接持有任何具体工具，所有原子能力都封装在子代理中。

2.1 架构全景图
用户请求 (HTTP/SSE)
  ↓
AgentChatController (/agent/orchestrate/stream)
  ↓
OrchestratorService (主脑编排服务)
  ↓
MasterBrain (LangChain4j AiService, system prompt ~900 tokens + 动态注入)
  │
  ├── [CompositeToolProvider] ← 将两类 Agent 统一注册为 LLM 可调用工具
  │     │
  │     ├── SystemSubAgentToolProvider (系统内置 Agent, 编译时确定)
  │     │     ├── mail_assistant      → MailSystemSubAgent    (邮件搜索/阅读/回复)
  │     │     ├── document_assistant   → DocumentSystemSubAgent (文库搜索/文档操作)
  │     │     └── general_tools        → GeneralToolsSubAgent  (网络搜索/Artifact/翻译/代码执行)
  │     │
  │     └── DynamicSubAgentToolProvider (数据库动态 Agent, 运行时发现)
  │           └── 用户在后台配置的自定义 Agent (带知识库)
  │                └── AgentRuntime → KnowledgeSearchTool
  │
  └── 用户回复（主脑整合所有子代理结果后输出）
2.2 核心类详解
(1) OrchestratorService — 主脑编排入口
@/D:/jade/Java/kortex-ai-cloud/kortex-modules/kortex-ai/src/main/java/com/kortex/ai/features/agent/orchestrator/OrchestratorService.java:47-49

职责：接收用户请求，构建 MasterBrain 实例，调度子代理。

关键设计：

MasterBrain 接口定义了同步 chat() 和流式 chatStream() 两种模式
buildMasterBrain() 方法构建完整的 AiService，核心组件包括：
CompositeToolProvider：合并系统 Agent 和动态 Agent
systemMessageProvider：动态生成 System Prompt（Langfuse → Nacos 回退链）
SummarizingTokenWindowChatMemory：带摘要的 Token 窗口记忆
toolExecutionErrorHandler：结构化包装工具异常
AiServiceListener：注册 Langfuse 追踪 + 日志 + 指标监听
System Prompt 动态注入的内容：

[基础 Prompt ~900 tokens]
+ ## 当前时间 （始终注入）
+ ## 当前用户 （UserContextPromptEnricher）
+ ## 历史对话摘要 （Redis 存储，有则注入）
+ ## 已记录的关键发现 （笔记系统，有则注入）
Prompt 来源的三级回退链：

Langfuse 租户级 (tenant-{tenantId} label)
Langfuse production (production label)
Nacos 配置 (OrchestratorAiPromptProperties.masterBrainSystemMessage)
(2) SystemSubAgent 接口 — 系统内置子代理
@/D:/jade/Java/kortex-ai-cloud/kortex-modules/kortex-ai/src/main/java/com/kortex/ai/features/agent/orchestrator/system/SystemSubAgent.java:17-56

定义了 4 个核心方法：getAgentCode()、getAgentName()、getDescription()、execute()。

三个实现类：

子代理	类	AgentCode	核心能力
邮件助手	MailSystemSubAgent	mail_assistant	搜索邮件、阅读正文、撰写回复
文档助手	DocumentSystemSubAgent	document_assistant	文库搜索、文档内容查看
通用助手	GeneralToolsSubAgent	general_tools	网络搜索、Artifact 创建、翻译、代码执行、长文档生成
(3) SystemSubAgentToolProvider — 系统 Agent 工具注册
@/D:/jade/Java/kortex-ai-cloud/kortex-modules/kortex-ai/src/main/java/com/kortex/ai/features/agent/orchestrator/system/SystemSubAgentToolProvider.java:38-63

自动发现所有 SystemSubAgent Bean（Spring 自动注入 List<SystemSubAgent>）
将每个 Agent 包装为 ToolSpecification + ToolExecutor
每个工具统一接口：user_query（必填）、context（跨 Agent 传参）、show_result_to_user（SSE 直推）
(4) DynamicSubAgentToolProvider — 动态 Agent 工具注册
@/D:/jade/Java/kortex-ai-cloud/kortex-modules/kortex-ai/src/main/java/com/kortex/ai/features/agent/orchestrator/DynamicSubAgentToolProvider.java:32-91

通过 Dubbo RPC (RemoteAgentService) 从数据库查询所有启用的 Agent 配置
权限过滤：校验部门 + 角色权限，过滤掉用户无权访问的 Agent
创建 SubAgentTool（包装 AgentRuntime），支持结构化结果包装
版本感知：DynamicAgentFactory.getOrCreateRuntime() 会检查版本号，配置变更自动重建
(5) SubAgentTool — 动态 Agent 执行器
@/D:/jade/Java/kortex-ai-cloud/kortex-modules/kortex-ai/src/main/java/com/kortex/ai/features/agent/orchestrator/SubAgentTool.java:26-177

核心执行流程：

解析 user_query、context、show_result_to_user 参数
清洗 context（移除控制字符）+ 截断超长内容（防止 Token 溢出）
从 memoryId 构建子 Agent 的独立会话 ID（格式：userId:sessionId:sub-agent:agentCode）
传播上下文：BaseToolContext（SSE 推送 + 权限） + LangfuseTraceContext（追踪链路）
推送 agent_dispatch 事件（通知前端正在调度哪个 Agent）
调用 agentRuntime.chat() 执行
show_result_to_user=true 时，直接 SSE 推送结果给前端
通过 SubAgentResultWrapper.wrap() 结构化包装结果
(6) AgentRuntime — Agent 运行时
@/D:/jade/Java/kortex-ai-cloud/kortex-modules/kortex-ai/src/main/java/com/kortex/ai/features/agent/runtime/AgentRuntime.java:35-161

封装单个 Agent 的 配置 + 模型 + 记忆 + 工具
如果 Agent 关联了知识库（kbLibraryId 非空），自动注入 KnowledgeSearchTool
System Prompt = 数据库配置的 systemPrompt + 知识库搜索协议 + 用户上下文 + 笔记
缓存在 DynamicAgentFactory.runtimeCache 中，版本过期自动重建
(7) DynamicAgentFactory — 动态 Agent 工厂
@/D:/jade/Java/kortex-ai-cloud/kortex-modules/kortex-ai/src/main/java/com/kortex/ai/features/agent/runtime/DynamicAgentFactory.java:39-189

版本感知缓存：ConcurrentHashMap<agentCode, AgentRuntime>
getOrCreateRuntime()：先检查缓存 → 版本过期则移除 → RPC 获取最新配置 → 创建新 Runtime
routeToAgent()：基于关键词匹配的简单路由（用于非编排模式）
三、SubAgentResultWrapper — 结构化结果包装器
@/D:/jade/Java/kortex-ai-cloud/kortex-modules/kortex-ai/src/main/java/com/kortex/ai/features/agent/orchestrator/SubAgentResultWrapper.java:19-360

这是主脑决策的核心辅助类，纯字符串操作，零额外 LLM 调用。

3.1 工作流程
子代理原始返回 → analyzeStatus() → generateSummary() → generateHint() → XML 包装
3.2 状态检测 (analyzeStatus)
按优先级检测：

确定性标记（最高优先级）：[CLARIFICATION_SENT] → awaiting_user
部分结果标记：[注意：处理 → partial
错误标记：status="error" / Error: → error
结构化数据检测：含 <search_result、<mails> 等 XML 标签 → success（即使文本含"未找到"）
空结果兜底：短文本以"未找到"开头 → empty
默认：success
3.3 Hint 生成 (generateHint)
使用 ACTION/REASON/DO_NOT 三元组 格式，基于状态 + 调用次数 + 连续重试次数生成决策建议：

条件	ACTION	说明
awaiting_user	stop_immediately	用户在等回答，禁止继续调度
调用次数即将耗尽	reply_with_available_info	用已有信息回复
连续 3+ 次 empty/error	stop_and_report	强制停止
连续 2 次 empty/error	change_strategy_or_stop	换策略
success + 长文档完成	reply_to_user	不要再扩写
success + 连续多次	batch_remaining_items	批量处理
success + 有原始请求	task_completeness_review	对照原始请求逐条审查
partial	continue_same_agent	继续完成
error (配置/权限)	report_error_to_user	直接报错
empty (document)	retry_with_broader_keywords	扩大搜索范围
3.4 输出 XML 格式
xml
<agent_result status="success" agent="mail_assistant" call_number="1/10" shown_to_user="true">
  <summary>返回详细结果（1200 字符）</summary>
  <data>...子代理原始返回内容...</data>
  <hint>ACTION: task_completeness_review
ORIGINAL_REQUEST: 帮我查一下Jason的邮件并整理成文档
COMPLETED_AGENTS: 1/10
INSTRUCTION: 对照 ORIGINAL_REQUEST 逐条审查...
DO_NOT: 不要自行升级需求范围。</hint>
</agent_result>
四、AI 检测与打分功能（Langfuse LLM-as-a-Judge）
这是你提到的「AI 给 AI 自己的回答打分」的功能，通过 Langfuse 平台的评估器（Evaluator） 实现。

4.1 工作原理
用户请求 → Master Brain 处理 → 生成回复
                ↓
    LangfuseTracingListener 捕获全链路事件
                ↓
    上报 Trace (input + output + metadata) → Langfuse 平台
                ↓
    Langfuse 评估器 (LLM-as-a-Judge) 自动触发
                ↓
    用另一个 LLM 对 (input, output) 打分
4.2 核心实现
LangfuseTracingListener — 全链路追踪监听器
@/D:/jade/Java/kortex-ai-cloud/kortex-modules/kortex-ai/src/main/java/com/kortex/ai/infrastructure/monitor/listener/LangfuseTracingListener.java:52-797

Trace 层级模型：

Langfuse Session  ←  前端 sessionId
  └── Trace       ←  一次 orchestrateStream 调用
        ├── Generation  ←  Master Brain LLM 调用
        ├── Span        ←  Sub-Agent 调度
        │     ├── Generation  ←  Sub-Agent LLM 调用
        │     └── Span        ←  Sub-Agent 工具执行
        └── Span        ←  Master Brain 直接工具执行
关键设计点：

延迟 trace-create（第 379 行）：
Trace 的创建延迟到 onCompleted 时才发送
目的：确保 output 已填充，避免评估器在 output 为空时提前触发打 0 分
这正是「AI 给 AI 打分」功能的关键 bug fix
多级 output 回退（第 391-399 行）：
回退链：event.result() → 缓存的 generation output → 从 chat memory 读取最后一条 AI 消息
确保评估器始终能拿到有效 output
会话上下文注入 trace input（第 531-744 行）：
buildTraceInput() 将最近 6 条历史消息附加到 trace input
格式：[会话上下文]\nUser: xxx\nAI: xxx\n[当前问题]\n用户最新消息
目的：提升 LLM-as-a-Judge 评估精度，避免多轮对话中的追问被误判为幻觉
SSE 推送内容合并（第 403-419 行）：
show_result_to_user=true 时，子代理的内容通过 SSE 直推给前端
这些内容也记录到 trace output，否则审计人员在 Langfuse 只看到主脑简短点评
LangfuseIngestionClient — 异步批量上报
事件缓存在内存队列中，按 flushIntervalMs（默认 5s）/ maxBatchSize（默认 20）批量发送
HTTP 调用 Langfuse REST API (/api/public/ingestion)
LangfusePromptResolver — Prompt 版本关联
在 trace 中记录使用的 Prompt 名称和版本号（promptName + promptVersion）
支持 Langfuse 的 Linked Generations 功能，可对比不同 Prompt 版本的评分效果
4.3 评估器配置（Langfuse 平台侧）
评估器在 Langfuse 平台 UI 中配置（不在 Java 代码中），典型配置：

触发条件：新 Trace 创建且 output 非空
评估方式：LLM-as-a-Judge（用另一个 LLM 评估）
评估维度：正确性、完整性、相关性、幻觉检测等
评分范围：0-1 或自定义
4.4 其他监控能力
AiServiceMetricsListener — Prometheus 指标
@/D:/jade/Java/kortex-ai-cloud/kortex-modules/kortex-ai/src/main/java/com/kortex/ai/infrastructure/monitor/listener/AiServiceMetricsListener.java:27-191

采集的指标：

kortex.ai.service.invocations — 调用次数（started/success/failed）
kortex.ai.service.latency — 延迟
kortex.ai.service.tokens.input/output/total — Token 用量
kortex.ai.service.tool.calls — 工具调用次数
kortex.ai.service.errors — 错误次数
AiServiceObservabilityListener — 日志 + SSE 推送
@/D:/jade/Java/kortex-ai-cloud/kortex-modules/kortex-ai/src/main/java/com/kortex/ai/infrastructure/monitor/listener/AiServiceObservabilityListener.java:34-306

中文结构化日志（【AI调用开始】【AI响应】【AI调用工具】等）
SSE 工具结果推送（区分自管理工具 vs 通用推送）
异步笔记提取（ToolResultNoteExtractor）
五、提示词修改功能
5.1 提示词管理三级体系
优先级: Langfuse 租户级 > Langfuse production > Nacos 配置（兜底）
层级 1: Nacos 动态配置（默认）
配置类	配置前缀	作用
OrchestratorAiPromptProperties	kortex.ai.prompts.orchestrator	主脑 System Prompt
AgentAiPromptProperties	kortex.ai.prompts.agent	知识库搜索协议
MailAiPromptProperties	kortex.ai.prompts.mail	邮件助手 System Prompt
都标注了 @RefreshScope，Nacos 配置变更实时热刷新
无需重启服务即可修改提示词
主脑默认提示词：

@/D:/jade/Java/kortex-ai-cloud/kortex-modules/kortex-ai/src/main/java/com/kortex/ai/features/agent/orchestrator/config/OrchestratorAiPromptProperties.java:28-63

包含：角色定义、核心原则（直答/批量/上下文传递/按hint决策）、结果处理规则、多任务执行、整合输出规范、调用预算。

层级 2: Langfuse Prompt Management（可选覆盖）
@/D:/jade/Java/kortex-ai-cloud/kortex-modules/kortex-ai/src/main/java/com/kortex/ai/infrastructure/monitor/langfuse/LangfusePromptResolver.java:24-148

通过 langfuse.prompt-management.enabled=true 开启
支持多租户 Prompt 覆盖：每个租户可有独立的 Prompt 版本
内置 TTL 缓存（含负缓存），避免频繁 HTTP 调用
Prompt 名称常量：master-brain、document-chat、mail-qa、matter-analysis
支持 {{variable}} 占位符替换
层级 3: 数据库配置（动态 Agent）
用户自定义 Agent 的 systemPrompt 存储在数据库 (RemoteAgentVo.systemPrompt)
通过后台管理界面修改
DynamicAgentFactory 版本感知，配置变更自动重建 Runtime
六、记忆系统
6.1 SummarizingTokenWindowChatMemory — 带摘要的 Token 窗口记忆
@/D:/jade/Java/kortex-ai-cloud/kortex-modules/kortex-ai/src/main/java/com/kortex/ai/infrastructure/memory/redis/SummarizingTokenWindowChatMemory.java:42-650

核心特性：

Token 阈值触发摘要：当消息总 Token 超过 maxTokens × threshold（默认 80%）时，自动调用 LLM 生成摘要
摘要单独存储：摘要存到 Redis 独立 key (LLM:SESSION:{memoryId}:summary)，不受后续记忆压缩影响
一致性自愈：确保上下文始终以 UserMessage 开头（Qwen/DashScope 要求）
工具结果压缩：对已被 LLM 消费过的大工具返回值压缩为摘要（节省 ~80% Token）
中文感知 Token 估算：采样 CJK 字符比例，中文 ~1.5 token/字符
6.2 RedisChatMemoryStore — Redis 持久化
@/D:/jade/Java/kortex-ai-cloud/kortex-modules/kortex-ai/src/main/java/com/kortex/ai/infrastructure/memory/redis/RedisChatMemoryStore.java:26-250

Key 格式：LLM:SESSION:{userId}:{sessionId}:{scope}
三类数据：消息列表、摘要 (:summary)、笔记 (:notes)
TTL 24 小时，自动清理
6.3 ToolResultNoteExtractor — 笔记提取器
@/D:/jade/Java/kortex-ai-cloud/kortex-modules/kortex-ai/src/main/java/com/kortex/ai/infrastructure/memory/notes/ToolResultNoteExtractor.java:22-113

异步调用轻量 LLM (kortexQwenFastChatModel) 从大工具结果中提取关键信息
提取：人名、日期、数字、文件名、结论、状态
存入 Redis，注入到 System Prompt 的 ## 已记录的关键发现 段落
去重机制：已提取过相同标签的笔记不会重复提取
七、通用工具详解
@/D:/jade/Java/kortex-ai-cloud/kortex-modules/kortex-ai/src/main/java/com/kortex/ai/features/common/tool/

工具类	功能	说明
ArtifactDocumentTool	创建/更新/重写 Artifact	聊天中的虚拟文件，非文库文档
AskClarificationTool	向用户发送澄清问题	返回 [CLARIFICATION_SENT] 标记
LongFormWriteTool	长文档生成	极耗资源，需先确认，返回 [LONG_DOC_COMPLETE]
CommonMailSearchTool	邮件搜索（通用层）	跨场景可用
CommonContactSearchTool	联系人搜索	跨场景可用
FileOperationTool	文件操作	文件读写
RunCodeTool	代码执行	沙箱执行代码
工具注入机制：通过 @ToolScope 注解控制作用域（MAIL/DOCUMENT/COMMON），由 CompositeToolProvider 按场景过滤。

八、SSE 实时推送体系
事件类型
事件	推送时机	前端用途
agent_dispatch	主脑调度子代理时	显示"正在调用邮件助手..."
agent_result	show_result_to_user=true 时	直接展示子代理完整结果
tool_summary	子代理内部工具执行完毕	显示进度（工具名+截断结果）
component	网络搜索/文档搜索结果	前端渲染富组件卡片
chat/text	流式文本输出	逐字显示 AI 回复
九、API 接口
@/D:/jade/Java/kortex-ai-cloud/kortex-modules/kortex-ai/src/main/java/com/kortex/ai/features/agent/controller/AgentChatController.java:27-143

方法	路径	功能
POST	/agent/chat	直接对话（指定 agentCode）
POST	/agent/route	自动路由（关键词匹配 Agent）
POST	/agent/orchestrate	主脑编排（同步）
POST	/agent/orchestrate/stream	主脑编排（流式 SSE）
DELETE	/agent/memory	清除会话记忆
十、总结与关键设计亮点
10.1 架构优势
纯编排模式：主脑只做路由和决策，不直接持有工具，解耦清晰
双轨 Agent 发现：系统 Agent（编译时）+ 动态 Agent（运行时），兼顾稳定性和灵活性
结构化 Hint 机制：SubAgentResultWrapper 用 ACTION/REASON/DO_NOT 三元组引导主脑决策，减少 LLM 困惑
多层记忆系统：消息 + 摘要 + 笔记三层，兼顾上下文完整性和 Token 效率
全链路可观测：Langfuse 追踪 + Prometheus 指标 + 结构化日志
10.2 AI 检测/打分机制
通过 Langfuse 评估器实现 LLM-as-a-Judge
Java 端负责上报完整的 Trace（input + output + 会话上下文）
延迟 trace-create 确保 output 就绪，避免评估器误判
支持多维度评分和 Prompt 版本 A/B 对比
10.3 提示词热更新
Nacos @RefreshScope：配置变更实时生效
Langfuse Prompt Management：多租户覆盖 + 版本管理 + 效果对比
数据库：动态 Agent 的 Prompt 版本感知自动重载
# Omnitrix AI 模块完整技术文档 (V2)

本文档详细描述 `ich_omnitrix` 的完整架构与实现。覆盖 **V1（SubAgent+IntentRouter+Blackboard）** 和 **V2（LangChain4j MasterBrain+@Tool）** 双模式。依据本文档可从零复现整个 AI 模块。

**技术栈**: Java 17 / Spring Boot 3.x / LangChain4j 0.35.0 / Dubbo / MyBatis / Redis / MySQL / SSE

---

## 文档导航 (4部分 / 24章节)

### [Part 1: 架构与编排](./V2_Part1_架构与编排.md)
1. 系统概述与设计理念 (背景/双模式/三层路径/多层记忆/技术选型)
2. 整体架构设计 (架构图/V1流程/V2流程)
3. 项目结构与文件清单 (完整目录树)
4. 核心编排 OrchestratorService (32依赖/6入口/V1 doChatSync/V2 doChatSyncV2/invokeBrain/后处理/流式/配置类)

### [Part 2: Agent与Tool系统](./V2_Part2_Agent与Tool系统.md)
5. V1 SubAgent系统 (接口/Context/Registry/19个Agent实现/ToolSelector)
6. V2 LangChain4j架构 (AiServices原理/AiRequestContext ThreadLocal)
7. Brain接口与MasterBrainFactory (User/Admin/Ultra Brain/工厂构建)
8. Tool工具系统详解 (ToolRegistry/8个@Tool类方法清单)
9. 意图路由系统V1 (IntentRouter多层匹配/Admin/Ultra路由)
10. 黑板协作架构V1 (TaskBoard/TaskNode/多轮执行/再规划/Ultra L2)
11. Skill动态增强 (实体/7默认Skill/HeritageSkillPrompt/V1V2注入)

### [Part 3: 基础设施](./V2_Part3_基础设施.md)
12. LLM基础设施 (LlmProperties/LangChain4jConfig/LlmClient重试缓存/LlmStreamHandler think分流/熔断器)
13. 提示词工程 (PromptManager三层回退/PromptAssembler多层级拼接/PromptTemplate)
14. 记忆系统 (L1会话/L2摘要/L3长期画像/L4系统/RedisChatMemoryStore原子操作/MemoryExtractor/UserMemoryService)
15. 安全与护栏 (GuardrailsFilter输入输出/RateLimiter Lua限流/TokenBudget日预算)
16. 流式输出与SSE (SseEmitterManager/9种事件类型/think标签分流)
17. 遥测与监控 (TelemetryTracer/AiSelfEvaluator/CostTracker/TitleGenerator/健康检查)
18. 待确认操作PendingAction (两轮确认流程/PendingAction DTO/ActionExecutor/18种操作类型)

### [Part 4: API与部署](./V2_Part4_API与部署.md)
19. Ultra超级管理员模式 (认证/AI访问控制/L2黑板/V2 UltraTools)
20. Controller API接口参考 (6个Controller/完整端点表)
21. Entity与数据库Schema (12张表DDL/索引/原子递增)
22. 配置参考 (application.yml/Redis Key清单/线程池配置)
23. 部署与运维指南 (环境/构建/启动/数据库初始化/V1到V2切换)
24. 架构分析与最佳实践 (优势/劣势/性能优化/适用场景/复现指南)

---

**文档版本**: V2.0 | **最后更新**: 2025年 | **模块**: ich_omnitrix

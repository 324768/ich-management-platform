# OmniTrix AI 架构详解：Skill 与 SubAgent 的关系

## 一、整体架构图

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                                    用户请求                                      │
└─────────────────────────────────────┬───────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                               OrchestratorService                              │
│  ┌─────────────────────────────────────────────────────────────────────────┐  │
│  │  Step 0: 安全检查（GuardrailsFilter + RateLimiter + TokenBudget）        │  │
│  └─────────────────────────────────────────────────────────────────────────┘  │
│                                      │                                          │
│                                      ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────────────┐  │
│  │  Step 1: MasterBrain.shouldUseLlmBrain() 决定是否需要LLM驱动            │  │
│  └─────────────────────────────────────────────────────────────────────────┘  │
│                                      │                                          │
│                    ┌─────────────────┴─────────────────┐                      │
│                    │ YES                                   │ NO               │
│                    ▼                                       ▼                   │
│  ┌────────────────────────────┐    ┌────────────────────────────────────────┐ │
│  │    MasterBrain.decide()    │    │  意图路由 IntentRouter.route()        │ │
│  │  (LLM驱动的智能决策)        │    │  快速路径：直接选择SubAgent           │ │
│  └─────────────┬──────────────┘    └───────────────────┬──────────────────┘ │
│                │                                        │                     │
│                ▼                                        ▼                     │
│  ┌─────────────────────────────────────────────────────────────────────────┐  │
│  │                    MasterBrain 决策结果                                  │  │
│  │  ┌──────────────────────────────────────────────────────────────────┐   │  │
│  │  │  path: "quick_path"      path: "blackboard"                     │   │  │
│  │  │  agent: "xxx"           tasks: [                                 │   │  │
│  │  │  reason: "..."          {                                       │   │  │
│  │  │                           agent: "content_assistant",           │   │  │
│  │  │                           query: "...",                         │   │  │
│  │  │                           skills: ["heritage_master"]            │   │  │
│  │  │                         }                                        │   │  │
│  │  │                       ]                                           │   │  │
│  │  └──────────────────────────────────────────────────────────────────┘   │  │
│  └─────────────────────────────────────────────────────────────────────────┘  │
│                                      │                                          │
│                                      ▼                                          │
└─────────────────────────────────────┬───────────────────────────────────────────┘
                                      │
                    ┌─────────────────┴─────────────────┐
                    │                                 │
                    ▼                                 ▼
┌─────────────────────────────────┐   ┌─────────────────────────────────────────┐
│      Quick Path (快速路径)        │   │       Blackboard Path (黑板路径)        │
│                                 │   │                                          │
│  1. SubAgentRegistry.get()      │   │  1. TaskBoard 创建任务节点              │
│  2. executeAgent() 执行          │   │     - agent: "content_assistant"        │
│  3. PromptAssembler 组装        │   │     - query: "搜索剪纸"                 │
│  4. LLM 调用                    │   │     - skills: ["heritage_master"]        │
│                                 │   │                                          │
│  Skill注入方式:                  │   │  2. 并行/串行执行任务                    │
│  - ContentSubAgent 自带Skill    │   │     - AgentQueryResult                  │
│  - PromptAssembler 动态注入      │   │                                          │
└─────────────────────────────────┘   │  3. PromptAssembler 组装                │
                                        │     - 从TaskBoard获取skills             │
                                        │     - 注入到最终Prompt                  │
                                        │  4. LLM 调用                            │
                                        └─────────────────────────────────────────┘
```

## 二、Skill 与 SubAgent 的映射关系

### 情况一：Skill 有对应的 SubAgent

```
用户: "帮我推荐一个适合送老人的礼物"
                    │
                    ▼
MasterBrain 决策:
  path: "quick_path"
  agent: "commerce_assistant"
  skills: ["shopping_advisor"]
                    │
                    ▼
SubAgentRegistry.get("commerce_assistant")
  → CommerceSubAgent (包含购物相关工具)
                    │
                    ▼
CommerceSubAgent.execute()
  → 搜索商品、调用工具
                    │
                    ▼
PromptAssembler.assemble()
  → 注入 "shopping_advisor" Skill
  → LLM 生成回答
```

### 情况二：Skill 没有对应的 SubAgent（通用Agent + Skill）

```
用户: "什么是昆曲？"
                    │
                    ▼
MasterBrain 决策:
  path: "quick_path"
  agent: "content_assistant"
  skills: ["heritage_master"]
                    │
                    ▼
SubAgentRegistry.get("content_assistant")
  → ContentSubAgent (通用内容助手)
                    │
                    ▼
ContentSubAgent.execute()
  → 搜索相关内容
                    │
                    ▼
PromptAssembler.assemble()
  → 注入 "heritage_master" Skill
  → LLM 回答时已经具备"非遗大师"能力
```

### 情况三：多Skill + 复杂任务（黑板模式）

```
用户: "帮我找剪纸相关的活动和产品"
                    │
                    ▼
MasterBrain 决策:
  path: "blackboard"
  tasks: [
    {
      agent: "content_assistant",
      query: "搜索剪纸活动",
      skills: ["heritage_master"]
    },
    {
      agent: "commerce_assistant",
      query: "搜索剪纸文创",
      skills: ["shopping_advisor"]
    }
  ]
                    │
                    ▼
TaskBoard 创建:
  task_1 → content_assistant + heritage_master
  task_2 → commerce_assistant + shopping_advisor
                    │
                    ▼
并行执行任务 + 收集结果
                    │
                    ▼
PromptAssembler 组装最终Prompt
  → 合并所有Skill
  → LLM 生成综合回答
```

## 三、Skill 注入的详细流程

```
┌─────────────────────────────────────────────────────────────────┐
│                    PromptAssembler 组装流程                       │
└─────────────────────────────────────┬───────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────┐
│  层级1: 基础Prompt                                            │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │ promptManager.resolve("master_brain_system")              │  │
│  └─────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────┬───────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────┐
│  层级1b: 动态注入Skills                                        │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │ if (taskBoard != null) {                                │  │
│  │     // 黑板模式：从TaskBoard获取Skills                   │  │
│  │     skills = getSkillsFromTaskBoard(taskBoard, agent)   │  │
│  │ } else {                                                │  │
│  │     // QuickPath：从关键词推断Skills                      │  │
│  │     skills = skillPromptConfig.getSkillsByKeywords()    │  │
│  │ }                                                        │  │
│  │                                                          │  │
│  │ skillPrompts = combineSkillPrompts(skills)              │  │
│  └─────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────┬───────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────┐
│  层级2: 当前时间 + 用户信息 + 用户画像                          │
└─────────────────────────────────────┬───────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────┐
│  层级3: 子代理角色                                             │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │ subAgent.getAgentPrompt()                               │  │
│  │ (ContentSubAgent 自带 heritage_master)                   │  │
│  └─────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────┬───────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────┐
│  层级4: 查询结果注入                                           │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │ queryResult.toPromptInjection()                         │  │
│  └─────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────┬───────────────────────────┘
                                      │
                                      ▼
                    ┌───────────────────────────────┐
                    │         最终System Prompt       │
                    │  = 层级1 + 层级1b + 层级2     │
                    │    + 层级3 + 层级4             │
                    └───────────────────────────────┘
```

## 四、没有对应 SubAgent 时的问题与解决方案

### 问题分析

当前架构中，有些Skill没有对应的专用SubAgent：

| Skill | 有对应SubAgent? | 当前处理方式 |
|-------|----------------|-------------|
| heritage_master | ✅ ContentSubAgent | 静态注入到AgentPrompt |
| shopping_advisor | ✅ CommerceSubAgent | 通过TaskBoard动态注入 |
| customer_service | ✅ UserSubAgent | 静态注入 |
| knowledge_expert | ❌ 无 | 需要解决 |
| recommend_expert | ✅ RecommendSubAgent | 已有 |
| security_audit | ❌ 无 | 需要解决 |
| quality_evaluator | ❌ 无 | 需要解决 |

### 解决方案

#### 方案一：使用通用Agent + Skill（推荐）

```
当Skill没有对应SubAgent时：
  agent: "general_assistant" (通用助手)
  skills: ["knowledge_expert"]
                    │
                    ▼
general_assistant.execute()
  → 不执行具体业务操作
  → 直接进行下一步
                    │
                    ▼
PromptAssembler 组装
  → 注入 "knowledge_expert" Skill
  → LLM 直接基于Skill能力回答
```

#### 方案二：为每个Skill创建对应的Agent

```
knowledge_expert Skill
  → KnowledgeSubAgent (新创建)
    - 实现知识库查询接口
    - getAgentPrompt() 包含 Skill
```

#### 方案三：Skill直接作为Prompt增强（最简单）

```
某些Skill不需要Agent执行：
  1. MasterBrain 决策使用 Skill
  2. 直接注入到 PromptAssembler
  3. LLM 基于 Skill 能力直接回答

适用场景：
  - knowledge_expert (纯问答)
  - quality_evaluator (评估)
  - security_audit (审核)
```

## 五、完整的Skill调用流程图

```
用户请求
    │
    ▼
┌─────────────────────────────────────────────────────────────────┐
│                     MasterBrain 决策                            │
│                                                                  │
│  输入: 用户问题 + 可用的SubAgents + 可用的Skills                 │
│  输出: path + agent + tasks + skills                           │
│                                                                  │
│  决策规则:                                                       │
│  1. 简单问题 → quick_path + agent + skill                      │
│  2. 复杂问题 → blackboard + tasks[] + skills[]                  │
│  3. Skill优先匹配同领域Agent                                     │
│  4. 无对应Agent时使用general_assistant + skill                  │
└─────────────────────────────────────┬───────────────────────────┘
                                    │
            ┌───────────────────────┼───────────────────────┐
            │                       │                       │
            ▼                       ▼                       ▼
    ┌───────────────┐     ┌───────────────┐     ┌───────────────┐
    │ Quick Path    │     │ Blackboard    │     │ Fallback      │
    │               │     │               │     │               │
    │ Agent + Skill │     │ Tasks + Skills│     │ general_      │
    │               │     │               │     │ assistant +   │
    │               │     │               │     │ Skill         │
    └───────┬───────┘     └───────┬───────┘     └───────┬───────┘
            │                     │                     │
            └─────────────────────┼─────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                  PromptAssembler 组装                            │
│                                                                  │
│  1. 基础Prompt                                                  │
│  2. Skills动态注入 ← 核心！                                      │
│  3. 用户画像                                                    │
│  4. Agent角色                                                  │
│  5. 查询结果                                                   │
└─────────────────────────────────────┬───────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                        LLM 调用                                 │
│                                                                  │
│  LLM 具备 Skill 能力后:                                         │
│  - heritage_master → 非遗大师回答                               │
│  - shopping_advisor → 专业购物顾问                              │
│  - knowledge_expert → 知识库问答                                │
│  - quality_evaluator → 质量评估                                │
└─────────────────────────────────────┬───────────────────────────┘
                                  │
                                  ▼
                    ┌───────────────────────────────┐
                    │         最终回答               │
                    └───────────────────────────────┘
```

## 六、关键技术点

### 1. Skill的两种注入方式

```java
// 方式一：静态注入（Agent自带）
public String getAgentPrompt() {
    return HeritageSkillPrompt.getHeritageMasterPrompt() + businessContext;
}

// 方式二：动态注入（PromptAssembler）
if (taskBoard != null) {
    List<Skill> skills = getSkillsFromTaskBoard(taskBoard, agentCode);
    sb.append(combineSkillPrompts(skills));
}
```

### 2. Skill选择策略

```java
// MasterBrain中的决策
- 如果问题是纯问答（什么是/为什么）→ knowledge_expert
- 如果问题涉及商品 → shopping_advisor
- 如果问题涉及非遗 → heritage_master
- 如果没有对应Agent → general_assistant + skill
```

### 3. 无对应Agent时的fallback

```java
// SubAgentRegistry.getOrDefault()
public SubAgent getOrDefault(String code) {
    SubAgent agent = get(code);
    if (agent == null) {
        agent = systemAgents.get("general_assistant"); // Fallback
    }
    return agent;
}
```

## 七、总结

1. **Skill是Prompt增强**：不改变Agent的执行逻辑，只改变LLM的"能力"
2. **Agent执行 vs Skill增强**：
   - Agent：执行具体业务操作（搜索、查询）
   - Skill：赋予LLM特定领域的专业知识
3. **最佳实践**：
   - 有专用Agent → Agent + Skill组合
   - 无专用Agent → general_assistant + Skill
4. **核心是PromptAssembler**：无论哪种方式，最终Skill都会注入到Prompt中

---
*文档版本: 1.0*
*最后更新: 2026-03-11*

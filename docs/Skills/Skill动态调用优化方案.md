# Skill 动态调用优化方案

## 一、当前问题

### 现有架构的问题

```
当前架构：
- Skill选择 → MasterBrain (LLM) 判断
- Agent选择 → MasterBrain (LLM) 判断  
- Skill注入 → PromptAssembler (规则) 注入

问题：
- LLM 判断使用哪些Skills，但不知道这些Skills是否有对应Agent
- 只能由开发者预设Skill-Agent映射关系
- 缺乏灵活性，无法智能处理新添加的Skills
```

## 二、改进方案：由LLM统一决策

### 设计思路

```
改进后架构：
- Skill选择 → LLM 判断
- Agent选择 → LLM 判断  
- Skill处理方式 → LLM 判断（核心改进点！）

LLM决策内容：
1. 要使用哪些Skills
2. 如何处理每个Skill：
   - 方式A：SubAgent执行 + Skill增强
   - 方式B：通用Agent + Skill增强
   - 方式C：LLM直接基于Skill回答（无Agent）
```

### LLM决策Prompt设计

```java
// 改进后的MasterBrain决策Prompt
private static final String DECISION_PROMPT = """
        你是一个智能任务规划专家。分析用户请求，决定最佳执行策略。

        【可用的执行路径】
        1. agent_skill：专用Agent执行 + Skill增强（推荐）
        2. general_skill：通用Agent + Skill增强
        3. direct_skill：LLM直接基于Skill回答（无Agent执行）

        【可用的SubAgents】
        - content_assistant: 非遗项目/传承人/活动相关
        - commerce_assistant: 商品、订单相关
        - user_assistant: 用户信息相关
        - recommend_assistant: 推荐相关
        - knowledge_assistant: 知识问答相关
        - general_assistant: 通用闲聊

        【可用的Skills】
        - heritage_master: 非遗文化大师
        - shopping_advisor: 购物顾问
        - customer_service: 客服话术师
        - knowledge_expert: 知识百科达人
        - recommend_expert: 推荐解读者
        - security_audit: 安全审核员
        - quality_evaluator: 质量评估师

        【决策规则】
        1. 如果问题需要执行具体操作（搜索、查询、下单）→ 使用专用Agent
        2. 如果问题只需要Skill能力回答 → 直接Skill回答
        3. 如果Skill有对应Agent → Agent + Skill
        4. 如果Skill没有对应Agent → 通用Agent + Skill 或 直接Skill回答

        【决策示例】
        用户: "什么是昆曲"
        → 直接Skill回答: path="direct_skill", skills=["heritage_master"], reason="纯知识问答"

        用户: "帮我找剪纸活动"
        → Agent执行: path="agent_skill", agent="content_assistant", skills=["heritage_master"], reason="需要查询活动"

        用户: "这个商品怎么样"
        → Agent执行: path="agent_skill", agent="commerce_assistant", skills=["shopping_advisor"]

        用户: "如何成为会员"
        → 通用Agent: path="general_skill", agent="general_assistant", skills=["knowledge_expert"], reason="知识问答"

        【输出格式】（严格JSON）
        {
            "path": "agent_skill" | "general_skill" | "direct_skill",
            "agent": "agent_code" (agent_skill/general_skill时需要),
            "query": "处理问题的指令" (传给Agent),
            "skills": ["skill_id1", "skill_id2"],
            "reason": "决策理由"
        }

        【用户请求】
        %s

        JSON:""";
```

## 三、决策流程

```
用户请求: "什么是昆曲"
              │
              ▼
    ┌───────────────────┐
    │   LLM 决策引擎    │
    └─────────┬─────────┘
              │
              ▼
    ┌───────────────────┐
    │ 分析用户问题类型   │
    └─────────┬─────────┘
              │
    ┌─────────┴─────────┐
    │                   │
    ▼                   ▼
┌─────────────┐   ┌─────────────┐
│ 需要执行操作？│   │ 纯知识问答？│
└──────┬──────┘   └──────┬──────┘
       │                  │
       ▼                  ▼
  Agent + Skill     Direct Skill
       │                  │
       ▼                  ▼
┌─────────────┐   ┌─────────────┐
│ 执行Agent   │   │ LLM直接回答 │
│ 查询数据    │   │ 具备Skill  │
│ + Skill增强 │   │ 能力       │
└─────────────┘   └─────────────┘
```

## 四、代码实现

### 1. 扩展Decision类

```java
// MasterBrain.java
@Data
public static class Decision {
    public enum Path { 
        AGENT_SKILL,    // 专用Agent + Skill
        GENERAL_SKILL,   // 通用Agent + Skill  
        DIRECT_SKILL     // LLM直接基于Skill回答
    }

    private Path path;
    private String agent;        // AGENT_SKILL/GENERAL_SKILL时使用
    private String query;       // 处理问题的指令
    private List<String> skills; // 需要启用的Skills
    private String reason;

    // 兼容旧版本
    public List<Task> getTasks() {
        if (path == Path.BLACKBOARD) {
            // 转换为旧格式
            return null;
        }
        return null;
    }
}
```

### 2. 修改OrchestratorService

```java
// OrchestratorService.java

private ChatResponse doChatSync(...) {
    // ... 现有逻辑 ...
    
    // MasterBrain决策
    Decision decision = masterBrain.decide(userMessage, isAdmin);
    
    // 根据决策类型处理
    switch (decision.getPath()) {
        case AGENT_SKILL:
            return executeAgentSkill(decision, ...);
        case GENERAL_SKILL:
            return executeGeneralSkill(decision, ...);
        case DIRECT_SKILL:
            return executeDirectSkill(decision, ...);
    }
}

// 专用Agent + Skill
private ChatResponse executeAgentSkill(Decision decision, ...) {
    SubAgent agent = subAgentRegistry.get(decision.getAgent());
    AgentQueryResult queryResult = executeAgent(agent, decision.getQuery(), context);
    String systemPrompt = promptAssembler.assembleWithSkills(
        context, agent, queryResult, summary, userProfile, decision.getSkills());
    // ... 调用LLM
}

// 通用Agent + Skill
private ChatResponse executeGeneralSkill(Decision decision, ...) {
    SubAgent agent = subAgentRegistry.get("general_assistant");
    // 不执行具体操作，直接进入LLM调用
    String systemPrompt = promptAssembler.assembleWithSkills(
        context, agent, null, summary, userProfile, decision.getSkills());
    // ... 调用LLM
}

// LLM直接基于Skill回答
private ChatResponse executeDirectSkill(Decision decision, ...) {
    // 不需要Agent，直接组装Prompt + Skills
    SubAgent agent = subAgentRegistry.get("general_assistant");
    String systemPrompt = promptAssembler.assembleWithSkills(
        context, agent, null, summary, userProfile, decision.getSkills());
    // ... 直接调用LLM，不执行Agent
}
```

### 3. PromptAssembler增强

```java
// PromptAssembler.java

/**
 * 组装包含Skills的Prompt（不执行Agent）
 */
public String assembleWithSkills(AgentContext context, SubAgent agent,
                                 AgentQueryResult queryResult, String summary,
                                 String userProfile, List<String> skillIds) {
    StringBuilder sb = new StringBuilder();
    
    // 1. 基础Prompt
    sb.append(promptManager.resolve("master_brain_system"));
    
    // 2. 注入Skills（核心！）
    if (skillIds != null && !skillIds.isEmpty()) {
        List<Skill> skills = skillPromptConfig.getSkills(skillIds);
        String skillPrompts = combineSkillPrompts(skills);
        sb.append("\n\n").append(skillPrompts);
    }
    
    // 3. 用户信息
    // ... 
    
    // 4. Agent角色（可选）
    if (agent != null && agent.getAgentPrompt() != null) {
        sb.append("\n\n").append(agent.getAgentPrompt());
    }
    
    // 5. 查询结果（可选）
    if (queryResult != null) {
        sb.append("\n\n").append(queryResult.toPromptInjection());
    }
    
    return sb.toString();
}
```

## 五、优势分析

### vs 旧方案

| 方面 | 旧方案 | 新方案 |
|------|--------|--------|
| 灵活性 | 固定Skill-Agent映射 | LLM动态决策 |
| 扩展性 | 新Skill需要修改代码 | 自动适配 |
| 智能化 | 规则判断 | LLM智能选择 |
| 维护性 | 映射关系复杂 | 统一由LLM决策 |

### 适用场景

```
DIRECT_SKILL（直接Skill回答）
✓ "什么是昆曲" → heritage_master
✓ "苏绣和湘绣有什么区别" → heritage_master  
✓ "这个会员规则什么意思" → knowledge_expert

AGENT_SKILL（Agent + Skill）
✓ "帮我找剪纸活动" → content_assistant + heritage_master
✓ "推荐一个礼物" → commerce_assistant + shopping_advisor

GENERAL_SKILL（通用Agent + Skill）
✓ "怎么使用平台" → general_assistant + knowledge_expert
✓ "推荐一些内容" → general_assistant + recommend_expert
```

## 六、总结

```
改进后的架构优势：

1. 更灵活：LLM可以根据问题类型智能选择处理方式
2. 更扩展：新Skill无需修改代码，LLM自动适配
3. 更自然：模拟人类助手思维方式，先理解问题再决定如何回答
4. 更解耦：Skill和Agent完全独立，按需组合

核心改进点：
- 让LLM判断"如何处理Skill"而非预设规则
- 三种处理方式：专用Agent / 通用Agent / 直接回答
- 完全释放Skill的灵活性
```

---

*方案版本: 1.0*
*最后更新: 2026-03-11*

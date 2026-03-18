# Skill动态选择系统实现计划

> **对于代理工作者：** 必需：使用 superpowers:subagent-driven-development（如果有子代理）或 superpowers:executing-plans 来实现此计划。步骤使用复选框（`- [ ]`）语法进行跟踪。

**目标：** 将Skill从静态全量注入改为动态按需选择，参考Claude的Skill自激活机制，让AI自主判断需要启用哪些Skill

**架构：** 
1. 新增SkillSelector组件，负责根据用户意图语义分析选择需要的Skill
2. 修改MasterBrainFactory，支持动态构建Skill Prompt
3. 在System Prompt中添加Skill选择指导，让AI知道如何按需调用

**技术栈：** LangChain4j, LLM辅助决策, Spring Boot

---

## 任务 1: 创建Skill选择器组件

**文件：**
- 创建：`ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/infrastructure/skill/SkillSelector.java`
- 修改：`ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/infrastructure/skill/SkillPromptConfig.java`
- 测试：`ich_parent/ich_omnitrix/src/test/java/com/hyang/ich/omnitrix/infrastructure/skill/SkillSelectorTest.java`

- [ ] **步骤 1: 创建SkillSelector.java**

```java
package com.hyang.ich.omnitrix.infrastructure.skill;

import com.hyang.ich.omnitrix.infrastructure.llm.LlmClient;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Skill选择器 — 动态根据用户意图选择需要启用的Skills
 * 参考Claude Skill自激活机制：AI根据任务语义自主判断是否启用相关Skill
 */
@Slf4j
@Component
public class SkillSelector {

    private final SkillPromptConfig skillPromptConfig;
    private final LlmClient llmClient;

    // 最大选择Skill数量，防止过多注入
    private static final int MAX_SELECTED_SKILLS = 3;

    public SkillSelector(SkillPromptConfig skillPromptConfig, LlmClient llmClient) {
        this.skillPromptConfig = skillPromptConfig;
        this.llmClient = llmClient;
    }

    /**
     * 根据用户消息动态选择需要启用的Skills
     * @param userMessage 用户消息
     * @return 选中的Skill列表（按相关性排序）
     */
    public List<Skill> selectSkills(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // 第一步：快速关键词匹配（过滤明显不相关的Skills）
        List<Skill> keywordMatched = skillPromptConfig.getSkillsByKeywords(userMessage);
        
        // 如果关键词匹配结果过多，使用LLM精细选择
        if (keywordMatched.size() > MAX_SELECTED_SKILLS) {
            return selectWithLLM(userMessage, keywordMatched);
        }
        
        // 如果关键词匹配结果在合理范围内，返回结果
        // 但限制最大数量
        return keywordMatched.stream()
                .limit(MAX_SELECTED_SKILLS)
                .collect(Collectors.toList());
    }

    /**
     * 使用LLM进行精细的Skill选择（当候选Skills过多时）
     */
    private List<Skill> selectWithLLM(String userMessage, List<Skill> candidates) {
        String prompt = buildSkillSelectionPrompt(userMessage, candidates);
        
        try {
            LlmResponse response = llmClient.chatAuxiliaryJson(prompt, new ArrayList<>(), "Skill选择");
            String content = response.getContent();
            
            if (content == null || content.trim().isEmpty()) {
                log.debug("LLM选择Skill返回为空，回退到关键词匹配");
                return candidates.stream().limit(MAX_SELECTED_SKILLS).collect(Collectors.toList());
            }
            
            return parseSelectedSkills(content, candidates);
        } catch (Exception e) {
            log.warn("LLM选择Skill失败，回退到关键词匹配: {}", e.getMessage());
            return candidates.stream().limit(MAX_SELECTED_SKILLS).collect(Collectors.toList());
        }
    }

    /**
     * 构建Skill选择Prompt
     */
    private String buildSkillSelectionPrompt(String userMessage, List<Skill> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个Skill选择专家。根据用户的问题，从候选Skills中选择最合适的几个。\n\n");
        sb.append("【用户问题】\n").append(userMessage).append("\n\n");
        sb.append("【候选Skills】\n");
        for (Skill skill : candidates) {
            sb.append("- ID: ").append(skill.getId()).append("\n");
            sb.append("  名称: ").append(skill.getName()).append("\n");
            sb.append("  描述: ").append(skill.getDescription()).append("\n");
            sb.append("  关键词: ").append(String.join(", ", skill.getKeywords())).append("\n\n");
        }
        sb.append("【选择原则】\n");
        sb.append("1. 选择最相关的Skills，最多不超过").append(MAX_SELECTED_SKILLS).append("个\n");
        sb.append("2. 如果用户问题不需要任何Skill，返回空列表\n");
        sb.append("3. 优先选择能直接帮助回答问题的Skill\n\n");
        sb.append("【输出格式】（严格JSON数组）\n");
        sb.append("选中: [\"skill_id1\", \"skill_id2\"]\n");
        sb.append("不选: []\n\n");
        sb.append("JSON数组: ");
        
        return sb.toString();
    }

    /**
     * 解析LLM返回的选中Skill IDs
     */
    private List<Skill> parseSelectedSkills(String content, List<Skill> candidates) {
        try {
            // 简单解析：提取数组中的ID
            content = content.trim();
            if (content.startsWith("[")) {
                // 解析JSON数组
                List<String> selectedIds = new ArrayList<>();
                String[] parts = content.replace("[", "").replace("]", "").split(",");
                for (String part : parts) {
                    String id = part.replace("\"", "").trim();
                    if (!id.isEmpty()) {
                        selectedIds.add(id);
                    }
                }
                
                // 映射回Skill对象
                Map<String, Skill> skillMap = candidates.stream()
                        .collect(Collectors.toMap(Skill::getId, s -> s));
                
                return selectedIds.stream()
                        .filter(skillMap::containsKey)
                        .map(skillMap::get)
                        .limit(MAX_SELECTED_SKILLS)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.debug("解析Skill选择结果失败: {}", e.getMessage());
        }
        
        // 解析失败，回退到关键词匹配
        return candidates.stream().limit(MAX_SELECTED_SKILLS).collect(Collectors.toList());
    }

    /**
     * 构建选中的Skills的Prompt（供MasterBrain使用）
     */
    public String buildSelectedSkillsPrompt(String userMessage) {
        List<Skill> selected = selectSkills(userMessage);
        if (selected.isEmpty()) {
            return "";
        }
        
        return selected.stream()
                .map(Skill::getSystemPrompt)
                .collect(Collectors.joining("\n\n"));
    }
}
```

- [ ] **步骤 2: 运行测试验证编译**

运行：`mvn compile -pl ich_parent/ich_omnitrix -am`
预期：编译成功，无错误

- [ ] **步骤 3: 添加单元测试**

```java
package com.hyang.ich.omnitrix.infrastructure.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SkillSelectorTest {

    @Mock
    private SkillPromptConfig skillPromptConfig;

    @Mock
    private LlmClient llmClient;

    @InjectMocks
    private SkillSelector skillSelector;

    @Test
    void testSelectSkills_emptyMessage() {
        List<Skill> result = skillSelector.selectSkills("");
        assertTrue(result.isEmpty());
    }

    @Test
    void testSelectSkills_keywordMatch() {
        Skill heritageSkill = new Skill("heritage_master", "非遗文化大师", 
            "回答非遗问题", "system prompt", 
            new String[]{"非遗", "传承人", "文化"}, true);
        
        Skill shoppingSkill = new Skill("shopping_advisor", "购物顾问",
            "购物咨询", "system prompt",
            new String[]{"购买", "商品"}, true);

        when(skillPromptConfig.getSkillsByKeywords("我想了解非遗"))
            .thenReturn(Arrays.asList(heritageSkill));

        List<Skill> result = skillSelector.selectSkills("我想了解非遗");
        
        assertEquals(1, result.size());
        assertEquals("heritage_master", result.get(0).getId());
    }

    @Test
    void testBuildSelectedSkillsPrompt() {
        Skill skill = new Skill("test", "测试", "测试", "test prompt", null, true);
        when(skillPromptConfig.getSkillsByKeywords(any()))
            .thenReturn(Arrays.asList(skill));

        String prompt = skillSelector.buildSelectedSkillsPrompt("test query");
        
        assertEquals("test prompt", prompt);
    }
}
```

- [ ] **步骤 4: 提交**

```bash
git add ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/infrastructure/skill/SkillSelector.java
git commit -m "feat: add SkillSelector for dynamic skill selection"
```

---

## 任务 2: 修改MasterBrainFactory支持动态Skill选择

**文件：**
- 修改：`ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/brain/MasterBrainFactory.java`

- [ ] **步骤 1: 添加SkillSelector依赖和动态选择方法**

修改 `MasterBrainFactory.java`:

```java
// 在成员变量区域添加
private final SkillSelector skillSelector;

// 构造函数添加
public MasterBrainFactory(..., SkillSelector skillSelector, ...) {
    ...
    this.skillSelector = skillSelector;
}

/**
 * 构建动态Skill Prompt — 根据用户消息按需选择Skills
 * 替代原来的 buildSkillsPrompt() 全量注入方式
 */
public String buildDynamicSkillsPrompt(String userMessage) {
    if (userMessage == null || userMessage.trim().isEmpty()) {
        return "";
    }
    return skillSelector.buildSelectedSkillsPrompt(userMessage);
}

/**
 * 保留原有方法用于向后兼容（可选：标记为@Deprecated）
 */
@Deprecated
public String buildSkillsPrompt() {
    List<Skill> enabledSkills = skillPromptConfig.getAllEnabledSkills();
    if (enabledSkills.isEmpty()) {
        return "";
    }
    return enabledSkills.stream()
            .map(Skill::getSystemPrompt)
            .collect(Collectors.joining("\n\n"));
}
```

- [ ] **步骤 2: 修改OrchestratorService使用动态Skill选择**

修改 `OrchestratorService.java` 中的 `doChatSync` 和 `doChatStream` 方法：

```java
// 在 invokeBrainSync 方法中，修改 skills 构建
String skills = masterBrainFactory.buildDynamicSkillsPrompt(userMessage);
// 原来: String skills = masterBrainFactory.buildSkillsPrompt();

// 在 buildTokenStream 方法中同样修改
String skills = masterBrainFactory.buildDynamicSkillsPrompt(userMessage);
```

- [ ] **步骤 3: 运行测试**

运行：`mvn test -pl ich_parent/ich_omnitrix -Dtest=MasterBrainFactoryTest`
预期：测试通过

- [ ] **步骤 4: 提交**

```bash
git add ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/brain/MasterBrainFactory.java
git add ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/orchestrator/OrchestratorService.java
git commit -m "feat: integrate dynamic Skill selection in MasterBrainFactory"
```

---

## 任务 3: 更新System Prompt指导AI按需使用Skills

**文件：**
- 修改：`ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/brain/UserMasterBrain.java`
- 修改：`ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/brain/AdminMasterBrain.java`
- 修改：`ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/brain/UltraMasterBrain.java`

- [ ] **步骤 1: 添加Skill使用指导到System Message**

在每个MasterBrain的@SystemMessage中添加：

```java
// 在现有 SystemMessage 中添加这段：
【Skill使用指导】
- 系统已根据你的问题自动选择了相关的Skills
- 只在需要时参考已激活的Skill指导，不要过度依赖
- 如果问题不需要任何Skill，正常回答即可
- 避免在回答中显式提及"Skill已激活"等系统信息
```

- [ ] **步骤 2: 运行测试验证**

运行：`mvn compile -pl ich_parent/ich_omnitrix`
预期：编译成功

- [ ] **步骤 3: 提交**

```bash
git add ich_parent/ich_omnitrix/src/main/java/com/hyang/ich/omnitrix/brain/*MasterBrain.java
git commit -m "feat: add Skill usage guidance in System Prompt"
```

---

## 任务 4: 单元测试和集成测试

**文件：**
- 创建：`ich_parent/ich_omnitrix/src/test/java/com/hyang/ich/omnitrix/infrastructure/skill/SkillSelectorTest.java`
- 创建：`ich_parent/ich_omnitrix/src/test/java/com/hyang/ich/omnitrix/brain/MasterBrainFactoryTest.java`

- [ ] **步骤 1: 编写完整的单元测试**

```java
package com.hyang.ich.omnitrix.brain;

import com.hyang.ich.omnitrix.agent.tool.ToolRegistry;
import com.hyang.ich.omnitrix.infrastructure.memory.ChatMemoryManager;
import com.hyang.ich.omnitrix.infrastructure.skill.SkillPromptConfig;
import com.hyang.ich.omnitrix.infrastructure.skill.SkillSelector;
import com.hyang.ich.omnitrix.service.UserMemoryService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MasterBrainFactoryTest {

    @Mock
    private ChatLanguageModel chatLanguageModel;
    
    @Mock
    private ChatLanguageModel auxiliaryChatModel;
    
    @Mock
    private StreamingChatLanguageModel streamingChatLanguageModel;
    
    @Mock
    private ToolRegistry toolRegistry;
    
    @Mock
    private SkillPromptConfig skillPromptConfig;
    
    @Mock
    private SkillSelector skillSelector;
    
    @Mock
    private UserMemoryService userMemoryService;
    
    @Mock
    private ChatMemoryManager chatMemoryManager;

    @InjectMocks
    private MasterBrainFactory factory;

    @Test
    void testBuildDynamicSkillsPrompt() {
        when(skillSelector.buildSelectedSkillsPrompt(anyString()))
            .thenReturn("selected skill prompt");

        String result = factory.buildDynamicSkillsPrompt("我想了解非遗");
        
        assertEquals("selected skill prompt", result);
    }

    @Test
    void testBuildDynamicSkillsPrompt_emptyMessage() {
        String result = factory.buildDynamicSkillsPrompt("");
        
        assertEquals("", result);
    }
}
```

- [ ] **步骤 2: 运行测试**

运行：`mvn test -pl ich_parent/ich_omnitrix -Dtest=MasterBrainFactoryTest,SkillSelectorTest`
预期：所有测试通过

- [ ] **步骤 3: 提交**

```bash
git add ich_parent/ich_omnitrix/src/test/
git commit -m "test: add unit tests for dynamic Skill selection"
```

---

## 总结

此计划实现了Skill动态选择系统，包含：
1. 新增SkillSelector组件，支持基于关键词和LLM的智能选择
2. 修改MasterBrainFactory，支持动态构建Skill Prompt
3. 更新System Prompt，添加Skill使用指导
4. 完整的单元测试覆盖

与Claude Skill机制对比：
| 功能 | Claude | 本实现 |
|------|--------|--------|
| 触发方式 | 语义自激活 | 关键词+LLM选择 |
| 数量限制 | 自动 | 最大3个 |
| 静态/动态 | 动态 | 动态 |

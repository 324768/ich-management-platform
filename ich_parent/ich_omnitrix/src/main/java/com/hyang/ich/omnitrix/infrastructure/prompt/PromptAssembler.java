package com.hyang.ich.omnitrix.infrastructure.prompt;

import com.hyang.ich.omnitrix.agent.AgentContext;
import com.hyang.ich.omnitrix.agent.SubAgent;
import com.hyang.ich.omnitrix.blackboard.TaskBoard;
import com.hyang.ich.omnitrix.dto.AgentQueryResult;
import com.hyang.ich.omnitrix.infrastructure.skill.Skill;
import com.hyang.ich.omnitrix.infrastructure.skill.SkillPromptConfig;
import com.hyang.ich.omnitrix.infrastructure.skill.SkillSelector;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
public class PromptAssembler {

    private final PromptManager promptManager;
    private final SkillPromptConfig skillPromptConfig;

    public PromptAssembler(PromptManager promptManager, SkillPromptConfig skillPromptConfig) {
        this.promptManager = promptManager;
        this.skillPromptConfig = skillPromptConfig;
    }

    /**
     * 组装完整的 System Prompt（向后兼容，无用户画像）
     */
    public String assemble(AgentContext context, SubAgent subAgent,
                           AgentQueryResult queryResult, String summary) {
        return assemble(context, subAgent, queryResult, summary, null);
    }

    /**
     * 组装完整的 System Prompt（含用户画像）
     *
     * @param context      用户上下文
     * @param subAgent     选中的子代理（可为null，表示GeneralSubAgent）
     * @param queryResult  子代理查询结果（可为null）
     * @param summary      对话摘要（可为null）
     * @param userProfile  用户画像文本（可为null，来自 UserMemoryService）
     * @return 完整的 System Prompt
     */
    public String assemble(AgentContext context, SubAgent subAgent,
                           AgentQueryResult queryResult, String summary,
                           String userProfile) {
        return assemble(context, subAgent, queryResult, summary, userProfile, null);
    }

    /**
     * 组装完整的 System Prompt（含用户画像 + 笔记）
     */
    public String assemble(AgentContext context, SubAgent subAgent,
                           AgentQueryResult queryResult, String summary,
                           String userProfile, String sessionNotes) {
        return doAssemble(context, subAgent, queryResult, summary, userProfile, null, sessionNotes, null);
    }

    /**
     * 组装完整的 System Prompt（含TaskBoard，从黑板获取Skills）
     * 主脑调用方案：Skills由主脑决定，写入TaskBoard，SubAgent从TaskBoard获取
     *
     * @param taskBoard 黑板（包含每个Task需要的Skills）
     */
    public String assemble(AgentContext context, SubAgent subAgent,
                           AgentQueryResult queryResult, String summary,
                           String userProfile, TaskBoard taskBoard) {
        return doAssemble(context, subAgent, queryResult, summary, userProfile, null, null, taskBoard);
    }

    /**
     * 实际组装逻辑
     */
    private String doAssemble(AgentContext context, SubAgent subAgent,
                              AgentQueryResult queryResult, String summary,
                              String userProfile, String systemMemory, String sessionNotes,
                              TaskBoard taskBoard) {

        StringBuilder sb = new StringBuilder();

        // 层级1: 基础 Prompt
        sb.append(promptManager.resolve("master_brain_system"));

        // 层级1b: 动态注入Skills（主脑调用方案：从TaskBoard获取）
        // 如果传入了TaskBoard，从黑板获取当前Agent需要的Skills
        if (taskBoard != null && subAgent != null) {
            String agentCode = subAgent.getCode();
            List<Skill> skills = getSkillsFromTaskBoard(taskBoard, agentCode);
            if (!skills.isEmpty()) {
                String skillPrompts = combineSkillPrompts(skills);
                sb.append("\n\n").append(skillPrompts);
                log.debug("[主脑调用方案] {} 使用Skills: {}", agentCode,
                    skills.stream().map(Skill::getName).toList());
            }
        } else {
            // Fallback: Quick Path模式，从context推断Skills（保持向后兼容）
            String userQuery = context != null ? context.getUserQuery() : null;
            if (StringUtils.isNotBlank(userQuery)) {
                List<Skill> selectedSkills = skillPromptConfig.getSkillsByKeywords(userQuery);
                if (!selectedSkills.isEmpty()) {
                    String skillPrompts = combineSkillPrompts(selectedSkills);
                    sb.append("\n\n").append(skillPrompts);
                    log.debug("[QuickPath Fallback] 动态注入Skills: {}",
                        selectedSkills.stream().map(Skill::getName).toList());
                }
            }
        }

        // 层级2a: 当前时间
        LocalDateTime now = LocalDateTime.now();
        String dayOfWeek = now.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINA);
        sb.append("\n\n## 当前时间\n");
        sb.append("今天是 ").append(now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        sb.append("（").append(dayOfWeek).append("），");
        sb.append("当前时间 ").append(now.format(DateTimeFormatter.ofPattern("HH:mm"))).append("。");

        // 层级2b: 用户身份信息
        if (context != null && context.getUserId() != null) {
            sb.append("\n\n## 当前用户信息\n");
            sb.append("- 用户ID: ").append(context.getUserId()).append("\n");
            if (StringUtils.isNotBlank(context.getUserRole())) {
                sb.append("- 角色: ").append(context.getUserRole()).append("\n");
            }
        }

        // 层级2b+: 用户画像（跨会话L3长期记忆）
        if (StringUtils.isNotBlank(userProfile)) {
            sb.append("\n\n## 用户画像（你对这位用户的了解）\n").append(userProfile);
        }

        // 层级4: 系统全局记忆（L4）
        if (StringUtils.isNotBlank(systemMemory)) {
            sb.append("\n\n## 系统全局记忆\n").append(systemMemory);
        }

        // 层级2c: 历史对话摘要
        if (StringUtils.isNotBlank(summary)) {
            sb.append("\n\n## 历史对话摘要\n> ").append(summary);
        }

        // 层级3: 子代理角色切换段
        if (subAgent != null && StringUtils.isNotBlank(subAgent.getAgentPrompt())) {
            sb.append("\n\n").append(subAgent.getAgentPrompt());
        }

        // 层级4: 查询结果注入
        if (queryResult != null) {
            sb.append("\n\n").append(queryResult.toPromptInjection());
        }

        return sb.toString();
    }

    /**
     * 从TaskBoard获取指定Agent需要的Skills
     * 主脑调用方案：每个TaskNode包含自己需要的Skills
     */
    private List<Skill> getSkillsFromTaskBoard(TaskBoard taskBoard, String agentCode) {
        if (taskBoard == null || agentCode == null) {
            return List.of();
        }

        // 查找黑板上与当前Agent匹配的任务节点
        for (var node : taskBoard.getAllNodes()) {
            if (agentCode.equals(node.getAgentCode()) && node.getRequiredSkills() != null) {
                return skillPromptConfig.getSkills(node.getRequiredSkills());
            }
        }
        return List.of();
    }

    /**
     * 组合多个Skills的Prompt
     */
    private String combineSkillPrompts(List<Skill> skills) {
        if (skills == null || skills.isEmpty()) {
            return "";
        }

        StringBuilder combinedPrompt = new StringBuilder();
        for (int i = 0; i < skills.size(); i++) {
            Skill skill = skills.get(i);
            combinedPrompt.append(skill.getSystemPrompt());

            // 如果不是最后一个，添加分隔符
            if (i < skills.size() - 1) {
                combinedPrompt.append("\n\n");
            }
        }

        return combinedPrompt.toString();
    }
}

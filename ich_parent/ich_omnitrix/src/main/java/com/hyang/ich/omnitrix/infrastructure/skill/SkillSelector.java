package com.hyang.ich.omnitrix.infrastructure.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skill选择器 - 动态调用模式的核心组件
 * 由主脑LLM调用，判断用户问题需要启用哪些Skills
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillSelector {

    private final SkillPromptConfig skillPromptConfig;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 选择Skill的Prompt模板
     */
    private static final String SKILL_SELECTION_PROMPT = """
            # Skill选择任务

            ## 任务说明
            请分析用户的问题，判断需要启用哪些Skills来更好地回答问题。

            %s

            ## 用户问题
            %s

            ## 输出要求
            请严格按照JSON格式输出：
            {
                "selected_skills": ["skill_id1", "skill_id2"],
                "reason": "选择理由（简短）"
            }

            注意：
            - 只选择与问题相关的Skills
            - 如果没有问题需要任何Skill，返回空数组
            - 使用Skill的ID标识（如 heritage_master）
            """;

    /**
     * JSON解析Pattern
     */
    private static final Pattern SKILLS_PATTERN = Pattern.compile(
            "\"selected_skills\"\\s*:\\s*\\[(.*?)\\]",
            Pattern.DOTALL
    );

    /**
     * 根据用户问题动态选择需要启用的Skills
     * @param userQuery 用户问题
     * @return 选中的Skills列表
     */
    public List<Skill> selectSkills(String userQuery) {
        try {
            // 1. 构建选择Prompt
            String skillsDesc = skillPromptConfig.getSkillsDescription();
            String prompt = String.format(SKILL_SELECTION_PROMPT, skillsDesc, userQuery);

            // 2. 调用LLM选择
            String llmResponse = llmClient.chat(prompt);
            log.debug("Skill选择结果: {}", llmResponse);

            // 3. 解析LLM响应
            List<String> skillIds = parseSkillIds(llmResponse);

            // 4. 获取Skill对象
            if (skillIds.isEmpty()) {
                log.debug("无需启用任何Skill");
                return Collections.emptyList();
            }

            List<Skill> selectedSkills = skillPromptConfig.getSkills(skillIds);
            log.info("选中的Skills: {}", selectedSkills.stream().map(Skill::getName).toList());

            return selectedSkills;

        } catch (Exception e) {
            log.warn("Skill选择失败，使用空列表: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 解析LLM响应，获取选中的skillIds
     */
    private List<String> parseSkillIds(String llmResponse) {
        List<String> skillIds = new ArrayList<>();

        try {
            // 尝试解析JSON
            Matcher matcher = SKILLS_PATTERN.matcher(llmResponse);
            if (matcher.find()) {
                String skillsContent = matcher.group(1);
                // 提取skill id
                Pattern idPattern = Pattern.compile("\"([^\"]+)\"");
                Matcher idMatcher = idPattern.matcher(skillsContent);
                while (idMatcher.find()) {
                    skillIds.add(idMatcher.group(1));
                }
            }

            // 如果JSON解析失败，尝试关键词匹配作为fallback
            if (skillIds.isEmpty()) {
                skillIds = keywordMatchFallback(llmResponse);
            }

        } catch (Exception e) {
            log.warn("解析Skill选择结果失败: {}", e.getMessage());
            skillIds = keywordMatchFallback(llmResponse);
        }

        return skillIds;
    }

    /**
     * 关键词匹配fallback
     * 当LLM返回格式不标准时使用
     */
    private List<String> keywordMatchFallback(String text) {
        List<String> matchedSkills = new ArrayList<>();

        // 获取所有启用的Skills
        for (Skill skill : skillPromptConfig.getAllEnabledSkills()) {
            if (skill.getKeywords() != null) {
                for (String keyword : skill.getKeywords()) {
                    if (text.contains(keyword)) {
                        matchedSkills.add(skill.getId());
                        break;
                    }
                }
            }
        }

        return matchedSkills;
    }

    /**
     * 组合多个Skills的Prompt
     * @param skills 选中的Skills
     * @return 组合后的Skill Prompt
     */
    public String combineSkillPrompts(List<Skill> skills) {
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

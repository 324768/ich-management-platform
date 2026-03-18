package com.hyang.ich.omnitrix.infrastructure.skill;

import com.hyang.ich.omnitrix.infrastructure.llm.LlmClient;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Skill选择器 — 完全参考Claude Skill自激活机制
 * 
 * Claude Skill核心特点：
 * 1. Skill是自激活的——AI根据任务自动判断是否启用相关Skill
 * 2. Skill定义包含YAML frontmatter（name、description）供AI理解
 * 3. Skill可以包含模板文件、脚本、示例等辅助资源
 * 4. Skill通过语义匹配自动触发，而非关键词匹配
 * 
 * 实现：
 * - 使用LLM进行语义理解，让AI自主判断需要哪些Skills
 * - 保留关键词作为快速预筛（提升性能）
 * - 支持triggerCondition让AI理解何时激活
 * - 支持examples帮助AI正确使用Skill
 */
@Slf4j
@Component
public class SkillSelector {

    private final SkillPromptConfig skillPromptConfig;
    private final LlmClient llmClient;

    // 最大选择Skill数量
    private static final int MAX_SELECTED_SKILLS = 3;

    // 快速预筛的关键词匹配数量阈值（超过此数量才使用LLM精细选择）
    private static final int LLM_SELECTION_THRESHOLD = 2;

    public SkillSelector(SkillPromptConfig skillPromptConfig, LlmClient llmClient) {
        this.skillPromptConfig = skillPromptConfig;
        this.llmClient = llmClient;
    }

    /**
     * 根据用户消息动态选择需要启用的Skills（自激活机制）
     * 参考Claude：AI根据任务语义自主判断是否启用相关Skill
     * 
     * @param userMessage 用户消息
     * @return 选中的Skill列表（按相关性排序）
     */
    public List<Skill> selectSkills(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // 获取所有启用的Skills
        List<Skill> allEnabled = skillPromptConfig.getAllEnabledSkills();
        if (allEnabled.isEmpty()) {
            return Collections.emptyList();
        }

        // 如果只有1-2个Skill，直接返回（无需选择）
        if (allEnabled.size() <= LLM_SELECTION_THRESHOLD) {
            return allEnabled;
        }

        // 使用LLM进行语义选择（核心自激活机制）
        return selectWithLLM(userMessage, allEnabled);
    }

    /**
     * 使用LLM进行语义选择（核心自激活机制）
     * 让AI自己判断哪些Skill与当前任务相关
     */
    private List<Skill> selectWithLLM(String userMessage, List<Skill> candidates) {
        String prompt = buildSemanticSelectionPrompt(userMessage, candidates);

        try {
            LlmResponse response = llmClient.chatAuxiliaryJson(prompt, new ArrayList<>(), "Skill语义选择");
            String content = response.getContent();

            if (content == null || content.trim().isEmpty()) {
                log.debug("LLM选择Skill返回为空，使用默认全部");
                return candidates.stream().limit(MAX_SELECTED_SKILLS).collect(Collectors.toList());
            }

            List<Skill> selected = parseSemanticSelection(content, candidates);
            
            // 如果LLM解析失败，回退到关键词匹配
            if (selected.isEmpty()) {
                return fallbackKeywordMatch(userMessage, candidates);
            }
            
            return selected;
            
        } catch (Exception e) {
            log.warn("LLM选择Skill失败，回退到关键词匹配: {}", e.getMessage());
            return fallbackKeywordMatch(userMessage, candidates);
        }
    }

    /**
     * 构建语义选择Prompt（让AI自主判断）
     */
    private String buildSemanticSelectionPrompt(String userMessage, List<Skill> candidates) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("你是一个AI助手，需要判断当前用户问题需要启用哪些Skills。\n\n");
        
        sb.append("【当前用户问题】\n");
        sb.append(userMessage).append("\n\n");
        
        sb.append("【可用的Skills（每个Skill都有触发条件）】\n");
        sb.append("请根据用户问题的语义，选择最相关的Skills。\n\n");
        
        for (Skill skill : candidates) {
            sb.append("## Skill: ").append(skill.getName()).append("\n");
            sb.append("- ID: ").append(skill.getId()).append("\n");
            sb.append("- 描述: ").append(skill.getDescription()).append("\n");
            sb.append("- 触发条件: ").append(skill.getTriggerCondition()).append("\n");
            if (skill.getExamples() != null && skill.getExamples().length > 0) {
                sb.append("- 使用示例:\n");
                for (String ex : skill.getExamples()) {
                    sb.append("  * ").append(ex).append("\n");
                }
            }
            sb.append("\n");
        }
        
        sb.append("【选择原则】（重要）\n");
        sb.append("1. 仔细阅读用户问题，理解用户的真实意图\n");
        sb.append("2. 对照每个Skill的【触发条件】，判断是否需要激活\n");
        sb.append("3. 选择最相关的Skills，最多不超过").append(MAX_SELECTED_SKILLS).append("个\n");
        sb.append("4. 如果用户问题不需要任何Skill，返回空列表[]\n");
        sb.append("5. 只选择你确信相关的Skill，不要为了显示能力而选择无关的\n\n");
        
        sb.append("【输出格式】（严格JSON数组）\n");
        sb.append("选择多个: [\"skill_id1\", \"skill_id2\"]\n");
        sb.append("只选择一个: [\"skill_id\"]\n");
        sb.append("不需要任何Skill: []\n\n");
        
        sb.append("请基于语义理解做出选择：\n");
        sb.append("JSON数组: ");
        
        return sb.toString();
    }

    /**
     * 解析语义选择结果
     */
    private List<Skill> parseSemanticSelection(String content, List<Skill> candidates) {
        try {
            content = content.trim();
            
            // 构建ID到Skill的映射
            Map<String, Skill> skillMap = candidates.stream()
                    .collect(Collectors.toMap(Skill::getId, s -> s));
            
            // 解析JSON数组
            List<String> selectedIds = new ArrayList<>();
            
            // 移除可能的markdown代码块标记
            content = content.replace("```json", "").replace("```", "").trim();
            
            if (content.startsWith("[")) {
                // 处理数组格式
                String[] parts = content.replace("[", "").replace("]", "").split(",");
                for (String part : parts) {
                    String id = part.replace("\"", "").trim();
                    if (!id.isEmpty()) {
                        selectedIds.add(id);
                    }
                }
            }
            
            // 映射回Skill对象
            return selectedIds.stream()
                    .filter(skillMap::containsKey)
                    .map(skillMap::get)
                    .limit(MAX_SELECTED_SKILLS)
                    .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.debug("解析语义选择结果失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 关键词匹配回退
     */
    private List<Skill> fallbackKeywordMatch(String userMessage, List<Skill> candidates) {
        List<Skill> matched = skillPromptConfig.getSkillsByKeywords(userMessage);
        if (matched.isEmpty()) {
            // 如果关键词也没匹配，返回空的（让AI正常回答）
            return Collections.emptyList();
        }
        return matched.stream().limit(MAX_SELECTED_SKILLS).collect(Collectors.toList());
    }

    /**
     * 构建选中的Skills的Prompt（供MasterBrain使用）
     * 包含Skill元信息，帮助AI正确使用
     */
    public String buildSelectedSkillsPrompt(String userMessage) {
        List<Skill> selected = selectSkills(userMessage);
        if (selected.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        
        // 添加Skill选择说明（让AI知道这是动态选择的）
        sb.append("【已自动选择的Skills】\n");
        sb.append("以下Skills是AI根据你的问题自动选择的，仅供参考：\n\n");
        
        // 添加每个选中Skill的元信息
        for (Skill skill : selected) {
            sb.append(skill.toMetadataString());
            sb.append("\n");
        }
        
        sb.append("---\n\n");
        
        // 添加实际Prompt
        for (Skill skill : selected) {
            sb.append(skill.getSystemPrompt()).append("\n\n");
        }
        
        return sb.toString();
    }

    /**
     * 获取所有Skills的元信息（用于展示给用户或调试）
     */
    public String getAllSkillsMetadata() {
        List<Skill> allEnabled = skillPromptConfig.getAllEnabledSkills();
        if (allEnabled.isEmpty()) {
            return "暂无可用的Skills";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【可用的Skills列表】\n\n");
        
        for (Skill skill : allEnabled) {
            sb.append(skill.toMetadataString());
            sb.append("\n");
        }
        
        return sb.toString();
    }
}
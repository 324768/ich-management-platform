package com.hyang.ich.omnitrix.agent.impl;

import com.hyang.ich.omnitrix.agent.AgentContext;
import com.hyang.ich.omnitrix.agent.SubAgent;
import com.hyang.ich.omnitrix.agent.tool.AgentTool;
import com.hyang.ich.omnitrix.agent.tool.ToolCallResult;
import com.hyang.ich.omnitrix.dto.AgentQueryResult;
import com.hyang.ich.omnitrix.entity.AiSkillConfig;
import com.hyang.ich.omnitrix.service.SkillConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Ultra 技能配置管理代理 —— 超级管理员通过AI对话控制Skills的开启/关闭
 * 这是Omnitrix AI Ultra版的专属子代理
 */
@Slf4j
@Component
public class UltraSkillControlAgent implements SubAgent {

    private final SkillConfigService skillConfigService;

    private static final List<AgentTool> TOOLS = Arrays.asList(
            AgentTool.of("list_all_skills", "查询所有Skills的列表及启用状态", "无参数"),
            AgentTool.of("enable_skill", "启用指定Skill", "Skill ID"),
            AgentTool.of("disable_skill", "禁用指定Skill", "Skill ID"),
            AgentTool.of("batch_enable_skills", "批量启用多个Skills", "Skill ID列表"),
            AgentTool.of("batch_disable_skills", "批量禁用多个Skills", "Skill ID列表"),
            AgentTool.of("get_skill_detail", "查看指定Skill的详细信息", "Skill ID")
    );

    public UltraSkillControlAgent(SkillConfigService skillConfigService) {
        this.skillConfigService = skillConfigService;
    }

    @Override
    public String getCode() { return "ultra_skill_control"; }

    @Override
    public String getName() { return "技能配置管理"; }

    @Override
    public String getDescription() { return "管理AI技能(Skills)的开启/关闭，超级管理员专用"; }

    @Override
    public String getAgentPrompt() {
        return "## 当前任务模式: Ultra 技能配置管理\n" +
                "你现在是Omnitrix AI Ultra版的\"技能配置管理员\"。\n" +
                "\n" +
                "【你的职责】\n" +
                "- 帮助超级管理员通过对话方式管理AI Skills的启用/禁用状态\n" +
                "- 可以查询当前所有Skills的状态\n" +
                "- 可以一键启用或禁用指定的Skills\n" +
                "\n" +
                "【可管理的Skills】\n" +
                "- heritage_master: 非遗文化大师\n" +
                "- shopping_advisor: 购物顾问\n" +
                "- customer_service: 客服话术师\n" +
                "- knowledge_expert: 知识百科达人\n" +
                "- recommend_expert: 推荐解读者\n" +
                "- security_audit: 安全审核员\n" +
                "- quality_evaluator: 质量评估师\n" +
                "\n" +
                "【对话示例】\n" +
                "用户: \"显示所有技能\"\n" +
                "→ 调用 list_all_skills 返回所有Skills状态\n" +
                "\n" +
                "用户: \"启用非遗文化大师\"\n" +
                "→ 调用 enable_skill(\"heritage_master\")\n" +
                "\n" +
                "用户: \"关闭客服话术师\"\n" +
                "→ 调用 disable_skill(\"customer_service\")\n" +
                "\n" +
                "用户: \"同时启用购物顾问和质量评估师\"\n" +
                "→ 调用 batch_enable_skills([\"shopping_advisor\", \"quality_evaluator\"])\n" +
                "\n" +
                "用户: \"查看技能详情\"\n" +
                "→ 调用 get_skill_detail(\"heritage_master\")\n" +
                "\n" +
                "【重要规则】\n" +
                "- 必须调用对应的工具来完成操作，不能只是回答\n" +
                "- 操作完成后要告知用户结果\n" +
                "- 如果用户请求不明确，要先列出所有Skills让用户选择\n";
    }

    public List<AgentTool> getTools() {
        return TOOLS;
    }

    public ToolCallResult callTool(String toolName, Map<String, Object> args, AgentContext context) {
        try {
            switch (toolName) {
                case "list_all_skills":
                    return handleListAllSkills();
                case "enable_skill":
                    return handleEnableSkill(args);
                case "disable_skill":
                    return handleDisableSkill(args);
                case "batch_enable_skills":
                    return handleBatchEnableSkills(args);
                case "batch_disable_skills":
                    return handleBatchDisableSkills(args);
                case "get_skill_detail":
                    return handleGetSkillDetail(args);
                default:
                    return ToolCallResult.error("未知工具: " + toolName);
            }
        } catch (Exception e) {
            log.error("UltraSkillControlAgent 调用工具失败: tool={}, error={}", toolName, e.getMessage(), e);
            return ToolCallResult.error("执行失败: " + e.getMessage());
        }
    }

    private ToolCallResult handleListAllSkills() {
        List<AiSkillConfig> skills = skillConfigService.getAllSkills();
        StringBuilder sb = new StringBuilder();
        sb.append("【所有AI Skills列表】\n\n");
        sb.append(String.format("%-20s %-15s %-10s %s\n", "Skill ID", "名称", "状态", "描述"));
        sb.append("─────────────────────────────────────────────────────────────\n");
        for (AiSkillConfig skill : skills) {
            String status = (skill.getEnabled() != null && skill.getEnabled() == 1) ? "启用" : "禁用";
            String desc = skill.getDescription() != null ?
                (skill.getDescription().length() > 20 ? skill.getDescription().substring(0, 20) + "..." : skill.getDescription()) : "";
            sb.append(String.format("%-20s %-15s %-10s %s\n",
                skill.getSkillId(), skill.getSkillName(), status, desc));
        }
        sb.append("\n总计: ").append(skills.size()).append(" 个Skills");
        return ToolCallResult.success(sb.toString());
    }

    private ToolCallResult handleEnableSkill(Map<String, Object> args) {
        String skillId = (String) args.get("skillId");
        if (skillId == null || skillId.isEmpty()) {
            return ToolCallResult.error("请提供Skill ID");
        }

        boolean success = skillConfigService.enableSkill(skillId);
        if (success) {
            AiSkillConfig skill = skillConfigService.getSkillById(skillId);
            String skillName = skill != null ? skill.getSkillName() : skillId;
            return ToolCallResult.success("已成功启用技能 [" + skillName + "]");
        } else {
            return ToolCallResult.error("启用失败，Skill ID不存在: " + skillId);
        }
    }

    private ToolCallResult handleDisableSkill(Map<String, Object> args) {
        String skillId = (String) args.get("skillId");
        if (skillId == null || skillId.isEmpty()) {
            return ToolCallResult.error("请提供Skill ID");
        }

        boolean success = skillConfigService.disableSkill(skillId);
        if (success) {
            AiSkillConfig skill = skillConfigService.getSkillById(skillId);
            String skillName = skill != null ? skill.getSkillName() : skillId;
            return ToolCallResult.success("已成功禁用技能 [" + skillName + "]");
        } else {
            return ToolCallResult.error("禁用失败，Skill ID不存在: " + skillId);
        }
    }

    @SuppressWarnings("unchecked")
    private ToolCallResult handleBatchEnableSkills(Map<String, Object> args) {
        Object skillIdsObj = args.get("skillIds");
        if (skillIdsObj == null) {
            return ToolCallResult.error("请提供Skill ID列表");
        }

        List<String> skillIds;
        if (skillIdsObj instanceof List) {
            skillIds = (List<String>) skillIdsObj;
        } else {
            skillIds = Arrays.asList(((String) skillIdsObj).split(","));
        }

        int count = skillConfigService.batchUpdateEnabled(skillIds, 1);
        return ToolCallResult.success("成功启用 " + count + " 个Skills: " + skillIds);
    }

    @SuppressWarnings("unchecked")
    private ToolCallResult handleBatchDisableSkills(Map<String, Object> args) {
        Object skillIdsObj = args.get("skillIds");
        if (skillIdsObj == null) {
            return ToolCallResult.error("请提供Skill ID列表");
        }

        List<String> skillIds;
        if (skillIdsObj instanceof List) {
            skillIds = (List<String>) skillIdsObj;
        } else {
            skillIds = Arrays.asList(((String) skillIdsObj).split(","));
        }

        int count = skillConfigService.batchUpdateEnabled(skillIds, 0);
        return ToolCallResult.success("成功禁用 " + count + " 个Skills: " + skillIds);
    }

    private ToolCallResult handleGetSkillDetail(Map<String, Object> args) {
        String skillId = (String) args.get("skillId");
        if (skillId == null || skillId.isEmpty()) {
            return ToolCallResult.error("请提供Skill ID");
        }

        AiSkillConfig skill = skillConfigService.getSkillById(skillId);
        if (skill == null) {
            return ToolCallResult.error("Skill不存在: " + skillId);
        }

        String status = (skill.getEnabled() != null && skill.getEnabled() == 1) ? "启用" : "禁用";
        StringBuilder sb = new StringBuilder();
        sb.append("【Skill详情】\n\n");
        sb.append("- Skill ID: ").append(skill.getSkillId()).append("\n");
        sb.append("- 名称: ").append(skill.getSkillName()).append("\n");
        sb.append("- 状态: ").append(status).append("\n");
        sb.append("- 描述: ").append(skill.getDescription()).append("\n");
        sb.append("- 关键词: ").append(skill.getKeywords()).append("\n");
        if (skill.getSystemPrompt() != null && !skill.getSystemPrompt().isEmpty()) {
            sb.append("- Prompt: 已配置\n");
        } else {
            sb.append("- Prompt: 使用默认\n");
        }
        return ToolCallResult.success(sb.toString());
    }

    @Override
    public AgentQueryResult execute(String query, AgentContext context) {
        // UltraSkillControlAgent 主要用于工具调用，不直接处理用户查询
        return AgentQueryResult.success("UltraSkillControlAgent 处理完成，请使用工具进行技能管理操作", getCode());
    }
}

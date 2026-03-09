package com.hyang.ich.omnitrix.agent.impl;

import com.hyang.ich.omnitrix.agent.AgentContext;
import com.hyang.ich.omnitrix.agent.AgentUtils;
import com.hyang.ich.omnitrix.agent.SubAgent;
import com.hyang.ich.omnitrix.agent.tool.AgentTool;
import com.hyang.ich.omnitrix.agent.tool.ToolCallResult;
import com.hyang.ich.omnitrix.agent.tool.ToolSelector;
import com.hyang.ich.omnitrix.dto.AgentQueryResult;
import com.hyang.ich.omnitrix.dto.PendingAction;
import com.hyang.ich.omnitrix.entity.AiUserAiConfig;
import com.hyang.ich.omnitrix.mapper.AiUserAiConfigMapper;
import com.hyang.ich.user.UserService;
import com.hyang.ich.user.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Ultra 用户AI控制代理 —— 管理用户端AI的开关和权限
 */
@Slf4j
@Component
public class UltraUserControlAgent implements SubAgent {

    private final AiUserAiConfigMapper configMapper;
    private final UserService userService;
    private final ToolSelector toolSelector;

    private static final List<AgentTool> TOOLS = Arrays.asList(
            AgentTool.of("disable_user_ai", "关闭、禁用某个用户的AI功能", "用户名或用户ID"),
            AgentTool.of("enable_user_ai", "启用、开启、恢复某个用户的AI功能", "用户名或用户ID"),
            AgentTool.of("query_ai_status", "查询某个用户的AI状态、AI权限", "用户名或用户ID"),
            AgentTool.of("list_disabled", "列出所有被禁用AI的用户", "无")
    );

    public UltraUserControlAgent(AiUserAiConfigMapper configMapper, UserService userService,
                                  ToolSelector toolSelector) {
        this.configMapper = configMapper;
        this.userService = userService;
        this.toolSelector = toolSelector;
    }

    @Override
    public String getCode() { return "ultra_user_control"; }

    @Override
    public String getName() { return "用户AI控制"; }

    @Override
    public String getDescription() { return "控制用户端AI功能的开关和权限（Ultra专用）"; }

    @Override
    public String getAgentPrompt() {
        return "## 当前任务模式: Ultra 用户AI控制\n" +
                "你现在拥有最高权限，可以控制任何用户的AI功能。\n" +
                "- 禁用AI前需要管理员确认\n" +
                "- 操作完成后记录到系统记忆中\n" +
                "- 展示操作结果时要清晰明确";
    }

    @Override
    public AgentQueryResult execute(String userQuery, AgentContext context) {
        try {
            ToolCallResult toolCall = toolSelector.select(userQuery, TOOLS);
            String selectedTool = toolCall.isNone() ? fallbackTool(userQuery) : toolCall.getToolName();
            String param = toolCall.getParameter() != null && !toolCall.getParameter().isEmpty()
                    ? toolCall.getParameter() : AgentUtils.extractKeyword(userQuery);

            switch (selectedTool) {
                case "disable_user_ai":
                    return proposeDisableAi(param, userQuery, context);
                case "enable_user_ai":
                    return proposeEnableAi(param, context);
                case "query_ai_status":
                    return queryAiStatus(param);
                case "list_disabled":
                    return listDisabledUsers();
                default:
                    return AgentQueryResult.success("请告诉我您需要对用户AI做什么操作？\n" +
                            "支持: 禁用AI、启用AI、查询AI状态、列出已禁用用户", getCode());
            }
        } catch (Exception e) {
            log.error("UltraUserControlAgent 执行异常: {}", e.getMessage(), e);
            return AgentQueryResult.error(getCode(), e.getMessage());
        }
    }

    private String fallbackTool(String query) {
        String q = query.toLowerCase();
        if (q.contains("关闭") || q.contains("禁用") || q.contains("禁止")) return "disable_user_ai";
        if (q.contains("启用") || q.contains("开启") || q.contains("恢复")) return "enable_user_ai";
        if (q.contains("状态") || q.contains("权限") || q.contains("查看")) return "query_ai_status";
        if (q.contains("列出") || q.contains("所有") || q.contains("哪些")) return "list_disabled";
        return "none";
    }

    private AgentQueryResult proposeDisableAi(String param, String userQuery, AgentContext context) {
        UserDTO user = findUser(param);
        if (user == null) {
            return AgentQueryResult.success("未找到用户「" + param + "」，请提供正确的用户名或ID", getCode());
        }

        // 提取禁用原因
        String reason = "管理员通过Ultra AI禁用";
        if (userQuery.contains("原因") || userQuery.contains("因为")) {
            int idx = Math.max(userQuery.indexOf("原因"), userQuery.indexOf("因为"));
            if (idx >= 0) {
                reason = userQuery.substring(idx).replaceFirst("(原因|因为)[：:]?\\s*", "").trim();
                if (reason.isEmpty()) reason = "管理员通过Ultra AI禁用";
            }
        }

        PendingAction action = PendingAction.of("ultra_disable_user_ai",
                "禁用用户「" + user.getNickname() + "」(ID:" + user.getId() + ")的AI功能")
                .param("targetUserId", String.valueOf(user.getId()))
                .param("disabledReason", reason);

        String data = "目标用户:\n- 昵称: " + user.getNickname() +
                "\n- ID: " + user.getId() +
                "\n- 禁用原因: " + reason +
                "\n\n⚠️ 禁用后该用户将无法使用AI聊天功能";
        return AgentQueryResult.actionProposed(data, getCode(), action);
    }

    private AgentQueryResult proposeEnableAi(String param, AgentContext context) {
        UserDTO user = findUser(param);
        if (user == null) {
            return AgentQueryResult.success("未找到用户「" + param + "」，请提供正确的用户名或ID", getCode());
        }

        AiUserAiConfig config = configMapper.selectByUserId(user.getId());
        if (config == null || config.getAiEnabled() == 1) {
            return AgentQueryResult.success("用户「" + user.getNickname() + "」的AI功能当前已经是启用状态", getCode());
        }

        PendingAction action = PendingAction.of("ultra_enable_user_ai",
                "启用用户「" + user.getNickname() + "」(ID:" + user.getId() + ")的AI功能")
                .param("targetUserId", String.valueOf(user.getId()));

        String data = "目标用户:\n- 昵称: " + user.getNickname() +
                "\n- ID: " + user.getId() +
                "\n- 原禁用原因: " + (config.getDisabledReason() != null ? config.getDisabledReason() : "未记录") +
                "\n\n✅ 启用后该用户将恢复AI聊天功能";
        return AgentQueryResult.actionProposed(data, getCode(), action);
    }

    private AgentQueryResult queryAiStatus(String param) {
        UserDTO user = findUser(param);
        if (user == null) {
            return AgentQueryResult.success("未找到用户「" + param + "」", getCode());
        }

        AiUserAiConfig config = configMapper.selectByUserId(user.getId());
        StringBuilder sb = new StringBuilder();
        sb.append("用户「").append(user.getNickname()).append("」(ID:").append(user.getId()).append(") AI状态:\n");
        if (config == null || config.getAiEnabled() == 1) {
            sb.append("- 状态: ✅ 已启用\n");
            sb.append("- 每日查询上限: ").append(config != null ? config.getMaxDailyQueries() : 100).append("次\n");
            if (config != null && config.getBlockedAgents() != null) {
                sb.append("- 限制的Agent: ").append(config.getBlockedAgents()).append("\n");
            }
        } else {
            sb.append("- 状态: ❌ 已禁用\n");
            sb.append("- 禁用原因: ").append(config.getDisabledReason() != null ? config.getDisabledReason() : "未记录").append("\n");
            sb.append("- 禁用时间: ").append(config.getUpdateTime()).append("\n");
        }
        return AgentQueryResult.success(sb.toString(), getCode());
    }

    private AgentQueryResult listDisabledUsers() {
        List<AiUserAiConfig> disabledList = configMapper.selectDisabled();
        if (disabledList == null || disabledList.isEmpty()) {
            return AgentQueryResult.success("当前没有被禁用AI功能的用户", getCode());
        }

        StringBuilder sb = new StringBuilder("已禁用AI的用户列表:\n");
        for (AiUserAiConfig config : disabledList) {
            UserDTO user = userService.findById(config.getUserId());
            String name = user != null ? user.getNickname() : "未知";
            sb.append("- ").append(name).append(" (ID:").append(config.getUserId()).append(")");
            if (config.getDisabledReason() != null) sb.append(", 原因: ").append(config.getDisabledReason());
            sb.append("\n");
        }
        sb.append("\n共 ").append(disabledList.size()).append(" 个用户的AI功能已被禁用");
        return AgentQueryResult.success(sb.toString(), getCode());
    }

    private UserDTO findUser(String param) {
        if (param == null || param.isEmpty()) return null;
        // 尝试按ID查找
        try {
            Long id = Long.parseLong(param.trim());
            return userService.findById(id);
        } catch (NumberFormatException ignored) {}
        // 按昵称查找
        try {
            return userService.findByNickname(param.trim());
        } catch (Exception ignored) {}
        return null;
    }
}

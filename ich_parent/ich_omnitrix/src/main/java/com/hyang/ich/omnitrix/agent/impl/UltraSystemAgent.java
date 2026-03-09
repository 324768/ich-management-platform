package com.hyang.ich.omnitrix.agent.impl;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.omnitrix.agent.AgentContext;
import com.hyang.ich.omnitrix.agent.SubAgent;
import com.hyang.ich.omnitrix.agent.tool.AgentTool;
import com.hyang.ich.omnitrix.agent.tool.ToolCallResult;
import com.hyang.ich.omnitrix.agent.tool.ToolSelector;
import com.hyang.ich.omnitrix.dto.AgentQueryResult;
import com.hyang.ich.omnitrix.dto.PendingAction;
import com.hyang.ich.user.UserService;
import com.hyang.ich.user.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Ultra 系统操作代理 —— 系统级操作（删除用户、在线状态查询、用户管理等）
 */
@Slf4j
@Component
public class UltraSystemAgent implements SubAgent {

    private final UserService userService;
    private final ToolSelector toolSelector;

    private static final List<AgentTool> TOOLS = Arrays.asList(
            AgentTool.of("query_online_users", "查询在线用户、查看谁在线、在线状态", "无"),
            AgentTool.of("delete_user", "删除用户账号、移除用户", "用户名或用户ID"),
            AgentTool.of("ban_user", "封禁用户、禁用用户账号", "用户名或用户ID"),
            AgentTool.of("unban_user", "解封用户、恢复用户账号", "用户名或用户ID"),
            AgentTool.of("list_users", "查看用户列表、所有用户", "关键词(可选)")
    );

    public UltraSystemAgent(UserService userService, ToolSelector toolSelector) {
        this.userService = userService;
        this.toolSelector = toolSelector;
    }

    @Override
    public String getCode() { return "ultra_system"; }

    @Override
    public String getName() { return "系统管理"; }

    @Override
    public String getDescription() { return "系统级操作：用户管理、在线状态查询（Ultra专用）"; }

    @Override
    public String getAgentPrompt() {
        return "## 当前任务模式: Ultra 系统管理\n" +
                "你现在拥有最高系统权限，可以执行系统级操作。\n" +
                "- 删除用户和封禁用户属于危险操作，必须确认\n" +
                "- 展示用户列表时注意保护隐私，只显示必要信息\n" +
                "- 操作结果要清晰明确";
    }

    @Override
    public AgentQueryResult execute(String userQuery, AgentContext context) {
        try {
            ToolCallResult toolCall = toolSelector.select(userQuery, TOOLS);
            String selectedTool = toolCall.isNone() ? fallbackTool(userQuery) : toolCall.getToolName();
            String param = toolCall.getParameter() != null && !toolCall.getParameter().isEmpty()
                    ? toolCall.getParameter() : extractKeyword(userQuery);

            switch (selectedTool) {
                case "query_online_users":
                    return queryOnlineUsers();
                case "delete_user":
                    return proposeDeleteUser(param, context);
                case "ban_user":
                    return proposeBanUser(param, context);
                case "unban_user":
                    return proposeUnbanUser(param, context);
                case "list_users":
                    return listUsers(param);
                default:
                    return AgentQueryResult.success("请告诉我您需要执行什么系统操作？\n" +
                            "支持: 查询在线用户、删除用户、封禁/解封用户、查看用户列表", getCode());
            }
        } catch (Exception e) {
            log.error("UltraSystemAgent 执行异常: {}", e.getMessage(), e);
            return AgentQueryResult.error(getCode(), e.getMessage());
        }
    }

    private String fallbackTool(String query) {
        String q = query.toLowerCase();
        if (q.contains("在线") || q.contains("谁在")) return "query_online_users";
        if (q.contains("删除") && (q.contains("用户") || q.contains("账号"))) return "delete_user";
        if (q.contains("封禁") || q.contains("禁用账号") || q.contains("封号")) return "ban_user";
        if (q.contains("解封") || q.contains("恢复账号")) return "unban_user";
        if (q.contains("用户列表") || q.contains("所有用户") || q.contains("查看用户")) return "list_users";
        return "none";
    }

    private AgentQueryResult queryOnlineUsers() {
        try {
            List<UserDTO> onlineUsers = userService.listOnlineUsers();
            if (onlineUsers == null || onlineUsers.isEmpty()) {
                return AgentQueryResult.success("当前没有在线用户", getCode());
            }

            StringBuilder sb = new StringBuilder("当前在线用户:\n");
            for (UserDTO user : onlineUsers) {
                sb.append("- ").append(user.getNickname() != null ? user.getNickname() : user.getUsername())
                  .append(" (ID:").append(user.getId()).append(")");
                if (user.getLastActiveTime() != null) {
                    sb.append(", 最后活跃: ").append(user.getLastActiveTime());
                }
                sb.append("\n");
            }
            sb.append("\n共 ").append(onlineUsers.size()).append(" 个用户在线");
            return AgentQueryResult.success(sb.toString(), getCode());
        } catch (Exception e) {
            return AgentQueryResult.success("查询在线用户失败: " + e.getMessage(), getCode());
        }
    }

    private AgentQueryResult proposeDeleteUser(String param, AgentContext context) {
        UserDTO user = findUser(param);
        if (user == null) {
            return AgentQueryResult.success("未找到用户「" + param + "」，请提供正确的用户名或ID", getCode());
        }

        PendingAction action = PendingAction.of("ultra_delete_user",
                "删除用户「" + user.getNickname() + "」(ID:" + user.getId() + ")")
                .param("targetUserId", String.valueOf(user.getId()));

        String data = "⚠️ 危险操作 - 删除用户\n" +
                "- 昵称: " + user.getNickname() + "\n" +
                "- ID: " + user.getId() + "\n" +
                "- 用户名: " + (user.getUsername() != null ? user.getUsername() : "未知") + "\n" +
                "- 状态: " + (user.getStatus() != null && user.getStatus() == 1 ? "正常" : "已禁用") + "\n" +
                "\n❗ 删除后该用户将无法登录，相关数据将被标记删除";
        return AgentQueryResult.actionProposed(data, getCode(), action);
    }

    private AgentQueryResult proposeBanUser(String param, AgentContext context) {
        UserDTO user = findUser(param);
        if (user == null) {
            return AgentQueryResult.success("未找到用户「" + param + "」", getCode());
        }

        if (user.getStatus() != null && user.getStatus() == 0) {
            return AgentQueryResult.success("用户「" + user.getNickname() + "」当前已经是封禁状态", getCode());
        }

        PendingAction action = PendingAction.of("ultra_ban_user",
                "封禁用户「" + user.getNickname() + "」(ID:" + user.getId() + ")")
                .param("targetUserId", String.valueOf(user.getId()));

        String data = "封禁用户:\n" +
                "- 昵称: " + user.getNickname() + "\n" +
                "- ID: " + user.getId() + "\n" +
                "\n封禁后该用户将无法登录";
        return AgentQueryResult.actionProposed(data, getCode(), action);
    }

    private AgentQueryResult proposeUnbanUser(String param, AgentContext context) {
        UserDTO user = findUser(param);
        if (user == null) {
            return AgentQueryResult.success("未找到用户「" + param + "」", getCode());
        }

        if (user.getStatus() != null && user.getStatus() == 1) {
            return AgentQueryResult.success("用户「" + user.getNickname() + "」当前已经是正常状态", getCode());
        }

        PendingAction action = PendingAction.of("ultra_unban_user",
                "解封用户「" + user.getNickname() + "」(ID:" + user.getId() + ")")
                .param("targetUserId", String.valueOf(user.getId()));

        String data = "解封用户:\n" +
                "- 昵称: " + user.getNickname() + "\n" +
                "- ID: " + user.getId() + "\n" +
                "\n解封后该用户将恢复正常登录";
        return AgentQueryResult.actionProposed(data, getCode(), action);
    }

    private AgentQueryResult listUsers(String keyword) {
        try {
            String searchKey = (keyword != null && !keyword.isEmpty()
                    && !keyword.equals("无") && !keyword.equals("none")) ? keyword : null;
            PageResult<UserDTO> result = userService.listUsers(1, 20, searchKey);
            if (result == null || result.getList() == null || result.getList().isEmpty()) {
                return AgentQueryResult.success("未找到匹配的用户", getCode());
            }

            StringBuilder sb = new StringBuilder();
            if (searchKey != null) {
                sb.append("搜索关键词「").append(searchKey).append("」的结果:\n");
            } else {
                sb.append("用户列表:\n");
            }
            for (UserDTO u : result.getList()) {
                sb.append("- ").append(u.getNickname() != null ? u.getNickname() : u.getUsername());
                sb.append(" (ID:").append(u.getId()).append(")");
                sb.append(", 状态: ").append(u.getStatus() != null && u.getStatus() == 1 ? "正常" : "禁用");
                sb.append("\n");
            }
            sb.append("\n共 ").append(result.getTotal()).append(" 个用户");
            if (result.getTotal() > 20) {
                sb.append("（仅显示前20个）");
            }
            return AgentQueryResult.success(sb.toString(), getCode());
        } catch (Exception e) {
            return AgentQueryResult.success("查询用户列表失败: " + e.getMessage(), getCode());
        }
    }

    private UserDTO findUser(String param) {
        if (param == null || param.isEmpty()) return null;
        try {
            Long id = Long.parseLong(param.trim());
            return userService.findById(id);
        } catch (NumberFormatException ignored) {}
        try {
            return userService.findByNickname(param.trim());
        } catch (Exception ignored) {}
        return null;
    }

    private String extractKeyword(String query) {
        if (query == null) return "";
        return query.replaceAll("(查看|查询|列出|显示|所有|用户|列表|账号|在线|删除|封禁|解封)", "").trim();
    }
}

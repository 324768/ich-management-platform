package com.hyang.ich.omnitrix.agent.impl;

import com.hyang.ich.omnitrix.agent.AgentContext;
import com.hyang.ich.omnitrix.agent.SubAgent;
import com.hyang.ich.omnitrix.agent.tool.AgentTool;
import com.hyang.ich.omnitrix.agent.tool.ToolCallResult;
import com.hyang.ich.omnitrix.agent.tool.ToolSelector;
import com.hyang.ich.omnitrix.dto.AgentQueryResult;
import com.hyang.ich.omnitrix.entity.AiUserMemory;
import com.hyang.ich.omnitrix.mapper.AiUserMemoryMapper;
import com.hyang.ich.omnitrix.service.UserMemoryService;
import com.hyang.ich.user.UserService;
import com.hyang.ich.user.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Ultra 跨用户分析代理 —— 查询任意用户画像、行为分析、兴趣统计
 */
@Slf4j
@Component
public class UltraAnalyticsAgent implements SubAgent {

    private final UserService userService;
    private final UserMemoryService userMemoryService;
    private final AiUserMemoryMapper userMemoryMapper;
    private final ToolSelector toolSelector;

    private static final List<AgentTool> TOOLS = Arrays.asList(
            AgentTool.of("query_user_profile", "查询某用户的画像、偏好、兴趣", "用户名或用户ID"),
            AgentTool.of("query_user_memories", "查询某用户的所有长期记忆", "用户名或用户ID"),
            AgentTool.of("query_user_detail", "查询某用户的详细信息、账号信息", "用户名或用户ID")
    );

    public UltraAnalyticsAgent(UserService userService, UserMemoryService userMemoryService,
                                AiUserMemoryMapper userMemoryMapper, ToolSelector toolSelector) {
        this.userService = userService;
        this.userMemoryService = userMemoryService;
        this.userMemoryMapper = userMemoryMapper;
        this.toolSelector = toolSelector;
    }

    @Override
    public String getCode() { return "ultra_analytics"; }

    @Override
    public String getName() { return "用户分析"; }

    @Override
    public String getDescription() { return "跨用户分析：查询用户画像、行为分析、兴趣统计（Ultra专用）"; }

    @Override
    public String getAgentPrompt() {
        return "## 当前任务模式: Ultra 用户分析\n" +
                "你现在拥有最高权限，可以查看任何用户的画像和行为数据。\n" +
                "- 展示用户画像时要结构化呈现\n" +
                "- 注意保护敏感隐私信息\n" +
                "- 可以给出基于数据的运营建议";
    }

    @Override
    public AgentQueryResult execute(String userQuery, AgentContext context) {
        try {
            ToolCallResult toolCall = toolSelector.select(userQuery, TOOLS);
            String selectedTool = toolCall.isNone() ? fallbackTool(userQuery) : toolCall.getToolName();
            String param = toolCall.getParameter() != null && !toolCall.getParameter().isEmpty()
                    ? toolCall.getParameter() : extractUserName(userQuery);

            switch (selectedTool) {
                case "query_user_profile":
                    return queryUserProfile(param);
                case "query_user_memories":
                    return queryUserMemories(param);
                case "query_user_detail":
                    return queryUserDetail(param);
                default:
                    return AgentQueryResult.success("请告诉我您需要查询哪个用户的信息？\n" +
                            "支持: 用户画像、长期记忆、账号详情", getCode());
            }
        } catch (Exception e) {
            log.error("UltraAnalyticsAgent 执行异常: {}", e.getMessage(), e);
            return AgentQueryResult.error(getCode(), e.getMessage());
        }
    }

    private String fallbackTool(String query) {
        String q = query.toLowerCase();
        if (q.contains("画像") || q.contains("偏好") || q.contains("兴趣")) return "query_user_profile";
        if (q.contains("记忆") || q.contains("记住")) return "query_user_memories";
        if (q.contains("详情") || q.contains("信息") || q.contains("账号")) return "query_user_detail";
        return "query_user_profile"; // 默认查画像
    }

    private AgentQueryResult queryUserProfile(String param) {
        UserDTO user = findUser(param);
        if (user == null) {
            return AgentQueryResult.success("未找到用户「" + param + "」，请提供正确的用户名或ID", getCode());
        }

        String profile = userMemoryService.buildUserProfile(user.getId());
        StringBuilder sb = new StringBuilder();
        sb.append("用户「").append(user.getNickname()).append("」(ID:").append(user.getId()).append(") 的画像:\n\n");

        if (profile != null && !profile.isEmpty()) {
            sb.append(profile);
        } else {
            sb.append("该用户暂无画像数据（尚未与AI进行足够的对话）");
        }

        return AgentQueryResult.success(sb.toString(), getCode());
    }

    private AgentQueryResult queryUserMemories(String param) {
        UserDTO user = findUser(param);
        if (user == null) {
            return AgentQueryResult.success("未找到用户「" + param + "」", getCode());
        }

        List<AiUserMemory> memories = userMemoryMapper.selectByUserId(user.getId());
        if (memories == null || memories.isEmpty()) {
            return AgentQueryResult.success("用户「" + user.getNickname() + "」暂无长期记忆数据", getCode());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("用户「").append(user.getNickname()).append("」(ID:").append(user.getId()).append(") 的长期记忆:\n\n");

        // 按类型分组展示
        appendMemoryGroup(sb, memories, "preference", "偏好");
        appendMemoryGroup(sb, memories, "interest", "兴趣");
        appendMemoryGroup(sb, memories, "fact", "已知事实");
        appendMemoryGroup(sb, memories, "decision", "重要决策");
        appendMemoryGroup(sb, memories, "lesson", "经验教训");

        sb.append("\n共 ").append(memories.size()).append(" 条记忆");
        return AgentQueryResult.success(sb.toString(), getCode());
    }

    private AgentQueryResult queryUserDetail(String param) {
        UserDTO user = findUser(param);
        if (user == null) {
            return AgentQueryResult.success("未找到用户「" + param + "」", getCode());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("用户详细信息:\n");
        sb.append("- 昵称: ").append(user.getNickname() != null ? user.getNickname() : "未设置").append("\n");
        sb.append("- 用户ID: ").append(user.getId()).append("\n");
        sb.append("- 用户名: ").append(user.getUsername() != null ? user.getUsername() : "未知").append("\n");
        sb.append("- 邮箱: ").append(user.getEmail() != null ? user.getEmail() : "未设置").append("\n");
        sb.append("- 手机: ").append(user.getPhone() != null ? maskPhone(user.getPhone()) : "未设置").append("\n");
        sb.append("- 状态: ").append(user.getStatus() != null && user.getStatus() == 1 ? "正常" : "禁用").append("\n");
        sb.append("- 传承标志: ").append(user.getHeritageFlag() != null && user.getHeritageFlag() == 1 ? "是" : "否").append("\n");
        if (user.getLastLoginTime() != null) {
            sb.append("- 最近登录: ").append(user.getLastLoginTime()).append("\n");
        }
        if (user.getCreateTime() != null) {
            sb.append("- 注册时间: ").append(user.getCreateTime()).append("\n");
        }

        // 附加画像摘要
        String profile = userMemoryService.buildUserProfile(user.getId());
        if (profile != null && !profile.isEmpty()) {
            sb.append("\n--- AI画像 ---\n").append(profile);
        }

        return AgentQueryResult.success(sb.toString(), getCode());
    }

    // ========== 辅助方法 ==========

    private void appendMemoryGroup(StringBuilder sb, List<AiUserMemory> memories,
                                    String type, String label) {
        boolean hasContent = false;
        for (AiUserMemory m : memories) {
            if (type.equals(m.getMemoryType())) {
                if (!hasContent) {
                    sb.append("### ").append(label).append("\n");
                    hasContent = true;
                }
                sb.append("- ").append(m.getMemoryValue());
                if (m.getConfidence() != null) {
                    sb.append(" (置信度: ").append(m.getConfidence()).append(")");
                }
                sb.append("\n");
            }
        }
        if (hasContent) sb.append("\n");
    }

    private String maskPhone(String phone) {
        if (phone.length() >= 7) {
            return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
        }
        return phone;
    }

    private String extractUserName(String query) {
        if (query == null) return "";
        String[] patterns = {"(.+?)的画像", "(.+?)的偏好", "(.+?)的兴趣", "(.+?)的记忆",
                "(.+?)的信息", "(.+?)的详情", "(.+?)的账号", "查看(.+?)的", "查询(.+?)的"};
        for (String p : patterns) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(p).matcher(query);
            if (m.find()) {
                String name = m.group(1).trim();
                name = name.replaceAll("^(用户|账号)", "").trim();
                if (!name.isEmpty()) return name;
            }
        }
        return query.replaceAll("(查看|查询|画像|偏好|兴趣|记忆|信息|详情|账号|用户|的)", "").trim();
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
}

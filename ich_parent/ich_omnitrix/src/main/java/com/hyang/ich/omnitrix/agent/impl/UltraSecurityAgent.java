package com.hyang.ich.omnitrix.agent.impl;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.omnitrix.agent.AgentContext;
import com.hyang.ich.omnitrix.agent.SubAgent;
import com.hyang.ich.omnitrix.agent.tool.AgentTool;
import com.hyang.ich.omnitrix.agent.tool.ToolCallResult;
import com.hyang.ich.omnitrix.agent.tool.ToolSelector;
import com.hyang.ich.omnitrix.dto.AgentQueryResult;
import com.hyang.ich.system.SystemService;
import com.hyang.ich.system.dto.SysOperationLogDTO;
import com.hyang.ich.user.UserService;
import com.hyang.ich.user.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ultra 安全审计代理 —— 查看操作日志、检测可疑行为、安全巡检
 */
@Slf4j
@Component
public class UltraSecurityAgent implements SubAgent {

    private final SystemService systemService;
    private final UserService userService;
    private final ToolSelector toolSelector;

    private static final List<AgentTool> TOOLS = Arrays.asList(
            AgentTool.of("security_check", "安全巡检、可疑行为检测、失败操作查询", ""),
            AgentTool.of("user_ops", "查看某用户的操作记录", "用户名或ID"),
            AgentTool.of("ops_overview", "查看今日操作概况/操作日志", ""),
            AgentTool.of("ops_search", "按模块搜索操作日志", "模块名")
    );

    public UltraSecurityAgent(SystemService systemService, UserService userService, ToolSelector toolSelector) {
        this.systemService = systemService;
        this.userService = userService;
        this.toolSelector = toolSelector;
    }

    @Override
    public String getCode() { return "ultra_security"; }

    @Override
    public String getName() { return "安全审计"; }

    @Override
    public String getDescription() { return "查看操作日志、检测可疑行为、安全巡检（Ultra专用）"; }

    @Override
    public String getAgentPrompt() {
        return "## 当前任务模式: Ultra 安全审计\n" +
                "你拥有最高权限，可以查看系统所有操作日志。\n" +
                "- 检测失败操作和可疑行为\n" +
                "- 追踪特定用户的操作记录\n" +
                "- 提供安全巡检报告";
    }

    @Override
    public AgentQueryResult execute(String userQuery, AgentContext context) {
        try {
            ToolCallResult toolCall = toolSelector.select(userQuery, TOOLS);
            String selectedTool = toolCall.isNone() ? fallbackTool(userQuery) : toolCall.getToolName();
            String param = toolCall.getParameter() != null ? toolCall.getParameter() : userQuery;

            switch (selectedTool) {
                case "security_check":
                    return securityCheck();
                case "user_ops":
                    return userOps(param, userQuery);
                case "ops_overview":
                    return opsOverview();
                case "ops_search":
                    return opsSearch(param, userQuery);
                default:
                    return securityCheck();
            }
        } catch (Exception e) {
            log.error("UltraSecurityAgent 执行异常: {}", e.getMessage(), e);
            return AgentQueryResult.error(getCode(), e.getMessage());
        }
    }

    private String fallbackTool(String query) {
        if (query.contains("可疑") || query.contains("异常") || query.contains("安全") || query.contains("巡检")
                || query.contains("失败")) return "security_check";
        if (query.contains("操作记录") || query.contains("做过什么")) return "user_ops";
        if (query.contains("今日") || query.contains("概况") || query.contains("总览")) return "ops_overview";
        if (query.contains("模块") || query.contains("搜索") || query.contains("日志")) return "ops_search";
        return "security_check";
    }

    /** 安全巡检：检测可疑行为 */
    private AgentQueryResult securityCheck() {
        StringBuilder sb = new StringBuilder("🔒 安全巡检报告\n\n");

        // 1. 今日操作统计
        int todayOps = systemService.countTodayOps();
        sb.append("📊 今日操作总数: ").append(todayOps).append("\n\n");

        // 2. 最近失败操作
        List<SysOperationLogDTO> failedOps = systemService.listFailedOps(24, 10);
        sb.append("⚠️ 最近24小时失败操作 (").append(failedOps.size()).append("条):\n");
        if (failedOps.isEmpty()) {
            sb.append("  无失败操作，系统运行正常 ✅\n");
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm");
            for (SysOperationLogDTO op : failedOps) {
                sb.append("  - [").append(op.getOperationTime() != null ? sdf.format(op.getOperationTime()) : "").append("] ");
                sb.append(op.getUsername() != null ? op.getUsername() : "未知用户").append(" ");
                sb.append(op.getModule() != null ? "[" + op.getModule() + "]" : "").append(" ");
                sb.append(op.getOperation() != null ? op.getOperation() : "").append(" ");
                sb.append("IP:").append(op.getIp() != null ? op.getIp() : "未知");
                if (op.getErrorMsg() != null && !op.getErrorMsg().isEmpty()) {
                    String errBrief = op.getErrorMsg().length() > 40
                            ? op.getErrorMsg().substring(0, 40) + "..." : op.getErrorMsg();
                    sb.append(" 错误:").append(errBrief);
                }
                sb.append("\n");
            }
        }

        // 3. 安全建议
        sb.append("\n📋 安全建议:\n");
        if (failedOps.size() >= 5) {
            sb.append("  ⚡ 失败操作较多，建议排查是否存在暴力破解或异常访问\n");
        }
        if (todayOps > 500) {
            sb.append("  ⚡ 今日操作量较大，建议关注系统负载\n");
        }
        if (failedOps.isEmpty() && todayOps <= 500) {
            sb.append("  ✅ 系统运行正常，未检测到可疑行为\n");
        }

        return AgentQueryResult.success(sb.toString(), getCode());
    }

    /** 查看某用户的操作记录 */
    private AgentQueryResult userOps(String param, String query) {
        Long targetUserId = resolveUserId(param, query);
        if (targetUserId == null) {
            return AgentQueryResult.success("请指定要查看的用户（用户名、昵称或ID）", getCode());
        }

        List<SysOperationLogDTO> ops = systemService.listUserRecentOps(targetUserId, 20);
        UserDTO user = null;
        try { user = userService.findById(targetUserId); } catch (Exception ignored) {}
        String displayName = user != null && user.getNickname() != null ? user.getNickname()
                : (user != null ? user.getUsername() : "ID:" + targetUserId);

        if (ops == null || ops.isEmpty()) {
            return AgentQueryResult.success("用户「" + displayName + "」暂无操作记录", getCode());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("用户「").append(displayName).append("」最近操作记录:\n\n");
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm");
        for (SysOperationLogDTO op : ops) {
            sb.append("- [").append(op.getOperationTime() != null ? sdf.format(op.getOperationTime()) : "").append("] ");
            sb.append(op.getModule() != null ? "[" + op.getModule() + "] " : "");
            sb.append(op.getOperation() != null ? op.getOperation() : "");
            sb.append(op.getStatus() != null && op.getStatus() == 1 ? " ❌失败" : " ✅");
            if (op.getExecuteTime() != null) sb.append(" (").append(op.getExecuteTime()).append("ms)");
            sb.append("\n");
        }
        sb.append("\n共 ").append(ops.size()).append(" 条记录");
        return AgentQueryResult.success(sb.toString(), getCode());
    }

    /** 今日操作概况 */
    private AgentQueryResult opsOverview() {
        int total = systemService.countTodayOps();
        List<SysOperationLogDTO> failedOps = systemService.listFailedOps(24, 5);

        StringBuilder sb = new StringBuilder("📊 今日操作概况\n\n");
        sb.append("操作总数: ").append(total).append("\n");
        sb.append("失败操作: ").append(failedOps.size()).append(" 条\n\n");

        if (!failedOps.isEmpty()) {
            sb.append("最近失败操作:\n");
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
            for (SysOperationLogDTO op : failedOps) {
                sb.append("- [").append(op.getOperationTime() != null ? sdf.format(op.getOperationTime()) : "").append("] ");
                sb.append(op.getUsername() != null ? op.getUsername() : "").append(" ");
                sb.append(op.getOperation() != null ? op.getOperation() : "").append("\n");
            }
        }

        return AgentQueryResult.success(sb.toString(), getCode());
    }

    /** 按模块搜索操作日志 */
    private AgentQueryResult opsSearch(String param, String query) {
        String module = extractModule(param, query);
        PageResult<SysOperationLogDTO> result = systemService.listOperationLogs(1, 15, null, module);

        if (result.getList() == null || result.getList().isEmpty()) {
            return AgentQueryResult.success("模块「" + (module != null ? module : "全部") + "」暂无操作日志", getCode());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("模块「").append(module != null ? module : "全部").append("」操作日志 (共")
          .append(result.getTotal()).append("条):\n\n");
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm");
        for (SysOperationLogDTO op : result.getList()) {
            sb.append("- [").append(op.getOperationTime() != null ? sdf.format(op.getOperationTime()) : "").append("] ");
            sb.append(op.getUsername() != null ? op.getUsername() : "").append(" ");
            sb.append(op.getOperation() != null ? op.getOperation() : "");
            sb.append(op.getStatus() != null && op.getStatus() == 1 ? " ❌" : "");
            sb.append("\n");
        }
        return AgentQueryResult.success(sb.toString(), getCode());
    }

    private Long resolveUserId(String param, String query) {
        String combined = param + " " + query;
        Matcher m = Pattern.compile("(?:ID|id|用户)[：:]?(\\d+)").matcher(combined);
        if (m.find()) {
            try { return Long.parseLong(m.group(1)); } catch (Exception ignored) {}
        }
        // 尝试按昵称查找
        String[] keywords = {"查看", "查询", "操作记录", "做过什么", "的", "用户", "安全"};
        String cleaned = combined;
        for (String kw : keywords) cleaned = cleaned.replace(kw, " ");
        cleaned = cleaned.trim();
        if (!cleaned.isEmpty()) {
            String[] parts = cleaned.split("\\s+");
            for (String part : parts) {
                if (part.length() >= 1 && part.length() <= 20) {
                    try {
                        UserDTO user = userService.findByNickname(part.trim());
                        if (user != null) return user.getId();
                    } catch (Exception ignored) {}
                }
            }
        }
        return null;
    }

    private String extractModule(String param, String query) {
        String combined = param + " " + query;
        String[] modules = {"用户管理", "内容管理", "商品管理", "订单管理", "系统管理", "AI服务"};
        for (String mod : modules) {
            if (combined.contains(mod)) return mod;
        }
        return null;
    }
}

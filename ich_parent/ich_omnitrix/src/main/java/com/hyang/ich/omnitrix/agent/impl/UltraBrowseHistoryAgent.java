package com.hyang.ich.omnitrix.agent.impl;

import com.hyang.ich.common.BrowseHistoryService;
import com.hyang.ich.common.dto.BrowseHistoryDTO;
import com.hyang.ich.omnitrix.agent.AgentContext;
import com.hyang.ich.omnitrix.agent.SubAgent;
import com.hyang.ich.omnitrix.agent.tool.AgentTool;
import com.hyang.ich.omnitrix.agent.tool.ToolCallResult;
import com.hyang.ich.omnitrix.agent.tool.ToolSelector;
import com.hyang.ich.omnitrix.dto.AgentQueryResult;
import com.hyang.ich.user.UserService;
import com.hyang.ich.user.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ultra 用户行为追踪代理 —— 查看任意用户的浏览历史记录
 * 可以追踪某个用户在某一天做了什么，分析用户行为轨迹
 */
@Slf4j
@Component
public class UltraBrowseHistoryAgent implements SubAgent {

    private final BrowseHistoryService browseHistoryService;
    private final UserService userService;
    private final ToolSelector toolSelector;

    private static final List<AgentTool> TOOLS = Arrays.asList(
            AgentTool.of("track_user_date", "查看某用户某天的浏览记录、某用户某天做了什么", "用户名或ID + 日期"),
            AgentTool.of("track_user_recent", "查看某用户最近的浏览记录", "用户名或ID"),
            AgentTool.of("track_user_type", "查看某用户对某类内容的浏览记录", "用户名或ID + 类型"),
            AgentTool.of("track_user_dates", "查看某用户有哪些天有浏览记录", "用户名或ID")
    );

    private static final Map<String, String> TYPE_MAP = new LinkedHashMap<>();
    static {
        TYPE_MAP.put("非遗", "ich_item");
        TYPE_MAP.put("项目", "ich_item");
        TYPE_MAP.put("传承人", "heritage_man");
        TYPE_MAP.put("活动", "activity");
        TYPE_MAP.put("商品", "product");
        TYPE_MAP.put("文创", "product");
        TYPE_MAP.put("知识", "knowledge");
    }

    private static final Map<String, String> TYPE_LABELS = new LinkedHashMap<>();
    static {
        TYPE_LABELS.put("ich_item", "非遗项目");
        TYPE_LABELS.put("heritage_man", "传承人");
        TYPE_LABELS.put("activity", "活动");
        TYPE_LABELS.put("product", "文创商品");
        TYPE_LABELS.put("knowledge", "知识");
    }

    public UltraBrowseHistoryAgent(BrowseHistoryService browseHistoryService,
                                    UserService userService,
                                    ToolSelector toolSelector) {
        this.browseHistoryService = browseHistoryService;
        this.userService = userService;
        this.toolSelector = toolSelector;
    }

    @Override
    public String getCode() { return "ultra_browse_history"; }

    @Override
    public String getName() { return "用户行为追踪"; }

    @Override
    public String getDescription() { return "查看任意用户的浏览历史记录，追踪用户行为轨迹（Ultra专用）"; }

    @Override
    public String getAgentPrompt() {
        return "## 当前任务模式: Ultra 用户行为追踪\n" +
                "你拥有最高权限，可以查看任意用户的浏览历史记录。\n" +
                "- 浏览记录按天分组展示，可以看到某用户某天的完整行为轨迹\n" +
                "- 记录不可篡改，保证数据真实性\n" +
                "- 注意保护用户隐私，仅在管理需要时使用";
    }

    @Override
    public AgentQueryResult execute(String userQuery, AgentContext context) {
        try {
            ToolCallResult toolCall = toolSelector.select(userQuery, TOOLS);
            String selectedTool = toolCall.isNone() ? fallbackTool(userQuery) : toolCall.getToolName();
            String param = toolCall.getParameter() != null ? toolCall.getParameter() : userQuery;

            // 解析用户标识
            UserDTO targetUser = resolveUser(param, userQuery);
            if (targetUser == null) {
                return AgentQueryResult.success("请指定要查看的用户（用户名、昵称或ID）", getCode());
            }

            switch (selectedTool) {
                case "track_user_date":
                    String date = resolveDate(param, userQuery);
                    return trackUserByDate(targetUser, date);
                case "track_user_recent":
                    return trackUserRecent(targetUser, 7);
                case "track_user_type":
                    String type = resolveType(param, userQuery);
                    return trackUserByType(targetUser, type);
                case "track_user_dates":
                    return trackUserDates(targetUser);
                default:
                    return trackUserRecent(targetUser, 3);
            }
        } catch (Exception e) {
            log.error("UltraBrowseHistoryAgent 执行异常: {}", e.getMessage(), e);
            return AgentQueryResult.error(getCode(), e.getMessage());
        }
    }

    private String fallbackTool(String query) {
        if (query.matches(".*\\d{4}-\\d{2}-\\d{2}.*") || query.contains("某天") || query.contains("那天")
                || query.contains("今天") || query.contains("昨天")) return "track_user_date";
        if (query.contains("哪些天") || query.contains("日期")) return "track_user_dates";
        for (String key : TYPE_MAP.keySet()) {
            if (query.contains(key)) return "track_user_type";
        }
        return "track_user_recent";
    }

    private AgentQueryResult trackUserByDate(UserDTO user, String date) {
        List<BrowseHistoryDTO> records = browseHistoryService.listByDate(user.getId(), date);
        String displayName = user.getNickname() != null ? user.getNickname() : user.getUsername();
        if (records == null || records.isEmpty()) {
            return AgentQueryResult.success("用户「" + displayName + "」在 " + date + " 没有浏览记录", getCode());
        }
        StringBuilder sb = new StringBuilder();
        sb.append("用户「").append(displayName).append("」(ID:").append(user.getId()).append(") ");
        sb.append(date).append(" 的行为轨迹:\n\n");
        formatRecords(sb, records);
        sb.append("\n共 ").append(records.size()).append(" 条记录");
        return AgentQueryResult.success(sb.toString(), getCode());
    }

    private AgentQueryResult trackUserRecent(UserDTO user, int days) {
        List<BrowseHistoryDTO> records = browseHistoryService.listRecent(user.getId(), days);
        String displayName = user.getNickname() != null ? user.getNickname() : user.getUsername();
        if (records == null || records.isEmpty()) {
            return AgentQueryResult.success("用户「" + displayName + "」最近 " + days + " 天没有浏览记录", getCode());
        }

        Map<String, List<BrowseHistoryDTO>> grouped = new LinkedHashMap<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        for (BrowseHistoryDTO r : records) {
            String dateKey = r.getBrowseDate() != null ? sdf.format(r.getBrowseDate()) : "未知日期";
            grouped.computeIfAbsent(dateKey, k -> new java.util.ArrayList<>()).add(r);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("用户「").append(displayName).append("」(ID:").append(user.getId()).append(") ");
        sb.append("最近 ").append(days).append(" 天的行为轨迹:\n\n");
        for (Map.Entry<String, List<BrowseHistoryDTO>> entry : grouped.entrySet()) {
            sb.append("📅 ").append(entry.getKey()).append(" (").append(entry.getValue().size()).append("条)\n");
            formatRecords(sb, entry.getValue());
            sb.append("\n");
        }
        sb.append("共 ").append(records.size()).append(" 条记录");
        return AgentQueryResult.success(sb.toString(), getCode());
    }

    private AgentQueryResult trackUserByType(UserDTO user, String targetType) {
        String label = TYPE_LABELS.getOrDefault(targetType, targetType);
        String displayName = user.getNickname() != null ? user.getNickname() : user.getUsername();
        List<BrowseHistoryDTO> records = browseHistoryService.listByType(user.getId(), targetType, 20);
        if (records == null || records.isEmpty()) {
            return AgentQueryResult.success("用户「" + displayName + "」没有「" + label + "」类型的浏览记录", getCode());
        }
        StringBuilder sb = new StringBuilder();
        sb.append("用户「").append(displayName).append("」的「").append(label).append("」浏览记录:\n\n");
        formatRecords(sb, records);
        sb.append("\n共 ").append(records.size()).append(" 条记录");
        return AgentQueryResult.success(sb.toString(), getCode());
    }

    private AgentQueryResult trackUserDates(UserDTO user) {
        String displayName = user.getNickname() != null ? user.getNickname() : user.getUsername();
        List<String> dates = browseHistoryService.listBrowseDates(user.getId(), 30);
        if (dates == null || dates.isEmpty()) {
            return AgentQueryResult.success("用户「" + displayName + "」最近30天没有浏览记录", getCode());
        }
        StringBuilder sb = new StringBuilder();
        sb.append("用户「").append(displayName).append("」有浏览记录的日期:\n");
        for (String date : dates) {
            int count = browseHistoryService.countByDate(user.getId(), date);
            sb.append("- ").append(date).append(" (").append(count).append("条)\n");
        }
        return AgentQueryResult.success(sb.toString(), getCode());
    }

    private void formatRecords(StringBuilder sb, List<BrowseHistoryDTO> records) {
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm");
        for (BrowseHistoryDTO r : records) {
            String label = TYPE_LABELS.getOrDefault(r.getTargetType(), r.getTargetType());
            String time = r.getBrowseTime() != null ? timeFmt.format(r.getBrowseTime()) : "";
            sb.append("  - [").append(time).append("] ")
              .append("[").append(label).append("] ")
              .append(r.getTargetTitle() != null ? r.getTargetTitle() : "ID:" + r.getTargetId());
            if (r.getDurationSeconds() != null && r.getDurationSeconds() > 0) {
                sb.append(" (停留").append(formatDuration(r.getDurationSeconds())).append(")");
            }
            if (r.getSource() != null && !"web".equals(r.getSource())) {
                sb.append(" [").append(r.getSource()).append("]");
            }
            sb.append("\n");
        }
    }

    private String formatDuration(int seconds) {
        if (seconds < 60) return seconds + "秒";
        int min = seconds / 60;
        int sec = seconds % 60;
        return sec > 0 ? min + "分" + sec + "秒" : min + "分钟";
    }

    private UserDTO resolveUser(String param, String query) {
        // 尝试从参数中提取用户ID
        String combined = param + " " + query;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:ID|id|用户)[：:]?(\\d+)").matcher(combined);
        if (m.find()) {
            try { return userService.findById(Long.parseLong(m.group(1))); } catch (Exception ignored) {}
        }

        // 尝试从参数中提取用户名/昵称
        String[] keywords = {"查看", "查询", "看看", "追踪", "浏览", "记录", "历史", "做了什么",
                "行为", "某天", "今天", "昨天", "最近", "的", "用户"};
        String cleaned = combined;
        for (String kw : keywords) cleaned = cleaned.replace(kw, " ");
        cleaned = cleaned.replaceAll("\\d{4}-\\d{2}-\\d{2}", "").trim();
        for (String key : TYPE_MAP.keySet()) cleaned = cleaned.replace(key, "");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        if (!cleaned.isEmpty()) {
            String[] parts = cleaned.split("\\s+");
            for (String part : parts) {
                if (part.length() >= 1 && part.length() <= 20) {
                    try {
                        UserDTO user = userService.findByNickname(part.trim());
                        if (user != null) return user;
                    } catch (Exception ignored) {}
                }
            }
        }
        return null;
    }

    private String resolveDate(String param, String query) {
        String combined = param + " " + query;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{4}-\\d{2}-\\d{2})").matcher(combined);
        if (m.find()) return m.group(1);
        if (combined.contains("昨天") || combined.contains("昨日"))
            return LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
        if (combined.contains("前天"))
            return LocalDate.now().minusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE);
        return LocalDate.now().toString();
    }

    private String resolveType(String param, String query) {
        String combined = param + " " + query;
        for (Map.Entry<String, String> entry : TYPE_MAP.entrySet()) {
            if (combined.contains(entry.getKey())) return entry.getValue();
        }
        return "ich_item";
    }
}

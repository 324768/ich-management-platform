package com.hyang.ich.omnitrix.agent.impl;

import com.hyang.ich.common.BrowseHistoryService;
import com.hyang.ich.common.dto.BrowseHistoryDTO;
import com.hyang.ich.omnitrix.agent.AgentContext;
import com.hyang.ich.omnitrix.agent.SubAgent;
import com.hyang.ich.omnitrix.agent.tool.AgentTool;
import com.hyang.ich.omnitrix.agent.tool.ToolCallResult;
import com.hyang.ich.omnitrix.agent.tool.ToolSelector;
import com.hyang.ich.omnitrix.dto.AgentQueryResult;
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
 * 用户端浏览历史助手 —— 查询用户浏览记录（不可删除，按时间分类）
 */
@Slf4j
@Component
public class BrowseHistorySubAgent implements SubAgent {

    private final BrowseHistoryService browseHistoryService;
    private final ToolSelector toolSelector;

    private static final List<AgentTool> TOOLS = Arrays.asList(
            AgentTool.of("query_today", "查看今天的浏览记录、今日浏览了什么", "无"),
            AgentTool.of("query_by_date", "查看某一天的浏览记录", "日期(yyyy-MM-dd)"),
            AgentTool.of("query_recent", "查看最近几天的浏览历史、浏览汇总", "天数(默认7)"),
            AgentTool.of("query_by_type", "查看某类浏览记录(非遗项目/商品/传承人/知识)", "类型关键词"),
            AgentTool.of("query_dates", "查看有浏览记录的日期列表", "无")
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

    public BrowseHistorySubAgent(BrowseHistoryService browseHistoryService, ToolSelector toolSelector) {
        this.browseHistoryService = browseHistoryService;
        this.toolSelector = toolSelector;
    }

    @Override
    public String getCode() { return "browse_history_assistant"; }

    @Override
    public String getName() { return "浏览历史助手"; }

    @Override
    public String getDescription() { return "查询用户浏览历史记录（按时间分类，不可删除）"; }

    @Override
    public String getAgentPrompt() {
        return "## 当前任务模式: 浏览历史查询\n" +
                "你可以帮用户查看他们的浏览记录。浏览记录按时间分类展示。\n" +
                "- 浏览记录是系统自动记录的，用户不能删除\n" +
                "- 可以按日期、类型筛选浏览记录\n" +
                "- 展示时按天分组，清晰展示每天浏览了什么";
    }

    @Override
    public AgentQueryResult execute(String userQuery, AgentContext context) {
        try {
            ToolCallResult toolCall = toolSelector.select(userQuery, TOOLS);
            String selectedTool = toolCall.isNone() ? fallbackTool(userQuery) : toolCall.getToolName();
            String param = toolCall.getParameter();

            switch (selectedTool) {
                case "query_today":
                    return queryByDate(context.getUserId(), LocalDate.now().toString());
                case "query_by_date":
                    return queryByDate(context.getUserId(), resolveDate(param));
                case "query_recent":
                    return queryRecent(context.getUserId(), resolveDays(param));
                case "query_by_type":
                    return queryByType(context.getUserId(), resolveType(param != null ? param : userQuery));
                case "query_dates":
                    return queryDates(context.getUserId());
                default:
                    return queryRecent(context.getUserId(), 3);
            }
        } catch (Exception e) {
            log.error("BrowseHistorySubAgent 执行异常: {}", e.getMessage(), e);
            return AgentQueryResult.error(getCode(), e.getMessage());
        }
    }

    private String fallbackTool(String query) {
        String q = query.toLowerCase();
        if (q.contains("今天") || q.contains("今日")) return "query_today";
        if (q.contains("哪些天") || q.contains("日期列表") || q.contains("哪几天")) return "query_dates";
        for (String key : TYPE_MAP.keySet()) {
            if (q.contains(key)) return "query_by_type";
        }
        if (q.matches(".*\\d{4}-\\d{2}-\\d{2}.*")) return "query_by_date";
        if (q.contains("最近") || q.contains("近期")) return "query_recent";
        return "query_recent";
    }

    private AgentQueryResult queryByDate(Long userId, String date) {
        try {
            List<BrowseHistoryDTO> records = browseHistoryService.listByDate(userId, date);
            if (records == null || records.isEmpty()) {
                return AgentQueryResult.success(date + " 没有浏览记录", getCode());
            }
            StringBuilder sb = new StringBuilder();
            sb.append("📅 ").append(date).append(" 的浏览记录:\n\n");
            formatRecords(sb, records);
            sb.append("\n共 ").append(records.size()).append(" 条记录");
            return AgentQueryResult.success(sb.toString(), getCode());
        } catch (Exception e) {
            return AgentQueryResult.success("查询浏览记录失败: " + e.getMessage(), getCode());
        }
    }

    private AgentQueryResult queryRecent(Long userId, int days) {
        try {
            List<BrowseHistoryDTO> records = browseHistoryService.listRecent(userId, days);
            if (records == null || records.isEmpty()) {
                return AgentQueryResult.success("最近 " + days + " 天没有浏览记录", getCode());
            }

            // 按天分组
            Map<String, List<BrowseHistoryDTO>> grouped = new LinkedHashMap<>();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            for (BrowseHistoryDTO r : records) {
                String dateKey = r.getBrowseDate() != null ? sdf.format(r.getBrowseDate()) : "未知日期";
                grouped.computeIfAbsent(dateKey, k -> new java.util.ArrayList<>()).add(r);
            }

            StringBuilder sb = new StringBuilder();
            sb.append("最近 ").append(days).append(" 天的浏览记录:\n\n");
            for (Map.Entry<String, List<BrowseHistoryDTO>> entry : grouped.entrySet()) {
                sb.append("📅 ").append(entry.getKey()).append(" (").append(entry.getValue().size()).append("条)\n");
                formatRecords(sb, entry.getValue());
                sb.append("\n");
            }
            sb.append("共 ").append(records.size()).append(" 条记录");
            return AgentQueryResult.success(sb.toString(), getCode());
        } catch (Exception e) {
            return AgentQueryResult.success("查询浏览记录失败: " + e.getMessage(), getCode());
        }
    }

    private AgentQueryResult queryByType(Long userId, String targetType) {
        try {
            String label = TYPE_LABELS.getOrDefault(targetType, targetType);
            List<BrowseHistoryDTO> records = browseHistoryService.listByType(userId, targetType, 20);
            if (records == null || records.isEmpty()) {
                return AgentQueryResult.success("没有「" + label + "」类型的浏览记录", getCode());
            }
            StringBuilder sb = new StringBuilder();
            sb.append("「").append(label).append("」类型的浏览记录:\n\n");
            formatRecords(sb, records);
            sb.append("\n共 ").append(records.size()).append(" 条记录");
            return AgentQueryResult.success(sb.toString(), getCode());
        } catch (Exception e) {
            return AgentQueryResult.success("查询浏览记录失败: " + e.getMessage(), getCode());
        }
    }

    private AgentQueryResult queryDates(Long userId) {
        try {
            List<String> dates = browseHistoryService.listBrowseDates(userId, 30);
            if (dates == null || dates.isEmpty()) {
                return AgentQueryResult.success("最近30天没有浏览记录", getCode());
            }
            StringBuilder sb = new StringBuilder();
            sb.append("有浏览记录的日期（最近30天）:\n");
            for (String date : dates) {
                int count = browseHistoryService.countByDate(userId, date);
                sb.append("- ").append(date).append(" (").append(count).append("条)\n");
            }
            return AgentQueryResult.success(sb.toString(), getCode());
        } catch (Exception e) {
            return AgentQueryResult.success("查询浏览日期失败: " + e.getMessage(), getCode());
        }
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
            sb.append("\n");
        }
    }

    private String formatDuration(int seconds) {
        if (seconds < 60) return seconds + "秒";
        int min = seconds / 60;
        int sec = seconds % 60;
        return sec > 0 ? min + "分" + sec + "秒" : min + "分钟";
    }

    private String resolveDate(String param) {
        if (param != null && param.matches("\\d{4}-\\d{2}-\\d{2}")) return param;
        if (param != null) {
            if (param.contains("昨天") || param.contains("昨日"))
                return LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
            if (param.contains("前天"))
                return LocalDate.now().minusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        return LocalDate.now().toString();
    }

    private int resolveDays(String param) {
        if (param == null || param.isEmpty()) return 7;
        try { return Integer.parseInt(param.replaceAll("[^0-9]", "")); }
        catch (Exception e) { return 7; }
    }

    private String resolveType(String query) {
        for (Map.Entry<String, String> entry : TYPE_MAP.entrySet()) {
            if (query.contains(entry.getKey())) return entry.getValue();
        }
        return "ich_item";
    }
}

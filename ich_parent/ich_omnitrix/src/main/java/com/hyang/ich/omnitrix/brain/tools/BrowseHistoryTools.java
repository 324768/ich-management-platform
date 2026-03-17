package com.hyang.ich.omnitrix.brain.tools;

import com.hyang.ich.common.BrowseHistoryService;
import com.hyang.ich.common.dto.BrowseHistoryDTO;
import com.hyang.ich.omnitrix.brain.AiRequestContext;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 浏览历史工具 — 替代原 BrowseHistorySubAgent。
 */
@Slf4j
@Component
public class BrowseHistoryTools {

    private final BrowseHistoryService browseHistoryService;

    private static final Map<String, String> TYPE_LABELS = new LinkedHashMap<>();
    static {
        TYPE_LABELS.put("ich_item", "非遗项目");
        TYPE_LABELS.put("culture", "非遗文化");
        TYPE_LABELS.put("heritage_man", "传承人");
        TYPE_LABELS.put("activity", "活动");
        TYPE_LABELS.put("product", "文创商品");
        TYPE_LABELS.put("knowledge", "知识");
    }

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

    public BrowseHistoryTools(BrowseHistoryService browseHistoryService) {
        this.browseHistoryService = browseHistoryService;
    }

    @Tool("查看最近几天的浏览历史。参数: 天数（如'7'表示最近7天，默认3天）")
    public String queryRecentHistory(String daysStr) {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        Long userId = AiRequestContext.getUserId();
        int days = 3;
        try { if (daysStr != null) days = Integer.parseInt(daysStr.replaceAll("[^0-9]", "")); } catch (Exception ignored) {}
        try {
            List<BrowseHistoryDTO> records = browseHistoryService.listRecent(userId, days);
            if (records == null || records.isEmpty()) {
                AiRequestContext.recordToolFailure();
                return ToolResultWrapper.empty("最近 " + days + " 天没有浏览记录")
                        .withSearchEmptyHint("浏览记录").toXml();
            }

            Map<String, List<BrowseHistoryDTO>> grouped = new LinkedHashMap<>();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            for (BrowseHistoryDTO r : records) {
                String dateKey = r.getBrowseDate() != null ? sdf.format(r.getBrowseDate()) : "未知日期";
                grouped.computeIfAbsent(dateKey, k -> new java.util.ArrayList<>()).add(r);
            }

            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, List<BrowseHistoryDTO>> entry : grouped.entrySet()) {
                sb.append(entry.getKey()).append(" (").append(entry.getValue().size()).append("条)\n");
                formatRecords(sb, entry.getValue());
                sb.append("\n");
            }
            AiRequestContext.recordToolSuccess();
            return ToolResultWrapper.success("最近" + days + "天共" + records.size() + "条浏览记录", sb.toString())
                    .withSearchSuccessHint("浏览记录").toXml();
        } catch (Exception e) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.error("查询浏览记录失败: " + e.getMessage()).withErrorHint().toXml();
        }
    }

    @Tool("查看今天的浏览记录")
    public String queryTodayHistory() {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        Long userId = AiRequestContext.getUserId();
        String today = LocalDate.now().toString();
        try {
            List<BrowseHistoryDTO> records = browseHistoryService.listByDate(userId, today);
            if (records == null || records.isEmpty()) {
                AiRequestContext.recordToolFailure();
                return ToolResultWrapper.empty("今天没有浏览记录").withSearchEmptyHint("浏览记录").toXml();
            }
            StringBuilder sb = new StringBuilder();
            formatRecords(sb, records);
            AiRequestContext.recordToolSuccess();
            return ToolResultWrapper.success("今天共" + records.size() + "条浏览记录", sb.toString())
                    .withSearchSuccessHint("浏览记录").toXml();
        } catch (Exception e) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.error("查询浏览记录失败: " + e.getMessage()).withErrorHint().toXml();
        }
    }

    @Tool("按类型查看浏览记录。参数: 类型关键词（如'商品'、'非遗'、'传承人'、'活动'）")
    public String queryHistoryByType(String typeKeyword) {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        Long userId = AiRequestContext.getUserId();
        String targetType = "ich_item";
        for (Map.Entry<String, String> entry : TYPE_MAP.entrySet()) {
            if (typeKeyword != null && typeKeyword.contains(entry.getKey())) {
                targetType = entry.getValue();
                break;
            }
        }
        String label = TYPE_LABELS.getOrDefault(targetType, targetType);
        try {
            List<BrowseHistoryDTO> records = browseHistoryService.listByType(userId, targetType, 20);
            if (records == null || records.isEmpty()) {
                AiRequestContext.recordToolFailure();
                return ToolResultWrapper.empty("没有「" + label + "」类型的浏览记录")
                        .withSearchEmptyHint("浏览记录").toXml();
            }
            StringBuilder sb = new StringBuilder();
            formatRecords(sb, records);
            AiRequestContext.recordToolSuccess();
            return ToolResultWrapper.success("「" + label + "」类型共" + records.size() + "条记录", sb.toString())
                    .withSearchSuccessHint("浏览记录").toXml();
        } catch (Exception e) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.error("查询浏览记录失败: " + e.getMessage()).withErrorHint().toXml();
        }
    }

    @Tool("查看某一天的浏览记录。参数: 日期（yyyy-MM-dd格式，或'昨天'、'前天'）")
    public String queryHistoryByDate(String dateStr) {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        Long userId = AiRequestContext.getUserId();
        String date = resolveDate(dateStr);
        try {
            List<BrowseHistoryDTO> records = browseHistoryService.listByDate(userId, date);
            if (records == null || records.isEmpty()) {
                AiRequestContext.recordToolFailure();
                return ToolResultWrapper.empty(date + " 没有浏览记录")
                        .withSearchEmptyHint("浏览记录").toXml();
            }
            StringBuilder sb = new StringBuilder();
            formatRecords(sb, records);
            AiRequestContext.recordToolSuccess();
            return ToolResultWrapper.success(date + " 共" + records.size() + "条浏览记录", sb.toString())
                    .withSearchSuccessHint("浏览记录").toXml();
        } catch (Exception e) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.error("查询浏览记录失败: " + e.getMessage()).withErrorHint().toXml();
        }
    }

    private void formatRecords(StringBuilder sb, List<BrowseHistoryDTO> records) {
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm");
        for (BrowseHistoryDTO r : records) {
            String label = TYPE_LABELS.getOrDefault(r.getTargetType(), r.getTargetType());
            String time = r.getBrowseTime() != null ? timeFmt.format(r.getBrowseTime()) : "";
            sb.append("  - [").append(time).append("] [").append(label).append("] ")
                    .append(r.getTargetTitle() != null ? r.getTargetTitle() : "ID:" + r.getTargetId());
            if (r.getDurationSeconds() != null && r.getDurationSeconds() > 0) {
                sb.append(" (停留").append(formatDuration(r.getDurationSeconds())).append(")");
            }
            sb.append("\n");
        }
    }

    private String formatDuration(int seconds) {
        if (seconds < 60) return seconds + "秒";
        int min = seconds / 60, sec = seconds % 60;
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
}

package com.hyang.ich.omnitrix.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class DashboardStatsVO {

    private int totalConversations;
    private int totalMessages;
    private int todayMessages;
    private Double avgScore;
    private Double avgLatencyMs;
    private List<Map<String, Object>> topAgents;
    private Map<String, Object> scoreDistribution;

    /** Agent 性能统计（平均延迟/成功率/调用次数/最小最大延迟） */
    private List<Map<String, Object>> agentPerformance;
    /** Span 类型统计（REQUEST/AGENT/TOOL/LLM 各类型计数和延迟） */
    private List<Map<String, Object>> spanTypeStats;
}

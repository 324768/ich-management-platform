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
}

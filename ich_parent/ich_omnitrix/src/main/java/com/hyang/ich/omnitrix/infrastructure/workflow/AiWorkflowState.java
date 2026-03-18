package com.hyang.ich.omnitrix.infrastructure.workflow;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流执行状态
 * 参考 Kortex AiWorkflowState 设计
 */
@Data
@Builder
public class AiWorkflowState {
    
    private String sessionId;
    private String userQuery;
    private int currentIteration;
    private int maxIterations;
    
    @Builder.Default
    private List<ToolCallRecord> toolCalls = new ArrayList<>();
    
    @Builder.Default
    private List<String> reflections = new ArrayList<>();
    
    private String currentAnswer;
    private DecisionAction lastAction;
    private boolean terminated;
    
    // ========== Kortex 扩展字段 ==========
    
    // 用户画像/上下文
    private String userProfile;
    
    // 对话历史摘要
    private String conversationSummary;
    
    // 意图分析结果
    private String intentAnalysis;
    
    // 工具调用循环检测
    @Builder.Default
    private Map<String, Integer> toolCallCounts = new HashMap<>();
    
    // REPLAN 循环检测
    @Builder.Default
    private List<String> replanHistory = new ArrayList<>();
    
    // 上一个工具调用结果
    private String lastToolResult;
    
    // 连续成功工具调用计数（用于判定数据充足）
    @Builder.Default
    private int consecutiveSuccessfulToolCalls = 0;
    
    // 当前是否在反思模式
    @Builder.Default
    private boolean inReflectionMode = false;
    
    // 错误信息
    private String errorMessage;
    
    // ========== 方法 ==========
    
    public boolean reachedMaxIterations() {
        return currentIteration >= maxIterations;
    }
    
    public void incrementIteration() {
        this.currentIteration++;
    }
    
    /**
     * 记录工具调用 - 用于循环检测
     */
    public void recordToolCall(String toolName) {
        toolCallCounts.merge(toolName, 1, Integer::sum);
    }
    
    /**
     * 检查是否陷入工具调用循环
     */
    public boolean isToolCallLooping() {
        // 同一个工具调用超过 3 次视为循环
        return toolCallCounts.values().stream().anyMatch(count -> count > 3);
    }
    
    /**
     * 记录反思 - 用于循环检测
     */
    public void recordReplan(String reflection) {
        replanHistory.add(reflection);
    }
    
    /**
     * 检查是否陷入反思循环
     */
    public boolean isReplanLooping() {
        // 连续超过 2 次 REPLAN 视为循环
        if (replanHistory.size() < 2) return false;
        return replanHistory.get(replanHistory.size() - 1)
                .equals(replanHistory.get(replanHistory.size() - 2));
    }
    
    /**
     * 重置连续成功计数
     */
    public void resetConsecutiveSuccess() {
        this.consecutiveSuccessfulToolCalls = 0;
    }
    
    /**
     * 增加连续成功计数
     */
    public void incrementConsecutiveSuccess() {
        this.consecutiveSuccessfulToolCalls++;
    }
    
    /**
     * 判断数据是否充足（连续成功工具调用 >= 2）
     */
    public boolean hasSufficientData() {
        return consecutiveSuccessfulToolCalls >= 2;
    }
}

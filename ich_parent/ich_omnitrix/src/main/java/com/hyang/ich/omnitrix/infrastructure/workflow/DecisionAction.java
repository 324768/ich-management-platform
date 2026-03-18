package com.hyang.ich.omnitrix.infrastructure.workflow;

/**
 * 决策动作枚举
 */
public enum DecisionAction {
    CALL_TOOL,     // 调用工具获取数据
    DIRECT_ANSWER, // 直接生成答案
    REPLAN,        // 重新规划任务
    FINISH         // 完成任务
}

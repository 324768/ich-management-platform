package com.hyang.ich.omnitrix.agent.tool;

import lombok.Data;

/**
 * LLM 工具调用解析结果。
 */
@Data
public class ToolCallResult {

    private String toolName;
    private String parameter;

    public static ToolCallResult of(String toolName, String parameter) {
        ToolCallResult r = new ToolCallResult();
        r.setToolName(toolName);
        r.setParameter(parameter);
        return r;
    }

    public static ToolCallResult none() {
        return of("none", null);
    }

    public boolean isNone() {
        return "none".equals(toolName) || toolName == null;
    }
}

package com.hyang.ich.omnitrix.agent.tool;

import lombok.Data;

/**
 * 子代理可调用的工具定义。
 * LLM 根据用户意图选择要调用的工具，并提取参数。
 */
@Data
public class AgentTool {

    private String name;
    private String description;
    private String parameterHint;

    public static AgentTool of(String name, String description, String parameterHint) {
        AgentTool tool = new AgentTool();
        tool.setName(name);
        tool.setDescription(description);
        tool.setParameterHint(parameterHint);
        return tool;
    }

    /**
     * 格式化为 Prompt 文本
     */
    public String toPromptLine() {
        StringBuilder sb = new StringBuilder();
        sb.append("- ").append(name).append(": ").append(description);
        if (parameterHint != null && !parameterHint.isEmpty()) {
            sb.append(" (参数: ").append(parameterHint).append(")");
        }
        return sb.toString();
    }
}

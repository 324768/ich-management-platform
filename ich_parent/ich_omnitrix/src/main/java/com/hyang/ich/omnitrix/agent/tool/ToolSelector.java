package com.hyang.ich.omnitrix.agent.tool;

import com.hyang.ich.omnitrix.infrastructure.llm.LlmClient;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 使用辅助 LLM 模型（如 deepseek-ai/DeepSeek-V3）来选择工具并提取参数。
 * 替代 SubAgent 中硬编码的 if(query.contains(...)) 逻辑。
 */
@Slf4j
@Component
public class ToolSelector {

    private static final Pattern TOOL_PATTERN = Pattern.compile(
            "\\{\\s*\"tool\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"param\"\\s*:\\s*\"([^\"]*)\"\\s*\\}",
            Pattern.DOTALL
    );

    private static final String TOOL_SELECT_PROMPT =
            "你是一个工具选择器。根据用户问题，选择最合适的工具。\n\n" +
            "可用工具:\n%s\n\n" +
            "规则:\n" +
            "- 只能选择列表中的工具\n" +
            "- 如果没有合适的工具，选择 none\n" +
            "- param 是从用户问题中提取的关键参数（如搜索词、日期等），如果不需要参数则留空\n\n" +
            "严格按以下JSON格式输出，不要输出其他内容:\n" +
            "{\"tool\":\"工具名\",\"param\":\"参数值\"}";

    private final LlmClient llmClient;

    public ToolSelector(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * 使用辅助 LLM 选择工具并提取参数
     */
    public ToolCallResult select(String userQuery, List<AgentTool> tools) {
        if (tools == null || tools.isEmpty()) {
            return ToolCallResult.none();
        }

        try {
            // 构建工具列表文本
            StringBuilder toolList = new StringBuilder();
            for (AgentTool tool : tools) {
                toolList.append(tool.toPromptLine()).append("\n");
            }
            toolList.append("- none: 没有合适的工具匹配用户问题");

            String prompt = String.format(TOOL_SELECT_PROMPT, toolList.toString());

            // 使用 JSON Mode 强制输出合法 JSON
            LlmResponse response = llmClient.chatAuxiliaryJson(prompt, new ArrayList<>(), userQuery);
            String content = response.getContent();

            if (content != null) {
                Matcher matcher = TOOL_PATTERN.matcher(content.trim());
                if (matcher.find()) {
                    String toolName = matcher.group(1).trim();
                    String param = matcher.group(2).trim();
                    log.debug("LLM 工具选择: tool={}, param={}, query='{}'", toolName, param, userQuery);
                    return ToolCallResult.of(toolName, param);
                }
            }

            log.debug("LLM 工具选择未匹配JSON，回退: content={}", content);
        } catch (Exception e) {
            log.warn("LLM 工具选择失败(回退关键词): {}", e.getMessage());
        }

        return ToolCallResult.none();
    }
}

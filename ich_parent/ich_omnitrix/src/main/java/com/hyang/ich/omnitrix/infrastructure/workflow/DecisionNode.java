package com.hyang.ich.omnitrix.infrastructure.workflow;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.service.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 决策节点 - AI 自主决策下一步行动
 * 参考 Kortex DecisionNode 设计
 */
@Slf4j
@Component
public class DecisionNode {
    
    private final ChatLanguageModel chatModel;
    private final StreamingChatLanguageModel streamingChatModel;
    
    private static final String DECISION_PROMPT = """
            你是一个任务规划助手。根据当前任务状态，决定下一步行动。

            【任务】
            %s

            【已执行步骤】
            %s

            【上次行动】
            %s

            【已有信息】
            %s

            【可用行动】
            - CALL_TOOL: 需要调用工具获取更多信息（如查询数据库、搜索内容等）
            - DIRECT_ANSWER: 信息足够，可以直接生成答案
            - REPLAN: 任务复杂或遇到问题，需要重新规划
            - FINISH: 任务已完成

            【决策原则】
            1. 如果任务需要获取最新信息、查询数据、搜索内容 → CALL_TOOL
            2. 如果已有足够信息回答用户问题 → DIRECT_ANSWER
            3. 如果之前行动失败或遇到困难 → REPLAN
            4. 如果任务已完成 → FINISH

            请以 JSON 格式返回：
            {"action": "行动名称", "reasoning": "简短理由", "toolName": "工具名(仅CALL_TOOL需要)", "toolArgs": "参数(仅CALL_TOOL需要)"}
            """;

    public DecisionNode(ChatLanguageModel chatModel,
                        StreamingChatLanguageModel streamingChatModel) {
        this.chatModel = chatModel;
        this.streamingChatModel = streamingChatModel;
    }
    
    /**
     * 同步决策
     */
    public Decision decide(AiWorkflowState state) {
        String prompt = buildDecisionPrompt(state);
        
        try {
            Result<String> result = chatModel.chat(prompt);
            String content = result.content();
            
            Decision decision = parseResponse(content);
            log.info("决策结果: action={}, reasoning={}, toolName={}", 
                    decision.getAction(), decision.getReasoning(), decision.getToolName());
            
            return decision;
            
        } catch (Exception e) {
            log.error("决策节点执行失败: {}", e.getMessage());
            return Decision.builder()
                    .action(DecisionAction.DIRECT_ANSWER)
                    .reasoning("决策失败，默认生成答案: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * 构建决策 Prompt
     */
    private String buildDecisionPrompt(AiWorkflowState state) {
        StringBuilder sb = new StringBuilder();
        
        // 任务
        sb.append(String.format(DECISION_PROMPT,
                state.getUserQuery(),
                formatToolCalls(state.getToolCalls()),
                state.getLastAction() != null ? state.getLastAction().name() : "无",
                formatCurrentInfo(state)
        ));
        
        return sb.toString();
    }
    
    private String formatToolCalls(List<ToolCallRecord> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return "暂无";
        }
        
        StringBuilder sb = new StringBuilder();
        for (ToolCallRecord call : toolCalls) {
            sb.append(String.format("- %s: %s\n", call.getToolName(), 
                    call.getResult() != null ? call.getResult().substring(0, Math.min(100, call.getResult().length())) : ""));
        }
        return sb.toString();
    }
    
    private String formatCurrentInfo(AiWorkflowState state) {
        StringBuilder sb = new StringBuilder();
        
        if (state.getLastToolResult() != null) {
            sb.append("最新工具结果: ").append(state.getLastToolResult().substring(0, 
                    Math.min(200, state.getLastToolResult().length()))).append("\n");
        }
        
        if (state.isToolCallLooping()) {
            sb.append("⚠️ 检测到工具调用循环\n");
        }
        
        if (state.isReplanLooping()) {
            sb.append("⚠️ 检测到反思循环\n");
        }
        
        return sb.length() > 0 ? sb.toString() : "暂无";
    }
    
    /**
     * 解析 LLM 响应
     */
    private Decision parseResponse(String response) {
        try {
            // 简单 JSON 解析
            String actionStr = extractJsonValue(response, "action");
            String reasoning = extractJsonValue(response, "reasoning");
            String toolName = extractJsonValue(response, "toolName");
            String toolArgs = extractJsonValue(response, "toolArgs");
            
            DecisionAction action;
            try {
                action = DecisionAction.valueOf(actionStr);
            } catch (IllegalArgumentException e) {
                action = DecisionAction.DIRECT_ANSWER;
            }
            
            return Decision.builder()
                    .action(action)
                    .reasoning(reasoning)
                    .toolName(toolName)
                    .toolArgs(toolArgs)
                    .build();
                    
        } catch (Exception e) {
            log.warn("解析决策响应失败: {}", response);
            return Decision.builder()
                    .action(DecisionAction.DIRECT_ANSWER)
                    .reasoning("解析失败，默认生成答案")
                    .build();
        }
    }
    
    private String extractJsonValue(String json, String key) {
        // 简单的 JSON 解析
        String pattern = "\"" + key + "\"";
        int start = json.indexOf(pattern);
        if (start == -1) return null;
        
        start = json.indexOf(":", start) + 1;
        // 跳过空格
        while (start < json.length() && json.charAt(start) == ' ') start++;
        
        if (start >= json.length()) return null;
        
        char quote = json.charAt(start);
        if (quote != '"' && quote != '{' && quote != '[') {
            // 数字或布尔值
            int end = start;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') {
                end++;
            }
            return json.substring(start, end).trim();
        }
        
        if (quote == '"') {
            start++;
            int end = start;
            while (end < json.length() && json.charAt(end) != '"') {
                if (json.charAt(end) == '\\') end++; // skip escaped
                end++;
            }
            return json.substring(start, end);
        }
        
        // 对象或数组
        int end = json.indexOf(",", start);
        if (end == -1) end = json.indexOf("}", start);
        if (end == -1) return null;
        
        return json.substring(start, end).trim();
    }
}

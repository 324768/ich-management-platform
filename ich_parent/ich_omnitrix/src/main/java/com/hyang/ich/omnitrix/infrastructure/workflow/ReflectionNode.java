package com.hyang.ich.omnitrix.infrastructure.workflow;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 反思节点 - 任务失败或复杂时重新规划
 * 参考 Kortex ReflectionNode 设计
 */
@Slf4j
@Component
public class ReflectionNode {
    
    private final ChatLanguageModel chatModel;
    
    private static final String REFLECTION_PROMPT = """
            你是一个任务反思助手。分析当前任务执行情况，找出问题和解决方案。

            【原始任务】
            %s

            【已执行的工具调用】
            %s

            【上次行动】
            %s

            【请分析并返回 JSON】
            {"problemAnalysis": "问题分析", "suggestedAction": "下一步建议", "looping": true/false}
            """;
    
    public ReflectionNode(ChatLanguageModel chatModel) {
        this.chatModel = chatModel;
    }
    
    /**
     * 执行反思
     */
    public ReflectionResult reflect(AiWorkflowState state) {
        String prompt = buildReflectionPrompt(state);
        
        try {
            Result<String> result = chatModel.chat(prompt);
            String content = result.content();
            
            ReflectionResult reflection = parseReflection(content);
            
            // 记录反思历史
            state.recordReplan(reflection.getProblemAnalysis());
            
            log.info("反思完成: looping={}, suggestedAction={}", 
                    reflection.isLooping(), reflection.getSuggestedAction());
            
            return reflection;
            
        } catch (Exception e) {
            log.error("反思执行失败: {}", e.getMessage());
            return ReflectionResult.builder()
                    .problemAnalysis("反思失败: " + e.getMessage())
                    .suggestedAction("FINISH")
                    .looping(false)
                    .build();
        }
    }
    
    private String buildReflectionPrompt(AiWorkflowState state) {
        return String.format(REFLECTION_PROMPT,
                state.getUserQuery(),
                formatToolCalls(state.getToolCalls()),
                state.getLastAction() != null ? state.getLastAction().name() : "无"
        );
    }
    
    private String formatToolCalls(java.util.List<ToolCallRecord> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return "暂无";
        }
        
        StringBuilder sb = new StringBuilder();
        for (ToolCallRecord call : toolCalls) {
            sb.append(String.format("- %s: %s (success=%s)\n", 
                    call.getToolName(), 
                    call.getResult() != null ? call.getResult().substring(0, 50) : "无结果",
                    call.isSuccess()));
        }
        return sb.toString();
    }
    
    private ReflectionResult parseReflection(String content) {
        try {
            String problem = extractJsonValue(content, "problemAnalysis");
            String suggested = extractJsonValue(content, "suggestedAction");
            String loopingStr = extractJsonValue(content, "looping");
            
            boolean looping = "true".equalsIgnoreCase(loopingStr);
            
            return ReflectionResult.builder()
                    .problemAnalysis(problem != null ? problem : "无")
                    .suggestedAction(suggested != null ? suggested : "FINISH")
                    .looping(looping)
                    .build();
                    
        } catch (Exception e) {
            log.warn("解析反思结果失败: {}", content);
            return ReflectionResult.builder()
                    .problemAnalysis("解析失败")
                    .suggestedAction("FINISH")
                    .looping(false)
                    .build();
        }
    }
    
    private String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\"";
        int start = json.indexOf(pattern);
        if (start == -1) return null;
        
        start = json.indexOf(":", start) + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;
        
        if (start >= json.length()) return null;
        
        char quote = json.charAt(start);
        if (quote == '"') {
            start++;
            int end = start;
            while (end < json.length() && json.charAt(end) != '"') {
                if (json.charAt(end) == '\\') end++;
                end++;
            }
            return json.substring(start, end);
        }
        
        int end = start;
        while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') {
            end++;
        }
        return json.substring(start, end).trim();
    }
}

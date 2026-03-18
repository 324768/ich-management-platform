package com.hyang.ich.omnitrix.infrastructure.workflow;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 工具调用节点
 * 参考 Kortex ToolCallNode 设计
 */
@Slf4j
@Component
public class ToolCallNode {
    
    /**
     * 执行工具调用
     */
    public ToolCallResult execute(AiWorkflowState state, Decision decision, List<Object> tools) {
        long startTime = System.currentTimeMillis();
        String toolName = decision.getToolName();
        
        if (toolName == null || toolName.isEmpty()) {
            return ToolCallResult.builder()
                    .toolName("unknown")
                    .content("")
                    .success(false)
                    .error("决策未指定工具名称")
                    .durationMs((int) (System.currentTimeMillis() - startTime))
                    .build();
        }
        
        try {
            // 查找工具
            Object tool = findTool(tools, toolName);
            if (tool == null) {
                return ToolCallResult.builder()
                        .toolName(toolName)
                        .content("")
                        .success(false)
                        .error("工具不存在: " + toolName)
                        .durationMs((int) (System.currentTimeMillis() - startTime))
                        .build();
            }
            
            // 调用工具
            String args = decision.getToolArgs();
            String result = callTool(tool, toolName, args);
            
            // 记录到状态
            state.recordToolCall(toolName);
            state.setLastToolResult(result);
            
            // 判断成功
            boolean callSuccess = result != null && !result.isEmpty();
            if (callSuccess) {
                state.incrementConsecutiveSuccess();
            } else {
                state.resetConsecutiveSuccess();
            }
            
            log.info("工具调用完成: tool={}, success={}, resultLength={}, duration={}ms", 
                    toolName, callSuccess, result != null ? result.length() : 0, 
                    System.currentTimeMillis() - startTime);
            
            return ToolCallResult.builder()
                    .toolName(toolName)
                    .content(result != null ? result : "")
                    .success(callSuccess)
                    .durationMs((int) (System.currentTimeMillis() - startTime))
                    .build();
                    
        } catch (Exception e) {
            log.error("工具调用失败: tool={}, error={}", toolName, e.getMessage());
            state.resetConsecutiveSuccess();
            
            return ToolCallResult.builder()
                    .toolName(toolName)
                    .content("")
                    .success(false)
                    .error(e.getMessage())
                    .durationMs((int) (System.currentTimeMillis() - startTime))
                    .build();
        }
    }
    
    /**
     * 查找工具
     */
    private Object findTool(List<Object> tools, String toolName) {
        if (tools == null || toolName == null) return null;
        
        for (Object tool : tools) {
            // 匹配类名
            if (tool.getClass().getSimpleName().equalsIgnoreCase(toolName) ||
                tool.getClass().getName().toLowerCase().contains(toolName.toLowerCase())) {
                return tool;
            }
            
            // 匹配 @Tool 注解的方法名
            for (Method method : tool.getClass().getMethods()) {
                if (method.isAnnotationPresent(dev.langchain4j.agent.tool.Tool.class)) {
                    dev.langchain4j.agent.tool.Tool annotation = 
                            method.getAnnotation(dev.langchain4j.agent.tool.Tool.class);
                    if (annotation.value().equalsIgnoreCase(toolName) ||
                        annotation.name().equalsIgnoreCase(toolName)) {
                        return tool;
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * 调用工具方法
     */
    private String callTool(Object tool, String toolName, String args) throws Exception {
        // 首先尝试找 @Tool 注解的方法
        for (Method method : tool.getClass().getMethods()) {
            if (method.isAnnotationPresent(dev.langchain4j.agent.tool.Tool.class)) {
                dev.langchain4j.agent.tool.Tool annotation = 
                        method.getAnnotation(dev.langchain4j.agent.tool.Tool.class);
                
                if (annotation.value().equalsIgnoreCase(toolName) ||
                    annotation.name().equalsIgnoreCase(toolName) ||
                    method.getName().equalsIgnoreCase(toolName)) {
                    
                    // 尝试调用
                    if (method.getParameterCount() == 0) {
                        Object result = method.invoke(tool);
                        return result != null ? result.toString() : "";
                    } else if (method.getParameterCount() == 1) {
                        Object result = method.invoke(tool, args != null ? args : "");
                        return result != null ? result.toString() : "";
                    }
                }
            }
        }
        
        // 回退：调用第一个无参方法
        for (Method method : tool.getClass().getMethods()) {
            if (!method.getName().startsWith("get") && 
                !method.getName().startsWith("set") &&
                method.getParameterCount() == 0) {
                Object result = method.invoke(tool);
                return result != null ? result.toString() : "";
            }
        }
        
        return "";
    }
}

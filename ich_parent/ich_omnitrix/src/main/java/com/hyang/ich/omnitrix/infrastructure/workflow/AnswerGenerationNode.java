package com.hyang.ich.omnitrix.infrastructure.workflow;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 答案生成节点
 */
@Slf4j
@Component
public class AnswerGenerationNode {
    
    public String generate(AiWorkflowState state) {
        StringBuilder answer = new StringBuilder();
        
        for (ToolCallRecord call : state.getToolCalls()) {
            answer.append("工具: ").append(call.getToolName()).append("\n");
            answer.append("结果: ").append(call.getResult()).append("\n\n");
        }
        
        if (answer.length() == 0) {
            return "无法生成答案";
        }
        
        return "根据工具调用结果：" + answer.toString();
    }
}

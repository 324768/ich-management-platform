package com.hyang.ich.omnitrix.infrastructure.workflow;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 结果过滤器节点
 * 检查工具结果质量，决定是否生成答案
 * 参考 Kortex ResultFilterNode 设计
 */
@Slf4j
@Component
public class ResultFilterNode {
    
    private static final int MIN_RESULT_LENGTH = 10;
    
    /**
     * 判断是否应该生成答案
     */
    public boolean shouldGenerateAnswer(ToolCallResult result, AiWorkflowState state) {
        if (result == null) return false;
        
        if (!result.isSuccess()) {
            log.debug("工具调用失败，不生成答案: {}", result.getError());
            return false;
        }
        
        String content = result.getContent();
        if (content == null || content.isEmpty()) {
            log.debug("结果为空，不生成答案");
            return false;
        }
        
        if (content.length() < MIN_RESULT_LENGTH) {
            log.debug("结果太短(length={})，继续调用工具", content.length());
            return false;
        }
        
        if (state.isToolCallLooping()) {
            log.warn("检测到工具调用循环，强制生成答案");
            return true;
        }
        
        if (state.hasSufficientData()) {
            log.info("连续成功工具调用 >= 2，数据充足，生成答案");
            return true;
        }
        
        state.incrementConsecutiveSuccess();
        log.debug("工具调用成功但数据可能不足，继续: consecutiveSuccess={}", state.getConsecutiveSuccessfulToolCalls());
        
        return false;
    }
}

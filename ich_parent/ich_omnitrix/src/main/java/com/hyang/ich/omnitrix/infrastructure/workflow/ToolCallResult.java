package com.hyang.ich.omnitrix.infrastructure.workflow;

import lombok.Builder;
import lombok.Data;

/**
 * 工具调用结果
 */
@Data
@Builder
public class ToolCallResult {
    private String toolName;
    private String content;
    private boolean success;
    private String error;
    private long durationMs;
    
    public ToolCallRecord toRecord() {
        return ToolCallRecord.builder()
                .toolName(toolName)
                .result(content)
                .success(success)
                .durationMs(durationMs)
                .build();
    }
}

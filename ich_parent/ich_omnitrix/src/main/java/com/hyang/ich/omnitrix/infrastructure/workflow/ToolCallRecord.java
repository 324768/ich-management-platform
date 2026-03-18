package com.hyang.ich.omnitrix.infrastructure.workflow;

import lombok.Builder;
import lombok.Data;

/**
 * 工具调用记录
 */
@Data
@Builder
public class ToolCallRecord {
    private String toolName;
    private String arguments;
    private String result;
    private long durationMs;
    private boolean success;
}

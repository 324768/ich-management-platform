package com.hyang.ich.omnitrix.infrastructure.workflow;

import lombok.Builder;
import lombok.Data;

/**
 * 决策结果
 */
@Data
@Builder
public class Decision {
    private DecisionAction action;
    private String reasoning;
    private String toolName;
    private String toolArgs;
}

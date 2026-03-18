package com.hyang.ich.omnitrix.infrastructure.workflow;

import lombok.Builder;
import lombok.Data;

/**
 * 反思结果
 */
@Data
@Builder
public class ReflectionResult {
    private String problemAnalysis;
    private boolean isLooping;
    private String newPlan;
}

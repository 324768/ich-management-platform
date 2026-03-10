package com.hyang.ich.omnitrix.entity;

import lombok.Data;
import java.util.Date;

/**
 * 嵌套追踪 Span（支持 Request → Agent → Tool → LLM 多级追踪）。
 * 同一请求的所有 Span 共享相同的 traceId。
 */
@Data
public class AiTraceSpan {

    private Long id;
    /** 关联的 traceId（与 AiTraceLog.traceId 一致） */
    private String traceId;
    /** 本 Span 的唯一 ID */
    private String spanId;
    /** 父 Span ID（根 Span 为 null） */
    private String parentSpanId;
    /** Span 类型：REQUEST / AGENT / TOOL / LLM / REPLAN / BLACKBOARD */
    private String spanType;
    /** Span 名称（如 agent 名、tool 名、model 名） */
    private String spanName;
    /** 耗时（毫秒） */
    private Integer durationMs;
    /** 状态：success / error */
    private String status;
    /** 元数据 JSON（可选，如 token 数、错误信息等） */
    private String metadata;
    private Date createTime;
}

package com.hyang.ich.omnitrix.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class AiTraceLog {

    private Long id;
    private String traceId;
    private Long conversationId;
    private Long userId;
    private String userQuery;
    private String intent;
    private String subAgent;
    private String model;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer latencyMs;
    private BigDecimal selfScore;
    private BigDecimal accuracyScore;
    private BigDecimal completenessScore;
    private BigDecimal safetyScore;
    private String status;
    private String errorMessage;
    private Integer userFeedback;
    /** 本次请求成本（人民币元） */
    private BigDecimal costRmb;
    private Date createTime;
}

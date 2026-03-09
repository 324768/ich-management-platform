package com.hyang.ich.omnitrix.entity;

import lombok.Data;

import java.util.Date;

@Data
public class AiAgentConfig {

    private Long id;
    private String agentCode;
    private String agentName;
    private String icon;
    private String description;
    private String systemPrompt;
    private String routingKeywords;
    private Long knowledgeBaseId;
    private String fallbackStrategy;
    private Integer sort;
    private Integer status;
    private Long version;
    private Date createTime;
    private Date updateTime;
}

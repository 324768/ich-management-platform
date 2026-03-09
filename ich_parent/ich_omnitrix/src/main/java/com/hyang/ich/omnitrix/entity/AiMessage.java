package com.hyang.ich.omnitrix.entity;

import lombok.Data;

import java.util.Date;

@Data
public class AiMessage {

    private Long id;
    private Long conversationId;
    private String sessionId;
    private String role;
    private String content;
    private Integer tokens;
    private String model;
    private String subAgent;
    private Integer latencyMs;
    private Date createTime;
}

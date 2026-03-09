package com.hyang.ich.omnitrix.entity;

import lombok.Data;

import java.util.Date;

@Data
public class AiUserAiConfig {

    private Long id;
    private Long userId;
    private Integer aiEnabled;
    private String disabledReason;
    private Long disabledBy;
    private Integer maxDailyQueries;
    private String blockedAgents;
    private Date createTime;
    private Date updateTime;
}

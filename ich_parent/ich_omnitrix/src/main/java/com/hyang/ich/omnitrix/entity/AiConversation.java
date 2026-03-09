package com.hyang.ich.omnitrix.entity;

import lombok.Data;

import java.util.Date;

@Data
public class AiConversation {

    private Long id;
    private Long userId;
    private String sessionId;
    private String title;
    private Integer messageCount;
    private String summary;
    private Integer status;
    private Date createTime;
    private Date updateTime;
}

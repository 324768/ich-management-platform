package com.hyang.ich.omnitrix.entity;

import lombok.Data;

import java.util.Date;

@Data
public class AiKnowledgeBase {

    private Long id;
    private String question;
    private String answer;
    private Long categoryId;
    private String keywords;
    private Integer hitCount;
    private Integer status;
    private Date createTime;
    private Date updateTime;
}

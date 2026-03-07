package com.hyang.ich.ai.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class AiKnowledgeBaseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String question;
    private String answer;
    private Long categoryId;
    private Integer hitCount;
    private Integer status;
    private Date createTime;
    private Date updateTime;
}

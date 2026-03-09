package com.hyang.ich.omnitrix.entity;

import lombok.Data;

import java.util.Date;

@Data
public class AiPromptConfig {

    private Long id;
    private String promptKey;
    private String promptName;
    private String content;
    private String category;
    private String description;
    private Integer status;
    private Date createTime;
    private Date updateTime;
}

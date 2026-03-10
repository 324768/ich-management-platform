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
    /** 版本号（同一 promptKey 可有多个版本，仅 isActive=1 的版本生效） */
    private Integer version;
    /** 是否为当前激活版本：1=激活, 0=历史 */
    private Integer isActive;
    private Date createTime;
    private Date updateTime;
}

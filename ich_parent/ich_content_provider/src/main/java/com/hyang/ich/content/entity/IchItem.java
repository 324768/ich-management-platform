package com.hyang.ich.content.entity;

import lombok.Data;
import java.util.Date;

@Data
public class IchItem {
    private Long id;
    private Long categoryId;
    private String name;
    private String coverImage;
    private Integer level;
    private String regionCode;
    private String regionName;
    private String declarationUnit;
    private String protectionUnit;
    private Date declarationTime;
    private String description;
    private String content;
    private Integer status;
    private Integer sort;
    private Date createTime;
    private Date updateTime;
    private Integer isDeleted;
}

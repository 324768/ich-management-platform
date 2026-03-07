package com.hyang.ich.content.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class IchItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long categoryId;
    private String categoryName;
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
    private java.util.List<String> detailImages;
    private java.util.List<String> videos;
    private Integer status;
    private Integer sort;
    private Date createTime;
    private Date updateTime;
}

package com.hyang.ich.content.entity;

import lombok.Data;
import java.util.Date;

@Data
public class ProductCategory {
    private Long id;
    private Long parentId;
    private Long ichCategoryId;
    private String name;
    private Integer level;
    private Integer sort;
    private String icon;
    private String description;
    private String detailImages;
    private String videos;
    private Integer status;
    private Date createTime;
    private Date updateTime;
}

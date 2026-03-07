package com.hyang.ich.content.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
public class IchCategoryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long parentId;
    private String name;
    private Integer level;
    private Integer sort;
    private String icon;
    private List<String> detailImages;
    private Integer status;
    private Date createTime;
    private Date updateTime;

    /** 子分类列表（树形结构使用） */
    private List<IchCategoryDTO> children;
}

package com.hyang.ich.product.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
public class ProductCategoryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long parentId;
    private String name;
    private Integer level;
    private Integer sort;
    private String icon;
    private String description;
    private Integer isDeleted;
    private Date createTime;
    private Date updateTime;

    private List<ProductCategoryDTO> children;
}

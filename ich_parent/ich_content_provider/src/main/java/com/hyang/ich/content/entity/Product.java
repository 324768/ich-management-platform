package com.hyang.ich.content.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class Product {
    private Long id;
    private String productSn;
    private String name;
    private String subTitle;
    private Long brandId;
    private Long categoryId;
    private Long heritageManId;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Integer stock;
    private Integer sale;
    private BigDecimal weight;
    private String keywords;
    private String description;
    private String detailDesc;
    private String mainImage;
    private String subImages;
    private String videos;
    private Integer status;
    private Integer sort;
    private Date createTime;
    private Date updateTime;
    private Integer isDeleted;
}

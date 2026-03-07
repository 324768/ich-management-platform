package com.hyang.ich.product.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class ProductDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String productSn;
    private String name;
    private String subTitle;
    private Long brandId;
    private Long categoryId;
    private String categoryName;
    private Long heritageManId;
    private String heritageManName;
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
    private java.util.List<String> videos;
    private Integer status;
    private Integer sort;
    private Date createTime;
    private Date updateTime;
}

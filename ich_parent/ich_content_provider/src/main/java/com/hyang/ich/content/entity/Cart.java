package com.hyang.ich.content.entity;

import lombok.Data;
import java.util.Date;

@Data
public class Cart {
    private Long id;
    private Long userId;
    private Long productId;
    private Long productSkuId;
    private Integer quantity;
    private java.math.BigDecimal price;
    private Integer selected;
    private Integer source;
    private Date createTime;
    private Date updateTime;
    private Integer isDeleted;
}

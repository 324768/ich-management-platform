package com.hyang.ich.user.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class OrderItem {
    private Long id;
    private Long orderId;
    private String orderNo;
    private Long productId;
    private String productPic;
    private String productName;
    private String productBrand;
    private String productSn;
    private BigDecimal productPrice;
    private Integer productQuantity;
    private Long productSkuId;
    private String productSkuCode;
    private Long productCategoryId;
    private Date createTime;
    private Date updateTime;
    private Integer isDeleted;
}

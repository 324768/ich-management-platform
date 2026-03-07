package com.hyang.ich.order.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class OrderItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

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
}

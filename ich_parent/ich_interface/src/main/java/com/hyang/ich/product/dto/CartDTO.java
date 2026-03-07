package com.hyang.ich.product.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class CartDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private Long productId;
    private Integer quantity;
    private Integer checked;
    private Date createTime;
    private Date updateTime;

    /** 冗余展示字段 */
    private String productName;
    private String productImage;
    private BigDecimal productPrice;
    private Integer productStock;
}

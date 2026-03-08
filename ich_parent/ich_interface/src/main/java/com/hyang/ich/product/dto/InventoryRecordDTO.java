package com.hyang.ich.product.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class InventoryRecordDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long productId;
    private String productName;
    private Integer type;
    private Integer quantity;
    private Integer beforeStock;
    private Integer afterStock;
    private String reason;
    private Long operatorId;
    private String operatorName;
    private Date createTime;
    private Date updateTime;
}

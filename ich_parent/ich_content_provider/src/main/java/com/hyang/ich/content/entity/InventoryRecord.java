package com.hyang.ich.content.entity;

import lombok.Data;

import java.util.Date;

@Data
public class InventoryRecord {
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
    private Integer isDeleted;
}

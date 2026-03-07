package com.hyang.ich.order.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
public class OrderDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String orderNo;
    private Long userId;
    private BigDecimal totalAmount;
    private BigDecimal paymentAmount;
    private BigDecimal freightAmount;
    private String paymentType;
    private Date paymentTime;
    private String paymentSerialNumber;
    private String status;
    private String shippingName;
    private String shippingPhone;
    private String shippingAddress;
    private String note;
    private Integer confirmStatus;
    private Date confirmTime;
    private Date createTime;
    private Date updateTime;

    /** 订单商品列表 */
    private List<OrderItemDTO> orderItems;
}

package com.hyang.ich.order.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class OrderCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private Long addressId;
    private String note;

    /** 要下单的商品列表（从购物车选中的） */
    private List<OrderItemCreateDTO> items;

    @Data
    public static class OrderItemCreateDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long productId;
        private Integer quantity;
    }
}

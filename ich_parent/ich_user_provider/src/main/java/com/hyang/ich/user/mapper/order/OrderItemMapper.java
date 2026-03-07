package com.hyang.ich.user.mapper.order;

import com.hyang.ich.user.entity.OrderItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OrderItemMapper {

    int insertBatch(@Param("list") List<OrderItem> items);

    List<OrderItem> selectByOrderId(@Param("orderId") Long orderId);

    List<OrderItem> selectByOrderNo(@Param("orderNo") String orderNo);
}

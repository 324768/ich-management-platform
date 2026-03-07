package com.hyang.ich.user.mapper.order;

import com.hyang.ich.user.entity.Order;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface OrderMapper {

    int insert(Order order);

    Order selectById(@Param("id") Long id);

    Order selectByOrderNo(@Param("orderNo") String orderNo);

    List<Order> selectByUserId(@Param("userId") Long userId, @Param("status") Integer status,
                               @Param("offset") int offset, @Param("limit") int limit);

    int countByUserId(@Param("userId") Long userId, @Param("status") Integer status);

    List<Order> selectByCondition(@Param("orderNo") String orderNo, @Param("status") Integer status,
                                  @Param("offset") int offset, @Param("limit") int limit);

    int countByCondition(@Param("orderNo") String orderNo, @Param("status") Integer status);

    int updateStatus(@Param("orderNo") String orderNo, @Param("status") Integer status);

    int updatePayment(@Param("orderNo") String orderNo, @Param("status") Integer status,
                      @Param("payType") Integer payType);

    int updateShipping(@Param("orderNo") String orderNo, @Param("shippingName") String shippingName,
                       @Param("shippingCode") String shippingCode);

    int updateConfirm(@Param("orderNo") String orderNo);

    long countAll();

    List<Order> selectRecent(@Param("limit") int limit);

    List<Map<String, Object>> countByDay(@Param("startDate") String startDate);
}

package com.hyang.ich.order;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.order.dto.OrderCreateDTO;
import com.hyang.ich.order.dto.OrderDTO;

import java.util.List;
import java.util.Map;

public interface OrderService {

    /** 创建订单 */
    OrderDTO createOrder(OrderCreateDTO createDTO);

    /** 根据订单号查询订单 */
    OrderDTO getOrderByOrderNo(String orderNo);

    /** 根据ID查询订单 */
    OrderDTO getOrderById(Long orderId);

    /** 用户分页查询订单 */
    PageResult<OrderDTO> listUserOrders(Long userId, Integer status, int pageNum, int pageSize);

    /** 管理端分页查询订单 */
    PageResult<OrderDTO> listOrders(String orderNo, Integer status, int pageNum, int pageSize);

    /** 模拟支付 */
    void payOrder(String orderNo, Integer payType);

    /** 取消订单 */
    void cancelOrder(String orderNo, Long userId);

    /** 发货（管理端） */
    void shipOrder(String orderNo);

    /** 确认收货 */
    void confirmReceive(String orderNo, Long userId);

    /** 更新订单状态 */
    void updateOrderStatus(String orderNo, Integer status);

    /** 统计订单总数 */
    long countOrders();

    /** 查询最近订单 */
    List<OrderDTO> listRecentOrders(int limit);

    /** 查询最近7天每天的订单数量，返回Map: 日期字符串 -> 数量 */
    Map<String, Long> getWeeklyOrderCounts();
}

package com.hyang.ich.order;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.order.dto.OrderCreateDTO;
import com.hyang.ich.order.dto.OrderDTO;

public interface OrderService {

    /** 创建订单 */
    OrderDTO createOrder(OrderCreateDTO createDTO);

    /** 根据订单号查询订单 */
    OrderDTO getOrderByOrderNo(String orderNo);

    /** 根据ID查询订单 */
    OrderDTO getOrderById(Long orderId);

    /** 用户分页查询订单 */
    PageResult<OrderDTO> listUserOrders(Long userId, String status, int pageNum, int pageSize);

    /** 管理端分页查询订单 */
    PageResult<OrderDTO> listOrders(String orderNo, String status, int pageNum, int pageSize);

    /** 模拟支付 */
    void payOrder(String orderNo, String paymentType);

    /** 取消订单 */
    void cancelOrder(String orderNo, Long userId);

    /** 发货（管理端） */
    void shipOrder(String orderNo);

    /** 确认收货 */
    void confirmReceive(String orderNo, Long userId);

    /** 更新订单状态 */
    void updateOrderStatus(String orderNo, String status);
}

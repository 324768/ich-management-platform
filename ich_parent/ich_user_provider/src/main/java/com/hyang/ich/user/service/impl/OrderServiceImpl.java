package com.hyang.ich.user.service.impl;

import com.hyang.ich.common.exception.BusinessException;
import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.order.OrderService;
import com.hyang.ich.order.dto.OrderCreateDTO;
import com.hyang.ich.order.dto.OrderDTO;
import com.hyang.ich.order.dto.OrderItemDTO;
import com.hyang.ich.user.entity.Order;
import com.hyang.ich.user.entity.OrderItem;
import com.hyang.ich.user.mapper.order.OrderItemMapper;
import com.hyang.ich.user.mapper.order.OrderMapper;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@DubboService
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    // 状态常量: 0=待付款, 1=已付款, 2=已发货, 3=已完成, 4=已取消
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_PAID = 1;
    private static final int STATUS_SHIPPED = 2;
    private static final int STATUS_COMPLETED = 3;
    private static final int STATUS_CANCELLED = 4;

    @Override
    public OrderDTO createOrder(OrderCreateDTO createDTO) {
        String orderNo = generateOrderNo();

        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(createDTO.getUserId());
        order.setStatus(STATUS_PENDING);
        order.setFreightAmount(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setNote(createDTO.getNote());

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> items = new ArrayList<>();

        if (createDTO.getItems() != null) {
            for (OrderCreateDTO.OrderItemCreateDTO itemDTO : createDTO.getItems()) {
                OrderItem item = new OrderItem();
                item.setOrderNo(orderNo);
                item.setProductId(itemDTO.getProductId());
                item.setProductQuantity(itemDTO.getQuantity());
                item.setProductName("商品" + itemDTO.getProductId());
                item.setProductPrice(BigDecimal.ZERO);
                items.add(item);
            }
        }

        order.setTotalAmount(totalAmount);
        order.setPayAmount(totalAmount);
        orderMapper.insert(order);

        for (OrderItem item : items) {
            item.setOrderId(order.getId());
        }
        if (!items.isEmpty()) {
            orderItemMapper.insertBatch(items);
        }

        return getOrderByOrderNo(orderNo);
    }

    @Override
    public OrderDTO getOrderByOrderNo(String orderNo) {
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(500, "订单不存在");
        }
        return toOrderDTO(order);
    }

    @Override
    public OrderDTO getOrderById(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(500, "订单不存在");
        }
        return toOrderDTO(order);
    }

    @Override
    public PageResult<OrderDTO> listUserOrders(Long userId, Integer status, int pageNum, int pageSize) {
        int offset = (pageNum - 1) * pageSize;
        List<Order> orders = orderMapper.selectByUserId(userId, status, offset, pageSize);
        int total = orderMapper.countByUserId(userId, status);
        List<OrderDTO> dtoList = orders.stream().map(this::toOrderDTO).collect(Collectors.toList());
        return new PageResult<>(pageNum, pageSize, (long) total, dtoList);
    }

    @Override
    public PageResult<OrderDTO> listOrders(String orderNo, Integer status, int pageNum, int pageSize) {
        int offset = (pageNum - 1) * pageSize;
        List<Order> orders = orderMapper.selectByCondition(orderNo, status, offset, pageSize);
        int total = orderMapper.countByCondition(orderNo, status);
        List<OrderDTO> dtoList = orders.stream().map(this::toOrderDTO).collect(Collectors.toList());
        return new PageResult<>(pageNum, pageSize, (long) total, dtoList);
    }

    @Override
    public void payOrder(String orderNo, Integer payType) {
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) throw new BusinessException(500, "订单不存在");
        if (!Integer.valueOf(STATUS_PENDING).equals(order.getStatus())) throw new BusinessException(500, "订单状态不正确");
        orderMapper.updatePayment(orderNo, STATUS_PAID, payType != null ? payType : 1);
    }

    @Override
    public void cancelOrder(String orderNo, Long userId) {
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) throw new BusinessException(500, "订单不存在");
        if (!Integer.valueOf(STATUS_PENDING).equals(order.getStatus())) throw new BusinessException(500, "只能取消待支付订单");
        orderMapper.updateStatus(orderNo, STATUS_CANCELLED);
    }

    @Override
    public void shipOrder(String orderNo) {
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) throw new BusinessException(500, "订单不存在");
        if (!Integer.valueOf(STATUS_PAID).equals(order.getStatus())) throw new BusinessException(500, "只能对已支付订单发货");
        orderMapper.updateShipping(orderNo, null, null);
    }

    @Override
    public void confirmReceive(String orderNo, Long userId) {
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) throw new BusinessException(500, "订单不存在");
        if (!Integer.valueOf(STATUS_SHIPPED).equals(order.getStatus())) throw new BusinessException(500, "只能确认已发货订单");
        orderMapper.updateConfirm(orderNo);
    }

    @Override
    public void updateOrderStatus(String orderNo, Integer status) {
        orderMapper.updateStatus(orderNo, status);
    }

    @Override
    public long countOrders() {
        return orderMapper.countAll();
    }

    @Override
    public List<OrderDTO> listRecentOrders(int limit) {
        List<Order> orders = orderMapper.selectRecent(limit);
        return orders.stream().map(this::toOrderDTO).collect(Collectors.toList());
    }

    @Override
    public Map<String, Long> getWeeklyOrderCounts() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -6);
        String startDate = new SimpleDateFormat("yyyy-MM-dd").format(cal.getTime());

        List<Map<String, Object>> rows = orderMapper.countByDay(startDate);
        Map<String, Long> result = new LinkedHashMap<>();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Calendar cur = Calendar.getInstance();
        cur.add(Calendar.DAY_OF_MONTH, -6);
        for (int i = 0; i < 7; i++) {
            result.put(sdf.format(cur.getTime()), 0L);
            cur.add(Calendar.DAY_OF_MONTH, 1);
        }

        for (Map<String, Object> row : rows) {
            String day = String.valueOf(row.get("day"));
            Long cnt = ((Number) row.get("cnt")).longValue();
            result.put(day, cnt);
        }
        return result;
    }

    private String generateOrderNo() {
        return "ORD" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    private OrderDTO toOrderDTO(Order order) {
        OrderDTO dto = new OrderDTO();
        BeanUtils.copyProperties(order, dto);
        List<OrderItem> items = orderItemMapper.selectByOrderNo(order.getOrderNo());
        if (items != null) {
            dto.setOrderItems(items.stream().map(this::toOrderItemDTO).collect(Collectors.toList()));
        }
        return dto;
    }

    private OrderItemDTO toOrderItemDTO(OrderItem item) {
        OrderItemDTO dto = new OrderItemDTO();
        BeanUtils.copyProperties(item, dto);
        return dto;
    }
}

package com.hyang.ich.user.task;

import com.hyang.ich.user.entity.Order;
import com.hyang.ich.user.mapper.order.OrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderAutoCloseTask {

    private static final Logger log = LoggerFactory.getLogger(OrderAutoCloseTask.class);

    private static final int EXPIRE_MINUTES = 30;

    @Autowired
    private OrderMapper orderMapper;

    @Scheduled(fixedRate = 60000)
    public void autoCancelExpiredOrders() {
        List<Order> expiredOrders = orderMapper.selectExpiredUnpaid(EXPIRE_MINUTES);
        if (expiredOrders == null || expiredOrders.isEmpty()) {
            return;
        }

        int cancelled = orderMapper.batchCancelExpired(EXPIRE_MINUTES);
        if (cancelled > 0) {
            log.info("[订单自动关闭] 已取消 {} 笔超过{}分钟未支付的订单", cancelled, EXPIRE_MINUTES);
            for (Order order : expiredOrders) {
                log.info("[订单自动关闭] 订单号: {}, 用户ID: {}, 金额: {}", order.getOrderNo(), order.getUserId(), order.getTotalAmount());
            }
        }
    }
}

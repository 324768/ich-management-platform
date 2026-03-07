package com.hyang.ich.adminweb.controller;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.common.vo.Result;
import com.hyang.ich.order.OrderService;
import com.hyang.ich.order.dto.OrderDTO;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/order")
public class AdminOrderController {

    @DubboReference(check = false)
    private OrderService orderService;

    @GetMapping("/list")
    public Result<PageResult<OrderDTO>> listOrders(@RequestParam(defaultValue = "1") int pageNum,
                                                    @RequestParam(defaultValue = "10") int pageSize,
                                                    @RequestParam(required = false) String orderNo,
                                                    @RequestParam(required = false) Integer status) {
        return Result.success(orderService.listOrders(orderNo, status, pageNum, pageSize));
    }

    @GetMapping("/{orderNo}")
    public Result<OrderDTO> getOrder(@PathVariable String orderNo) {
        return Result.success(orderService.getOrderByOrderNo(orderNo));
    }

    @PutMapping("/ship")
    public Result<Void> shipOrder(@RequestParam String orderNo) {
        orderService.shipOrder(orderNo);
        return Result.success();
    }

    @PutMapping("/status")
    public Result<Void> updateStatus(@RequestParam String orderNo, @RequestParam Integer status) {
        orderService.updateOrderStatus(orderNo, status);
        return Result.success();
    }
}

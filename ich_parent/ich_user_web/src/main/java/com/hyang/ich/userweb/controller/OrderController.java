package com.hyang.ich.userweb.controller;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.common.vo.Result;
import com.hyang.ich.order.OrderService;
import com.hyang.ich.order.dto.OrderCreateDTO;
import com.hyang.ich.order.dto.OrderDTO;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    @DubboReference(check = false)
    private OrderService orderService;

    @PostMapping("/create")
    public Result<OrderDTO> createOrder(@RequestBody OrderCreateDTO createDTO) {
        return Result.success(orderService.createOrder(createDTO));
    }

    @GetMapping("/{orderNo}")
    public Result<OrderDTO> getOrder(@PathVariable String orderNo) {
        return Result.success(orderService.getOrderByOrderNo(orderNo));
    }

    @GetMapping("/list")
    public Result<PageResult<OrderDTO>> listOrders(@RequestParam Long userId,
                                                    @RequestParam(required = false) Integer status,
                                                    @RequestParam(defaultValue = "1") int pageNum,
                                                    @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(orderService.listUserOrders(userId, status, pageNum, pageSize));
    }

    @PostMapping("/pay")
    public Result<Void> payOrder(@RequestParam String orderNo,
                                  @RequestParam(defaultValue = "1") Integer payType) {
        orderService.payOrder(orderNo, payType);
        return Result.success();
    }

    @PostMapping("/cancel")
    public Result<Void> cancelOrder(@RequestParam String orderNo, @RequestParam Long userId) {
        orderService.cancelOrder(orderNo, userId);
        return Result.success();
    }

    @PostMapping("/confirm")
    public Result<Void> confirmReceive(@RequestParam String orderNo, @RequestParam Long userId) {
        orderService.confirmReceive(orderNo, userId);
        return Result.success();
    }
}

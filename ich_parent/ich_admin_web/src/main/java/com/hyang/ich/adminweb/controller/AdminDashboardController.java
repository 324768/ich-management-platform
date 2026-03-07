package com.hyang.ich.adminweb.controller;

import com.hyang.ich.common.vo.Result;
import com.hyang.ich.content.ContentService;
import com.hyang.ich.order.OrderService;
import com.hyang.ich.order.dto.OrderDTO;
import com.hyang.ich.product.ProductService;
import com.hyang.ich.user.UserService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    @DubboReference(check = false)
    private ContentService contentService;

    @DubboReference(check = false)
    private UserService userService;

    @DubboReference(check = false)
    private ProductService productService;

    @DubboReference(check = false)
    private OrderService orderService;

    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("heritageItems", contentService.countItems());
        stats.put("totalUsers", userService.countUsers());
        stats.put("products", productService.countProducts());
        stats.put("orders", orderService.countOrders());
        stats.put("lowStock", productService.countLowStockProducts(10));
        return Result.success(stats);
    }

    @GetMapping("/recent-orders")
    public Result<List<OrderDTO>> getRecentOrders() {
        return Result.success(orderService.listRecentOrders(5));
    }

    @GetMapping("/weekly-trend")
    public Result<Map<String, Long>> getWeeklyTrend() {
        return Result.success(orderService.getWeeklyOrderCounts());
    }
}

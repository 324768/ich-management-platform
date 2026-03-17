package com.hyang.ich.omnitrix.config;

import com.hyang.ich.common.BrowseHistoryService;
import com.hyang.ich.content.ContentService;
import com.hyang.ich.order.OrderService;
import com.hyang.ich.product.ProductService;
import com.hyang.ich.system.SystemService;
import com.hyang.ich.user.UserService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DubboConsumerConfig {

    @DubboReference(check = false, timeout = 5000, retries = 1)
    private ContentService contentService;

    @DubboReference(check = false, timeout = 5000, retries = 1)
    private ProductService productService;

    @DubboReference(check = false, timeout = 5000, retries = 1)
    private UserService userService;

    @DubboReference(check = false, timeout = 5000, retries = 1)
    private OrderService orderService;

    @DubboReference(check = false, timeout = 5000, retries = 1)
    private SystemService systemService;

    @DubboReference(check = false, timeout = 5000, retries = 1)
    private BrowseHistoryService browseHistoryService;
}

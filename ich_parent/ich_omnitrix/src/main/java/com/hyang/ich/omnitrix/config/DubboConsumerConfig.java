package com.hyang.ich.omnitrix.config;

import com.hyang.ich.common.BrowseHistoryService;
import com.hyang.ich.content.ContentService;
import com.hyang.ich.order.OrderService;
import com.hyang.ich.product.ProductService;
import com.hyang.ich.system.SystemService;
import com.hyang.ich.user.UserService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Bean;
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

    @Bean
    public ContentService contentService() {
        return contentService;
    }

    @Bean
    public ProductService productService() {
        return productService;
    }

    @Bean
    public UserService userService() {
        return userService;
    }

    @Bean
    public OrderService orderService() {
        return orderService;
    }

    @Bean
    public SystemService systemService() {
        return systemService;
    }

    @Bean
    public BrowseHistoryService browseHistoryService() {
        return browseHistoryService;
    }
}

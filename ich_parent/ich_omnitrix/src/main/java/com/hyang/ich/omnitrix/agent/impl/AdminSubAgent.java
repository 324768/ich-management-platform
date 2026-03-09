package com.hyang.ich.omnitrix.agent.impl;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.content.ContentService;
import com.hyang.ich.content.dto.IchActivityDTO;
import com.hyang.ich.order.OrderService;
import com.hyang.ich.order.dto.OrderDTO;
import com.hyang.ich.product.ProductService;
import com.hyang.ich.product.dto.ProductDTO;
import com.hyang.ich.user.UserService;
import com.hyang.ich.omnitrix.agent.AgentContext;
import com.hyang.ich.omnitrix.agent.AgentUtils;
import com.hyang.ich.omnitrix.agent.SubAgent;
import com.hyang.ich.omnitrix.agent.tool.AgentTool;
import com.hyang.ich.omnitrix.agent.tool.ToolCallResult;
import com.hyang.ich.omnitrix.agent.tool.ToolSelector;
import com.hyang.ich.omnitrix.dto.AgentQueryResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 管理员数据分析代理 —— 全平台数据统计、趋势分析、预警监控
 */
@Slf4j
@Component
public class AdminSubAgent implements SubAgent {

    private final ContentService contentService;
    private final ProductService productService;
    private final OrderService orderService;
    private final UserService userService;
    private final ToolSelector toolSelector;

    private static final List<AgentTool> TOOLS = Arrays.asList(
            AgentTool.of("query_overview", "查询平台综合统计数据、运营概况、总体数据、总览", "无"),
            AgentTool.of("query_order_trend", "查询近7天订单趋势、订单走势、销售趋势", "无"),
            AgentTool.of("query_low_stock", "查询低库存商品、库存预警、缺货商品", "无"),
            AgentTool.of("query_today_revenue", "查询今日收入、今天营收、销售额、最近收入", "无"),
            AgentTool.of("query_pending_approvals", "查询待审批活动、需要审核的活动、待处理审批", "无"),
            AgentTool.of("query_pending_orders", "查询待发货订单、需要发货的订单、待处理订单", "无"),
            AgentTool.of("query_user_stats", "查询用户统计、用户增长、注册用户数", "无"),
            AgentTool.of("query_content_stats", "查询内容统计、非遗项目数、活动数、动态数", "无")
    );

    public AdminSubAgent(ContentService contentService, ProductService productService,
                         OrderService orderService, UserService userService,
                         ToolSelector toolSelector) {
        this.contentService = contentService;
        this.productService = productService;
        this.orderService = orderService;
        this.userService = userService;
        this.toolSelector = toolSelector;
    }

    @Override
    public String getCode() {
        return "admin_data_agent";
    }

    @Override
    public String getName() {
        return "数据分析助手";
    }

    @Override
    public String getDescription() {
        return "平台数据统计、趋势分析、预警监控（管理员专用）";
    }

    @Override
    public String getAgentPrompt() {
        return "## 当前任务模式: 管理员数据分析助手\n" +
                "你现在是管理员的数据分析助手，回答平台运营统计、数据分析问题。\n" +
                "- 数字必须精确引用查询结果，不要近似或挥发\n" +
                "- 可以给出运营建议，但需注明\"建议\"\n" +
                "- 对于待处理事项，主动提醒管理员并给出优先级建议\n" +
                "- 回答要简洁明了，用列表和数字突出重点";
    }

    @Override
    public AgentQueryResult execute(String userQuery, AgentContext context) {
        try {
            ToolCallResult toolCall = toolSelector.select(userQuery, TOOLS);
            log.debug("AdminDataAgent 工具选择: tool={}", toolCall.getToolName());

            StringBuilder data = new StringBuilder();

            switch (toolCall.isNone() ? fallbackTool(userQuery) : toolCall.getToolName()) {
                case "query_low_stock":
                    data.append(queryLowStock());
                    break;
                case "query_order_trend":
                    data.append(queryOrderTrend());
                    break;
                case "query_today_revenue":
                    data.append(queryTodayRevenue());
                    break;
                case "query_pending_approvals":
                    data.append(queryPendingApprovals());
                    break;
                case "query_pending_orders":
                    data.append(queryPendingOrders());
                    break;
                case "query_user_stats":
                    data.append(queryUserStats());
                    break;
                case "query_content_stats":
                    data.append(queryContentStats());
                    break;
                case "query_overview":
                default:
                    data.append(queryOverview());
                    break;
            }

            return AgentQueryResult.success(data.toString(), getCode());

        } catch (Exception e) {
            log.error("AdminDataAgent 执行异常: {}", e.getMessage(), e);
            return AgentQueryResult.error(getCode(), e.getMessage());
        }
    }

    private String fallbackTool(String query) {
        String q = query.toLowerCase();
        if (q.contains("低库存") || q.contains("库存预警") || q.contains("缺货")) return "query_low_stock";
        if (q.contains("趋势") || q.contains("近7天") || q.contains("走势")) return "query_order_trend";
        if (q.contains("收入") || q.contains("营收") || q.contains("销售额")) return "query_today_revenue";
        if (q.contains("审批") || q.contains("审核") || q.contains("待审")) return "query_pending_approvals";
        if (q.contains("发货") || q.contains("待发") || q.contains("待处理订单")) return "query_pending_orders";
        if (q.contains("用户") || q.contains("增长") || q.contains("注册")) return "query_user_stats";
        if (q.contains("内容") || q.contains("项目数") || q.contains("活动数") || q.contains("动态数")) return "query_content_stats";
        return "query_overview";
    }

    // ========== 数据查询工具 ==========

    private String queryLowStock() {
        List<ProductDTO> lowStock = productService.listLowStockProducts(10);
        StringBuilder sb = new StringBuilder("低库存商品(库存<10):\n");
        if (lowStock != null && !lowStock.isEmpty()) {
            for (ProductDTO p : lowStock) {
                sb.append("- ").append(p.getName()).append(", 库存: ").append(p.getStock());
                if (p.getPrice() != null) sb.append(", 卖价: ¥").append(p.getPrice());
                sb.append("\n");
            }
            sb.append("\n共 ").append(lowStock.size()).append(" 个商品库存偏低，建议及时补货");
        } else {
            sb.append("暂无低库存商品，库存状况良好\n");
        }
        return sb.toString();
    }

    private String queryOrderTrend() {
        Map<String, Long> weeklyOrders = orderService.getWeeklyOrderCounts();
        StringBuilder sb = new StringBuilder("近7天订单趋势:\n");
        if (weeklyOrders != null && !weeklyOrders.isEmpty()) {
            long total = 0;
            for (Map.Entry<String, Long> entry : weeklyOrders.entrySet()) {
                sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("笔\n");
                total += entry.getValue();
            }
            sb.append("\n7天总计: ").append(total).append("笔，日均: ")
                    .append(String.format("%.1f", total / 7.0)).append("笔");
        } else {
            sb.append("暂无订单数据\n");
        }
        return sb.toString();
    }

    private String queryTodayRevenue() {
        List<OrderDTO> recentOrders = orderService.listRecentOrders(50);
        BigDecimal todayRevenue = BigDecimal.ZERO;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        int todayCount = 0;
        int paidCount = 0;
        java.time.LocalDate today = java.time.LocalDate.now();

        if (recentOrders != null) {
            for (OrderDTO o : recentOrders) {
                if (o.getPayAmount() != null && o.getStatus() != null && o.getStatus() >= 1) {
                    totalRevenue = totalRevenue.add(o.getPayAmount());
                    paidCount++;
                    if (o.getCreateTime() != null) {
                        java.time.LocalDate orderDate = o.getCreateTime().toInstant()
                                .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                        if (orderDate.equals(today)) {
                            todayRevenue = todayRevenue.add(o.getPayAmount());
                            todayCount++;
                        }
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder("收入统计:\n");
        sb.append("- 今日收入: ¥").append(todayRevenue).append(" (").append(todayCount).append("笔订单)\n");
        sb.append("- 近期总收入: ¥").append(totalRevenue).append(" (").append(paidCount).append("笔已付款订单)\n");
        if (paidCount > 0) {
            sb.append("- 客单价: ¥").append(totalRevenue.divide(BigDecimal.valueOf(paidCount), 2, RoundingMode.HALF_UP)).append("\n");
        }
        return sb.toString();
    }

    private String queryPendingApprovals() {
        try {
            PageResult<IchActivityDTO> pending = contentService.listActivitiesByApproval(1, 10, null, 0);
            StringBuilder sb = new StringBuilder("待审批活动:\n");
            if (pending != null && pending.getList() != null && !pending.getList().isEmpty()) {
                for (IchActivityDTO a : pending.getList()) {
                    sb.append("- ID:").append(a.getId()).append(" ").append(a.getName());
                    if (a.getOrganizer() != null) sb.append(", 主办: ").append(a.getOrganizer());
                    if (a.getStartTime() != null) sb.append(", 开始: ").append(a.getStartTime());
                    sb.append("\n");
                }
                sb.append("\n共 ").append(pending.getTotal()).append(" 个活动待审批");
            } else {
                sb.append("暂无待审批活动\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.debug("查询待审批活动异常: {}", e.getMessage());
            return "查询待审批活动失败\n";
        }
    }

    private String queryPendingOrders() {
        try {
            PageResult<OrderDTO> pending = orderService.listOrders(null, 1, 1, 10);
            StringBuilder sb = new StringBuilder("待发货订单(已付款):\n");
            if (pending != null && pending.getList() != null && !pending.getList().isEmpty()) {
                for (OrderDTO o : pending.getList()) {
                    sb.append("- 订单号: ").append(o.getOrderNo());
                    sb.append(", 金额: ¥").append(o.getPayAmount() != null ? o.getPayAmount() : "0");
                    if (o.getReceiverName() != null) sb.append(", 收货人: ").append(o.getReceiverName());
                    sb.append("\n");
                }
                sb.append("\n共 ").append(pending.getTotal()).append(" 个订单待发货");
            } else {
                sb.append("暂无待发货订单\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.debug("查询待发货订单异常: {}", e.getMessage());
            return "查询待发货订单失败\n";
        }
    }

    private String queryUserStats() {
        long userCount = userService.countUsers();
        StringBuilder sb = new StringBuilder("用户统计:\n");
        sb.append("- 注册用户总数: ").append(userCount).append(" 人\n");
        return sb.toString();
    }

    private String queryContentStats() {
        long itemCount = contentService.countItems();

        // 查询活动统计
        long activeActivityCount = 0;
        try {
            PageResult<IchActivityDTO> activities = contentService.listActivities(1, 1, null, 1, null);
            if (activities != null) activeActivityCount = activities.getTotal();
        } catch (Exception ignored) {}

        // 查询动态统计
        long postCount = 0;
        try {
            PageResult<com.hyang.ich.content.dto.IchPostDTO> posts = contentService.listPosts(1, 1, null, null);
            if (posts != null) postCount = posts.getTotal();
        } catch (Exception ignored) {}

        StringBuilder sb = new StringBuilder("内容统计:\n");
        sb.append("- 非遗项目: ").append(itemCount).append(" 个\n");
        sb.append("- 进行中活动: ").append(activeActivityCount).append(" 个\n");
        sb.append("- 用户动态: ").append(postCount).append(" 篇\n");
        return sb.toString();
    }

    private String queryOverview() {
        long itemCount = contentService.countItems();
        long userCount = userService.countUsers();
        long productCount = productService.countProducts();
        long orderCount = orderService.countOrders();
        long lowStockCount = productService.countLowStockProducts(10);

        StringBuilder sb = new StringBuilder("平台运营数据统计:\n");
        sb.append("- 非遗项目: ").append(itemCount).append(" 个\n");
        sb.append("- 注册用户: ").append(userCount).append(" 人\n");
        sb.append("- 文创商品: ").append(productCount).append(" 个\n");
        sb.append("- 总订单数: ").append(orderCount).append(" 笔\n");
        sb.append("- 低库存预警: ").append(lowStockCount).append(" 个\n");

        // 待审批活动数
        try {
            PageResult<IchActivityDTO> pending = contentService.listActivitiesByApproval(1, 1, null, 0);
            if (pending != null) {
                sb.append("- 待审批活动: ").append(pending.getTotal()).append(" 个\n");
            }
        } catch (Exception ignored) {}

        // 待发货订单数
        try {
            PageResult<OrderDTO> pendingShip = orderService.listOrders(null, 1, 1, 1);
            if (pendingShip != null) {
                sb.append("- 待发货订单: ").append(pendingShip.getTotal()).append(" 个\n");
            }
        } catch (Exception ignored) {}

        List<OrderDTO> recentOrders = orderService.listRecentOrders(5);
        if (recentOrders != null && !recentOrders.isEmpty()) {
            sb.append("\n最近订单:\n");
            for (OrderDTO order : recentOrders) {
                sb.append("- ").append(order.getOrderNo())
                        .append(", ¥").append(order.getPayAmount() != null ? order.getPayAmount() : "0")
                        .append(", ").append(AgentUtils.formatOrderStatus(order.getStatus())).append("\n");
            }
        }
        return sb.toString();
    }
}

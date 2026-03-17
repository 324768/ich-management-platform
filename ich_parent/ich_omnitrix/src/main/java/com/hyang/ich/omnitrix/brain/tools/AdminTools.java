package com.hyang.ich.omnitrix.brain.tools;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.content.ContentService;
import com.hyang.ich.content.dto.IchActivityDTO;
import com.hyang.ich.omnitrix.agent.AgentUtils;
import com.hyang.ich.omnitrix.brain.AiRequestContext;
import com.hyang.ich.omnitrix.dto.PendingAction;
import com.hyang.ich.omnitrix.orchestrator.ActionExecutor;
import com.hyang.ich.order.OrderService;
import com.hyang.ich.order.dto.OrderDTO;
import com.hyang.ich.product.ProductService;
import com.hyang.ich.product.dto.ProductDTO;
import com.hyang.ich.system.SystemService;
import com.hyang.ich.system.dto.SysNotificationDTO;
import com.hyang.ich.user.UserService;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * 管理员工具集 — 替代原 AdminSubAgent + AdminActionAgent。
 * 包含数据查询（只读）和管理操作（写，需确认）两部分。
 */
@Slf4j
@Component
public class AdminTools {

    private final ContentService contentService;
    private final ProductService productService;
    private final OrderService orderService;
    private final UserService userService;
    private final SystemService systemService;
    private final ActionExecutor actionExecutor;

    public AdminTools(ContentService contentService, ProductService productService,
                      OrderService orderService, UserService userService,
                      SystemService systemService, ActionExecutor actionExecutor) {
        this.contentService = contentService;
        this.productService = productService;
        this.orderService = orderService;
        this.userService = userService;
        this.systemService = systemService;
        this.actionExecutor = actionExecutor;
    }

    // ==================== 只读数据查询 ====================

    @Tool("查询平台综合运营数据概览：用户数、商品数、订单数、非遗项目数、低库存预警等")
    public String queryOverview() {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        long itemCount = contentService.countItems();
        long userCount = userService.countUsers();
        long productCount = productService.countProducts();
        long orderCount = orderService.countOrders();
        long lowStockCount = productService.countLowStockProducts(10);

        StringBuilder sb = new StringBuilder();
        sb.append("- 非遗项目: ").append(itemCount).append(" 个\n");
        sb.append("- 注册用户: ").append(userCount).append(" 人\n");
        sb.append("- 文创商品: ").append(productCount).append(" 个\n");
        sb.append("- 总订单数: ").append(orderCount).append(" 笔\n");
        sb.append("- 低库存预警: ").append(lowStockCount).append(" 个\n");
        try {
            PageResult<IchActivityDTO> pending = contentService.listActivitiesByApproval(1, 1, null, 0);
            if (pending != null) sb.append("- 待审批活动: ").append(pending.getTotal()).append(" 个\n");
        } catch (Exception ignored) {}
        try {
            PageResult<OrderDTO> pendingShip = orderService.listOrders(null, 1, 1, 1);
            if (pendingShip != null) sb.append("- 待发货订单: ").append(pendingShip.getTotal()).append(" 个\n");
        } catch (Exception ignored) {}
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.success("已获取平台运营数据概览", sb.toString())
                .withSearchSuccessHint("运营数据").toXml();
    }

    @Tool("查询近7天订单趋势、订单走势")
    public String queryOrderTrend() {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        Map<String, Long> weeklyOrders = orderService.getWeeklyOrderCounts();
        if (weeklyOrders == null || weeklyOrders.isEmpty()) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("暂无订单数据").withSearchEmptyHint("订单趋势").toXml();
        }
        StringBuilder sb = new StringBuilder();
        long total = 0;
        for (Map.Entry<String, Long> entry : weeklyOrders.entrySet()) {
            sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("笔\n");
            total += entry.getValue();
        }
        sb.append("\n7天总计: ").append(total).append("笔，日均: ")
                .append(String.format("%.1f", total / 7.0)).append("笔");
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.success("已获取近7天订单趋势", sb.toString())
                .withSearchSuccessHint("订单趋势").toXml();
    }

    @Tool("查询低库存商品、库存预警")
    public String queryLowStock() {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        List<ProductDTO> lowStock = productService.listLowStockProducts(10);
        if (lowStock == null || lowStock.isEmpty()) {
            AiRequestContext.recordToolSuccess();
            return ToolResultWrapper.success("暂无低库存商品，库存状况良好", null)
                    .withSearchSuccessHint("库存").toXml();
        }
        StringBuilder sb = new StringBuilder();
        for (ProductDTO p : lowStock) {
            sb.append("- ").append(p.getName()).append(", 库存: ").append(p.getStock());
            if (p.getPrice() != null) sb.append(", ¥").append(p.getPrice());
            sb.append("\n");
        }
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.success(lowStock.size() + "个商品库存偏低", sb.toString())
                .withSearchSuccessHint("低库存商品").toXml();
    }

    @Tool("查询今日收入和近期营收统计")
    public String queryTodayRevenue() {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        List<OrderDTO> recentOrders = orderService.listRecentOrders(50);
        BigDecimal todayRevenue = BigDecimal.ZERO, totalRevenue = BigDecimal.ZERO;
        int todayCount = 0, paidCount = 0;
        java.time.LocalDate today = java.time.LocalDate.now();
        if (recentOrders != null) {
            for (OrderDTO o : recentOrders) {
                if (o.getPayAmount() != null && o.getStatus() != null && o.getStatus() >= 1) {
                    totalRevenue = totalRevenue.add(o.getPayAmount());
                    paidCount++;
                    if (o.getCreateTime() != null) {
                        java.time.LocalDate d = o.getCreateTime().toInstant()
                                .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                        if (d.equals(today)) { todayRevenue = todayRevenue.add(o.getPayAmount()); todayCount++; }
                    }
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("- 今日收入: ¥").append(todayRevenue).append(" (").append(todayCount).append("笔)\n");
        sb.append("- 近期总收入: ¥").append(totalRevenue).append(" (").append(paidCount).append("笔)\n");
        if (paidCount > 0)
            sb.append("- 客单价: ¥").append(totalRevenue.divide(BigDecimal.valueOf(paidCount), 2, RoundingMode.HALF_UP));
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.success("已获取收入统计", sb.toString())
                .withSearchSuccessHint("营收数据").toXml();
    }

    @Tool("查询待审批活动列表")
    public String queryPendingApprovals() {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        try {
            PageResult<IchActivityDTO> pending = contentService.listActivitiesByApproval(1, 10, null, 0);
            if (pending == null || pending.getList() == null || pending.getList().isEmpty()) {
                AiRequestContext.recordToolFailure();
                return ToolResultWrapper.empty("暂无待审批活动").withSearchEmptyHint("待审批活动").toXml();
            }
            StringBuilder sb = new StringBuilder();
            for (IchActivityDTO a : pending.getList()) {
                sb.append("- ID:").append(a.getId()).append(" ").append(a.getName());
                if (a.getOrganizer() != null) sb.append(", 主办: ").append(a.getOrganizer());
                sb.append("\n");
            }
            AiRequestContext.recordToolSuccess();
            return ToolResultWrapper.success("共" + pending.getTotal() + "个待审批活动", sb.toString())
                    .withSearchSuccessHint("待审批活动").toXml();
        } catch (Exception e) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.error("查询待审批活动失败").withErrorHint().toXml();
        }
    }

    @Tool("查询待发货订单列表")
    public String queryPendingOrders() {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        try {
            PageResult<OrderDTO> pending = orderService.listOrders(null, 1, 1, 10);
            if (pending == null || pending.getList() == null || pending.getList().isEmpty()) {
                AiRequestContext.recordToolFailure();
                return ToolResultWrapper.empty("暂无待发货订单").withSearchEmptyHint("待发货订单").toXml();
            }
            StringBuilder sb = new StringBuilder();
            for (OrderDTO o : pending.getList()) {
                sb.append("- ").append(o.getOrderNo()).append(", ¥").append(o.getPayAmount() != null ? o.getPayAmount() : "0");
                if (o.getReceiverName() != null) sb.append(", 收货人: ").append(o.getReceiverName());
                sb.append("\n");
            }
            AiRequestContext.recordToolSuccess();
            return ToolResultWrapper.success("共" + pending.getTotal() + "个待发货订单", sb.toString())
                    .withSearchSuccessHint("待发货订单").toXml();
        } catch (Exception e) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.error("查询待发货订单失败").withErrorHint().toXml();
        }
    }

    @Tool("查询用户统计数据（注册用户数等）")
    public String queryUserStats() {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        long userCount = userService.countUsers();
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.success("已获取用户统计", "- 注册用户总数: " + userCount + " 人")
                .withSearchSuccessHint("用户统计").toXml();
    }

    @Tool("查询内容统计（非遗项目数、活动数、动态数）")
    public String queryContentStats() {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        long itemCount = contentService.countItems();
        long activeActivityCount = 0, postCount = 0;
        try {
            PageResult<IchActivityDTO> a = contentService.listActivities(1, 1, null, 1, null);
            if (a != null) activeActivityCount = a.getTotal();
        } catch (Exception ignored) {}
        try {
            PageResult<com.hyang.ich.content.dto.IchPostDTO> p = contentService.listPosts(1, 1, null, null);
            if (p != null) postCount = p.getTotal();
        } catch (Exception ignored) {}
        String data = "- 非遗项目: " + itemCount + " 个\n- 进行中活动: " + activeActivityCount +
                " 个\n- 用户动态: " + postCount + " 篇";
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.success("已获取内容统计", data).withSearchSuccessHint("内容统计").toXml();
    }

    // ==================== 管理写操作 ====================

    @Tool("审批通过活动。参数: 活动ID或活动名称关键词")
    public String approveActivity(String param) {
        IchActivityDTO activity = findActivityByParam(param);
        if (activity == null) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty(listPendingActivitiesHint("审批通过"))
                    .withSearchEmptyHint("活动").toXml();
        }
        PendingAction action = PendingAction.of("approve_activity",
                "审批通过活动「" + activity.getName() + "」(ID:" + activity.getId() + ")")
                .param("activityId", String.valueOf(activity.getId()));
        actionExecutor.savePendingAction(AiRequestContext.getSessionId(), action);
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.actionProposed("已提交审批通过「" + activity.getName() + "」的确认请求", null)
                .withActionProposedHint("审批通过").toXml();
    }

    @Tool("驳回活动审批。参数格式: '活动ID或名称|驳回原因'，用竖线分隔")
    public String rejectActivity(String input) {
        String[] parts = input.split("\\|", 2);
        String param = parts[0].trim();
        String reason = parts.length > 1 ? parts[1].trim() : "不符合要求";
        IchActivityDTO activity = findActivityByParam(param);
        if (activity == null) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty(listPendingActivitiesHint("驳回"))
                    .withSearchEmptyHint("活动").toXml();
        }
        PendingAction action = PendingAction.of("reject_activity",
                "驳回活动「" + activity.getName() + "」，原因: " + reason)
                .param("activityId", String.valueOf(activity.getId()))
                .param("rejectReason", reason);
        actionExecutor.savePendingAction(AiRequestContext.getSessionId(), action);
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.actionProposed("已提交驳回「" + activity.getName() + "」的确认请求，原因: " + reason, null)
                .withActionProposedHint("驳回活动").toXml();
    }

    @Tool("订单发货。参数: 订单号")
    public String shipOrder(String orderNo) {
        OrderDTO order = null;
        try { order = orderService.getOrderByOrderNo(orderNo.trim()); } catch (Exception ignored) {}
        if (order == null) {
            AiRequestContext.recordToolFailure();
            try {
                PageResult<OrderDTO> pending = orderService.listOrders(null, 1, 1, 5);
                if (pending != null && pending.getList() != null && !pending.getList().isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (OrderDTO o : pending.getList()) {
                        sb.append("- ").append(o.getOrderNo()).append(", ¥").append(o.getPayAmount() != null ? o.getPayAmount() : "0").append("\n");
                    }
                    return ToolResultWrapper.success("未找到该订单，以下是待发货订单", sb.toString())
                            .withHints("列出待发货订单让管理员选择", "管理员输入的订单号未匹配",
                                      "不要自动选择订单发货").toXml();
                }
            } catch (Exception ignored) {}
            return ToolResultWrapper.empty("未找到该订单").withSearchEmptyHint("订单").toXml();
        }
        if (order.getStatus() != null && order.getStatus() != 1) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.error("订单 " + order.getOrderNo() + " 状态为「" + AgentUtils.formatOrderStatus(order.getStatus()) + "」，无法发货")
                    .withErrorHint().toXml();
        }
        PendingAction action = PendingAction.of("ship_order", "为订单「" + order.getOrderNo() + "」发货")
                .param("orderNo", order.getOrderNo());
        actionExecutor.savePendingAction(AiRequestContext.getSessionId(), action);
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.actionProposed("已提交订单 " + order.getOrderNo() + " 发货的确认请求", null)
                .withActionProposedHint("发货").toXml();
    }

    @Tool("发布通知/公告。参数: 通知ID")
    public String publishNotification(String notificationIdStr) {
        SysNotificationDTO notification = null;
        try { notification = systemService.getNotificationById(Long.parseLong(notificationIdStr.trim())); } catch (Exception ignored) {}
        if (notification == null) {
            AiRequestContext.recordToolFailure();
            try {
                PageResult<SysNotificationDTO> unpublished = systemService.listNotifications(1, 5, null, null, 0);
                if (unpublished != null && unpublished.getList() != null && !unpublished.getList().isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (SysNotificationDTO n : unpublished.getList())
                        sb.append("- ID:").append(n.getId()).append(" ").append(n.getTitle() != null ? n.getTitle() : "无标题").append("\n");
                    return ToolResultWrapper.success("未找到该通知，以下是未发布通知", sb.toString())
                            .withHints("列出未发布通知让管理员选择", "输入的通知ID未匹配",
                                      "不要自动选择通知发布").toXml();
                }
            } catch (Exception ignored) {}
            return ToolResultWrapper.empty("未找到该通知").withSearchEmptyHint("通知").toXml();
        }
        if (notification.getIsPublished() != null && notification.getIsPublished() == 1) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.error("通知「" + notification.getTitle() + "」已经发布过了").withErrorHint().toXml();
        }
        PendingAction action = PendingAction.of("publish_notification", "发布通知「" + notification.getTitle() + "」")
                .param("notificationId", String.valueOf(notification.getId()));
        actionExecutor.savePendingAction(AiRequestContext.getSessionId(), action);
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.actionProposed("已提交发布通知「" + notification.getTitle() + "」的确认请求", null)
                .withActionProposedHint("发布通知").toXml();
    }

    @Tool("上架或下架活动。参数格式: '活动ID或名称|上架' 或 '活动ID或名称|下架'")
    public String updateActivityStatus(String input) {
        String[] parts = input.split("\\|", 2);
        String param = parts[0].trim();
        boolean isOnline = parts.length <= 1 || parts[1].trim().contains("上架") || parts[1].trim().contains("启用");
        IchActivityDTO activity = findActivityByParam(param);
        if (activity == null) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("未找到匹配的活动，请提供活动ID或名称")
                    .withSearchEmptyHint("活动").toXml();
        }
        int targetStatus = isOnline ? 1 : 0;
        String statusDesc = isOnline ? "上架" : "下架";
        PendingAction action = PendingAction.of("update_activity_status", statusDesc + "活动「" + activity.getName() + "」")
                .param("activityId", String.valueOf(activity.getId()))
                .param("status", String.valueOf(targetStatus));
        actionExecutor.savePendingAction(AiRequestContext.getSessionId(), action);
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.actionProposed("已提交" + statusDesc + "「" + activity.getName() + "」的确认请求", null)
                .withActionProposedHint(statusDesc + "活动").toXml();
    }

    // ==================== 辅助方法 ====================

    private IchActivityDTO findActivityByParam(String param) {
        if (param == null || param.isEmpty()) return null;
        try { return contentService.getActivityById(Long.parseLong(param.trim())); } catch (NumberFormatException ignored) {}
        try {
            PageResult<IchActivityDTO> r = contentService.listActivities(1, 1, param.trim(), null, null);
            if (r != null && r.getList() != null && !r.getList().isEmpty()) return r.getList().get(0);
        } catch (Exception ignored) {}
        return null;
    }

    private String listPendingActivitiesHint(String actionDesc) {
        try {
            PageResult<IchActivityDTO> pending = contentService.listActivitiesByApproval(1, 5, null, 0);
            if (pending != null && pending.getList() != null && !pending.getList().isEmpty()) {
                StringBuilder sb = new StringBuilder("待审批活动:\n");
                for (IchActivityDTO a : pending.getList())
                    sb.append("- ID:").append(a.getId()).append(" ").append(a.getName()).append("\n");
                return sb + "\n请告知您要" + actionDesc + "哪个活动";
            }
        } catch (Exception ignored) {}
        return "暂无待审批活动";
    }
}

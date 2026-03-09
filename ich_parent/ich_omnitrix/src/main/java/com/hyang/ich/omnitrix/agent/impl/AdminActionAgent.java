package com.hyang.ich.omnitrix.agent.impl;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.content.ContentService;
import com.hyang.ich.content.dto.IchActivityDTO;
import com.hyang.ich.order.OrderService;
import com.hyang.ich.order.dto.OrderDTO;
import com.hyang.ich.system.SystemService;
import com.hyang.ich.system.dto.SysNotificationDTO;
import com.hyang.ich.omnitrix.agent.AgentContext;
import com.hyang.ich.omnitrix.agent.AgentUtils;
import com.hyang.ich.omnitrix.agent.SubAgent;
import com.hyang.ich.omnitrix.agent.tool.AgentTool;
import com.hyang.ich.omnitrix.agent.tool.ToolCallResult;
import com.hyang.ich.omnitrix.agent.tool.ToolSelector;
import com.hyang.ich.omnitrix.dto.AgentQueryResult;
import com.hyang.ich.omnitrix.dto.PendingAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 管理员操作代理 —— 审批活动、订单发货、发布通知等管理写操作
 */
@Slf4j
@Component
public class AdminActionAgent implements SubAgent {

    private final ContentService contentService;
    private final OrderService orderService;
    private final SystemService systemService;
    private final ToolSelector toolSelector;

    private static final List<AgentTool> TOOLS = Arrays.asList(
            AgentTool.of("approve_activity", "审批通过活动、批准活动", "活动ID或关键词"),
            AgentTool.of("reject_activity", "驳回活动、拒绝活动审批", "活动ID或关键词和驳回原因"),
            AgentTool.of("ship_order", "订单发货、发出商品", "订单号"),
            AgentTool.of("publish_notification", "发布通知、发公告", "通知ID或关键词"),
            AgentTool.of("update_activity_status", "上架活动、下架活动、启用活动、停用活动", "活动ID或关键词")
    );

    public AdminActionAgent(ContentService contentService, OrderService orderService,
                            SystemService systemService, ToolSelector toolSelector) {
        this.contentService = contentService;
        this.orderService = orderService;
        this.systemService = systemService;
        this.toolSelector = toolSelector;
    }

    @Override
    public String getCode() {
        return "admin_action_agent";
    }

    @Override
    public String getName() {
        return "管理操作助手";
    }

    @Override
    public String getDescription() {
        return "执行管理员操作：审批活动、订单发货、发布通知（管理员专用）";
    }

    @Override
    public String getAgentPrompt() {
        return "## 当前任务模式: 管理员操作助手\n" +
                "你现在是管理员的操作助手，帮助管理员执行审批、发货、通知发布等管理操作。\n" +
                "- 所有写操作都需要管理员确认后才执行\n" +
                "- 操作前先展示目标详情，让管理员确认\n" +
                "- 对于批量操作，逐一列出并确认";
    }

    @Override
    public AgentQueryResult execute(String userQuery, AgentContext context) {
        try {
            ToolCallResult toolCall = toolSelector.select(userQuery, TOOLS);
            log.debug("AdminActionAgent 工具选择: tool={}", toolCall.getToolName());

            String selectedTool = toolCall.isNone() ? fallbackTool(userQuery) : toolCall.getToolName();
            String param = toolCall.getParameter() != null && !toolCall.getParameter().isEmpty()
                    ? toolCall.getParameter() : AgentUtils.extractKeyword(userQuery);

            switch (selectedTool) {
                case "approve_activity":
                    return proposeApproveActivity(param, context);
                case "reject_activity":
                    return proposeRejectActivity(param, context);
                case "ship_order":
                    return proposeShipOrder(param, context);
                case "publish_notification":
                    return proposePublishNotification(param, context);
                case "update_activity_status":
                    return proposeUpdateActivityStatus(param, userQuery, context);
                default:
                    return AgentQueryResult.success("请告诉我您需要执行什么管理操作？\n" +
                            "支持的操作：审批活动、驳回活动、订单发货、发布通知、活动上下架", getCode());
            }

        } catch (Exception e) {
            log.error("AdminActionAgent 执行异常: {}", e.getMessage(), e);
            return AgentQueryResult.error(getCode(), e.getMessage());
        }
    }

    private String fallbackTool(String query) {
        String q = query.toLowerCase();
        if (q.contains("通过") || q.contains("批准") || (q.contains("审批") && !q.contains("驳回"))) return "approve_activity";
        if (q.contains("驳回") || q.contains("拒绝")) return "reject_activity";
        if (q.contains("发货") || q.contains("发出") || q.contains("寄出")) return "ship_order";
        if (q.contains("发布通知") || q.contains("发通知") || q.contains("发公告")) return "publish_notification";
        if (q.contains("上架") || q.contains("下架") || q.contains("启用") || q.contains("停用")) return "update_activity_status";
        return "none";
    }

    // ========== 写操作提议 ==========

    private AgentQueryResult proposeApproveActivity(String param, AgentContext context) {
        // 尝试按ID查找
        IchActivityDTO activity = findActivityByParam(param);
        if (activity == null) {
            // 列出待审批活动
            try {
                PageResult<IchActivityDTO> pending = contentService.listActivitiesByApproval(1, 5, null, 0);
                if (pending != null && pending.getList() != null && !pending.getList().isEmpty()) {
                    StringBuilder sb = new StringBuilder("待审批活动列表:\n");
                    for (IchActivityDTO a : pending.getList()) {
                        sb.append("- ID:").append(a.getId()).append(" ").append(a.getName());
                        if (a.getOrganizer() != null) sb.append(" (主办: ").append(a.getOrganizer()).append(")");
                        sb.append("\n");
                    }
                    sb.append("\n请告诉我您要审批通过哪个活动(可以说活动ID或名称)");
                    return AgentQueryResult.success(sb.toString(), getCode());
                }
            } catch (Exception ignored) {}
            return AgentQueryResult.success("暂无待审批活动", getCode());
        }

        PendingAction action = PendingAction.of("approve_activity",
                "审批通过活动「" + activity.getName() + "」(ID:" + activity.getId() + ")")
                .param("activityId", String.valueOf(activity.getId()));
        String data = "找到活动:\n- 名称: " + activity.getName() +
                (activity.getOrganizer() != null ? "\n- 主办: " + activity.getOrganizer() : "") +
                (activity.getStartTime() != null ? "\n- 开始时间: " + activity.getStartTime() : "");
        return AgentQueryResult.actionProposed(data, getCode(), action);
    }

    private AgentQueryResult proposeRejectActivity(String param, AgentContext context) {
        // 尝试从param中分离活动标识和驳回原因
        String activityKeyword = param;
        String rejectReason = "不符合要求";
        if (param.contains("原因") || param.contains("因为")) {
            int idx = Math.max(param.indexOf("原因"), param.indexOf("因为"));
            activityKeyword = param.substring(0, idx).trim();
            rejectReason = param.substring(idx).replaceFirst("(原因|因为)[：:]?\\s*", "").trim();
            if (rejectReason.isEmpty()) rejectReason = "不符合要求";
        }

        IchActivityDTO activity = findActivityByParam(activityKeyword);
        if (activity == null) {
            try {
                PageResult<IchActivityDTO> pending = contentService.listActivitiesByApproval(1, 5, null, 0);
                if (pending != null && pending.getList() != null && !pending.getList().isEmpty()) {
                    StringBuilder sb = new StringBuilder("待审批活动列表:\n");
                    for (IchActivityDTO a : pending.getList()) {
                        sb.append("- ID:").append(a.getId()).append(" ").append(a.getName()).append("\n");
                    }
                    sb.append("\n请告诉我您要驳回哪个活动");
                    return AgentQueryResult.success(sb.toString(), getCode());
                }
            } catch (Exception ignored) {}
            return AgentQueryResult.success("暂无待审批活动", getCode());
        }

        PendingAction action = PendingAction.of("reject_activity",
                "驳回活动「" + activity.getName() + "」，原因: " + rejectReason)
                .param("activityId", String.valueOf(activity.getId()))
                .param("rejectReason", rejectReason);
        String data = "找到活动:\n- 名称: " + activity.getName() +
                "\n- 驳回原因: " + rejectReason;
        return AgentQueryResult.actionProposed(data, getCode(), action);
    }

    private AgentQueryResult proposeShipOrder(String param, AgentContext context) {
        // 尝试按订单号查找
        OrderDTO order = null;
        if (param != null && !param.isEmpty()) {
            try {
                order = orderService.getOrderByOrderNo(param.trim());
            } catch (Exception ignored) {}
        }

        if (order == null) {
            // 列出待发货订单
            try {
                PageResult<OrderDTO> pending = orderService.listOrders(null, 1, 1, 5);
                if (pending != null && pending.getList() != null && !pending.getList().isEmpty()) {
                    StringBuilder sb = new StringBuilder("待发货订单:\n");
                    for (OrderDTO o : pending.getList()) {
                        sb.append("- ").append(o.getOrderNo());
                        sb.append(", ¥").append(o.getPayAmount() != null ? o.getPayAmount() : "0");
                        if (o.getReceiverName() != null) sb.append(", 收货人: ").append(o.getReceiverName());
                        sb.append("\n");
                    }
                    sb.append("\n请告诉我您要发货哪个订单(请提供订单号)");
                    return AgentQueryResult.success(sb.toString(), getCode());
                }
            } catch (Exception ignored) {}
            return AgentQueryResult.success("暂无待发货订单", getCode());
        }

        if (order.getStatus() != null && order.getStatus() != 1) {
            return AgentQueryResult.success("订单 " + order.getOrderNo() + " 状态为「" +
                    AgentUtils.formatOrderStatus(order.getStatus()) + "」，无法发货", getCode());
        }

        PendingAction action = PendingAction.of("ship_order",
                "为订单「" + order.getOrderNo() + "」发货")
                .param("orderNo", order.getOrderNo());
        String data = "订单详情:\n- 订单号: " + order.getOrderNo() +
                "\n- 金额: ¥" + order.getPayAmount() +
                (order.getReceiverName() != null ? "\n- 收货人: " + order.getReceiverName() : "") +
                (order.getReceiverDetailAddress() != null ? "\n- 地址: " + order.getReceiverDetailAddress() : "");
        return AgentQueryResult.actionProposed(data, getCode(), action);
    }

    private AgentQueryResult proposePublishNotification(String param, AgentContext context) {
        SysNotificationDTO notification = null;
        // 尝试按ID查找
        try {
            Long id = Long.parseLong(param.trim());
            notification = systemService.getNotificationById(id);
        } catch (NumberFormatException ignored) {}

        if (notification == null) {
            // 列出未发布的通知
            try {
                PageResult<SysNotificationDTO> unpublished = systemService.listNotifications(1, 5, null, null, 0);
                if (unpublished != null && unpublished.getList() != null && !unpublished.getList().isEmpty()) {
                    StringBuilder sb = new StringBuilder("未发布通知列表:\n");
                    for (SysNotificationDTO n : unpublished.getList()) {
                        sb.append("- ID:").append(n.getId()).append(" ").append(n.getTitle() != null ? n.getTitle() : "无标题").append("\n");
                    }
                    sb.append("\n请告诉我您要发布哪个通知(可以说通知ID)");
                    return AgentQueryResult.success(sb.toString(), getCode());
                }
            } catch (Exception ignored) {}
            return AgentQueryResult.success("暂无未发布的通知", getCode());
        }

        if (notification.getIsPublished() != null && notification.getIsPublished() == 1) {
            return AgentQueryResult.success("通知「" + notification.getTitle() + "」已经发布过了", getCode());
        }

        PendingAction action = PendingAction.of("publish_notification",
                "发布通知「" + notification.getTitle() + "」")
                .param("notificationId", String.valueOf(notification.getId()));
        String data = "通知详情:\n- 标题: " + notification.getTitle() +
                (notification.getContent() != null ? "\n- 内容: " + AgentUtils.truncateDesc(notification.getContent(), 100) : "");
        return AgentQueryResult.actionProposed(data, getCode(), action);
    }

    private AgentQueryResult proposeUpdateActivityStatus(String param, String userQuery, AgentContext context) {
        IchActivityDTO activity = findActivityByParam(param);
        if (activity == null) {
            return AgentQueryResult.success("未找到匹配的活动，请提供活动ID或名称", getCode());
        }

        boolean isOnline = userQuery.contains("上架") || userQuery.contains("启用");
        int targetStatus = isOnline ? 1 : 0;
        String statusDesc = isOnline ? "上架" : "下架";

        PendingAction action = PendingAction.of("update_activity_status",
                statusDesc + "活动「" + activity.getName() + "」")
                .param("activityId", String.valueOf(activity.getId()))
                .param("status", String.valueOf(targetStatus));
        String data = "活动: " + activity.getName() + " (ID:" + activity.getId() + ")\n操作: " + statusDesc;
        return AgentQueryResult.actionProposed(data, getCode(), action);
    }

    // ========== 辅助方法 ==========

    private IchActivityDTO findActivityByParam(String param) {
        if (param == null || param.isEmpty()) return null;
        // 尝试按ID查找
        try {
            Long id = Long.parseLong(param.trim());
            return contentService.getActivityById(id);
        } catch (NumberFormatException ignored) {}
        // 按关键词搜索
        try {
            PageResult<IchActivityDTO> result = contentService.listActivities(1, 1, param.trim(), null, null);
            if (result != null && result.getList() != null && !result.getList().isEmpty()) {
                return result.getList().get(0);
            }
        } catch (Exception ignored) {}
        return null;
    }
}

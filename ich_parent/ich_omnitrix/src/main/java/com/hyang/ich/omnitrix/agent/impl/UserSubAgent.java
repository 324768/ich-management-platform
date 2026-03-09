package com.hyang.ich.omnitrix.agent.impl;

import com.hyang.ich.omnitrix.agent.AgentContext;
import com.hyang.ich.omnitrix.agent.AgentUtils;
import com.hyang.ich.omnitrix.agent.SubAgent;
import com.hyang.ich.omnitrix.agent.tool.AgentTool;
import com.hyang.ich.omnitrix.agent.tool.ToolCallResult;
import com.hyang.ich.omnitrix.agent.tool.ToolSelector;
import com.hyang.ich.omnitrix.dto.AgentQueryResult;
import com.hyang.ich.omnitrix.dto.PendingAction;
import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.system.SystemService;
import com.hyang.ich.system.dto.SysNotificationDTO;
import com.hyang.ich.user.UserService;
import com.hyang.ich.user.dto.UserAddressDTO;
import com.hyang.ich.user.dto.UserDTO;
import com.hyang.ich.user.dto.IchUserQualificationDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class UserSubAgent implements SubAgent {

    private final UserService userService;
    private final SystemService systemService;
    private final ToolSelector toolSelector;

    private static final List<AgentTool> TOOLS = Arrays.asList(
            AgentTool.of("query_addresses", "查询用户的收货地址列表", "无"),
            AgentTool.of("query_qualification", "查询用户的资格认证、传承人认证状态", "无"),
            AgentTool.of("query_profile", "查询用户基本信息、个人资料、用户名、手机号", "无"),
            AgentTool.of("set_default_address", "设置默认地址、切换默认收货地址", "地址关键词"),
            AgentTool.of("delete_address", "删除收货地址、移除地址", "地址关键词"),
            AgentTool.of("query_notifications", "查看系统通知、平台公告、消息", "无")
    );

    public UserSubAgent(UserService userService, SystemService systemService, ToolSelector toolSelector) {
        this.userService = userService;
        this.systemService = systemService;
        this.toolSelector = toolSelector;
    }

    @Override
    public String getCode() {
        return "user_assistant";
    }

    @Override
    public String getName() {
        return "用户服务助手";
    }

    @Override
    public String getDescription() {
        return "回答用户个人信息、地址、资格认证相关问题";
    }

    @Override
    public String getAgentPrompt() {
        return "## 当前任务模式: 用户服务助手\n" +
                "你现在专注于回答用户个人信息、地址、资格认证相关问题。\n" +
                "- 涉及隐私数据时只展示必要信息\n" +
                "- 引导用户到对应页面操作，而非替用户执行修改";
    }

    @Override
    public AgentQueryResult execute(String userQuery, AgentContext context) {
        try {
            ToolCallResult toolCall = toolSelector.select(userQuery, TOOLS);
            log.debug("UserSubAgent 工具选择: tool={}", toolCall.getToolName());

            String selectedTool = toolCall.isNone() ? fallbackTool(userQuery) : toolCall.getToolName();
            String param = toolCall.getParameter() != null && !toolCall.getParameter().isEmpty()
                    ? toolCall.getParameter() : AgentUtils.extractKeyword(userQuery);

            StringBuilder data = new StringBuilder();

            switch (selectedTool) {
                case "query_addresses":
                    data.append(queryAddresses(context));
                    break;
                case "query_qualification":
                    data.append(queryQualification(context));
                    break;
                case "set_default_address":
                    return proposeSetDefaultAddress(param, context);
                case "delete_address":
                    return proposeDeleteAddress(param, context);
                case "query_notifications":
                    data.append(queryNotifications());
                    break;
                case "query_profile":
                default:
                    data.append(queryProfile(context));
                    break;
            }

            if (data.length() == 0) {
                return AgentQueryResult.empty(getCode());
            }
            return AgentQueryResult.success(data.toString(), getCode());

        } catch (Exception e) {
            log.error("UserSubAgent 执行异常: {}", e.getMessage(), e);
            return AgentQueryResult.error(getCode(), e.getMessage());
        }
    }

    private String fallbackTool(String query) {
        String q = query.toLowerCase();
        if (q.contains("设置默认地址") || q.contains("切换默认")) return "set_default_address";
        if (q.contains("删除地址") || q.contains("移除地址")) return "delete_address";
        if (q.contains("地址") || q.contains("收货")) return "query_addresses";
        if (q.contains("资格") || q.contains("认证") || q.contains("传承人认证")) return "query_qualification";
        if (q.contains("通知") || q.contains("公告") || q.contains("消息")) return "query_notifications";
        return "query_profile";
    }

    private String queryAddresses(AgentContext context) {
        List<UserAddressDTO> addresses = userService.listAddresses(context.getUserId());
        if (addresses == null || addresses.isEmpty()) {
            return "暂无收货地址\n";
        }
        StringBuilder sb = new StringBuilder("收货地址列表:\n");
        for (UserAddressDTO addr : addresses) {
            sb.append("- ").append(addr.getReceiverName())
                    .append(", ").append(addr.getReceiverPhone())
                    .append(", ").append(addr.getProvince()).append(addr.getCity()).append(addr.getDistrict()).append(addr.getDetailAddress());
            if (addr.getIsDefault() != null && addr.getIsDefault() == 1) {
                sb.append(" [默认]");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String queryQualification(AgentContext context) {
        IchUserQualificationDTO qualification = userService.getQualificationByUserId(context.getUserId());
        if (qualification == null) {
            return "您尚未提交资格认证申请\n";
        }
        StringBuilder sb = new StringBuilder("资格认证信息:\n");
        sb.append("- 状态: ").append(AgentUtils.formatQualificationStatus(qualification.getStatus())).append("\n");
        if (qualification.getRejectReason() != null) {
            sb.append("- 驳回原因: ").append(qualification.getRejectReason()).append("\n");
        }
        return sb.toString();
    }

    // ========== 写操作提议 ==========

    private AgentQueryResult proposeSetDefaultAddress(String keyword, AgentContext context) {
        List<UserAddressDTO> addresses = userService.listAddresses(context.getUserId());
        if (addresses == null || addresses.isEmpty()) {
            return AgentQueryResult.success("您还没有收货地址", getCode());
        }
        UserAddressDTO target = null;
        for (UserAddressDTO addr : addresses) {
            String full = (addr.getReceiverName() != null ? addr.getReceiverName() : "") +
                    (addr.getDetailAddress() != null ? addr.getDetailAddress() : "");
            if (full.contains(keyword) || keyword.contains(addr.getReceiverName() != null ? addr.getReceiverName() : "")) {
                target = addr;
                break;
            }
        }
        StringBuilder data = new StringBuilder("收货地址列表:\n");
        int idx = 1;
        for (UserAddressDTO addr : addresses) {
            data.append(idx++).append(". ").append(addr.getReceiverName())
                    .append(", ").append(addr.getProvince()).append(addr.getCity()).append(addr.getDistrict()).append(addr.getDetailAddress());
            if (addr.getIsDefault() != null && addr.getIsDefault() == 1) data.append(" [默认]");
            data.append("\n");
        }
        if (target == null && addresses.size() > 1) {
            return AgentQueryResult.success(
                    data + "\n请告知您要设置哪个地址为默认", getCode());
        }
        if (target == null) target = addresses.get(0);
        PendingAction action = PendingAction.of("set_default_address",
                "设置「" + target.getReceiverName() + "」的地址为默认")
                .param("addressId", String.valueOf(target.getId()));
        return AgentQueryResult.actionProposed(data.toString(), getCode(), action);
    }

    private AgentQueryResult proposeDeleteAddress(String keyword, AgentContext context) {
        List<UserAddressDTO> addresses = userService.listAddresses(context.getUserId());
        if (addresses == null || addresses.isEmpty()) {
            return AgentQueryResult.success("您还没有收货地址", getCode());
        }
        UserAddressDTO target = null;
        for (UserAddressDTO addr : addresses) {
            String full = (addr.getReceiverName() != null ? addr.getReceiverName() : "") +
                    (addr.getDetailAddress() != null ? addr.getDetailAddress() : "");
            if (full.contains(keyword) || keyword.contains(addr.getReceiverName() != null ? addr.getReceiverName() : "")) {
                target = addr;
                break;
            }
        }
        StringBuilder data = new StringBuilder("收货地址列表:\n");
        int idx = 1;
        for (UserAddressDTO addr : addresses) {
            data.append(idx++).append(". ").append(addr.getReceiverName())
                    .append(", ").append(addr.getProvince()).append(addr.getCity()).append(addr.getDistrict()).append(addr.getDetailAddress());
            if (addr.getIsDefault() != null && addr.getIsDefault() == 1) data.append(" [默认]");
            data.append("\n");
        }
        if (target == null) {
            return AgentQueryResult.success(
                    data + "\n请告知您要删除哪个地址", getCode());
        }
        PendingAction action = PendingAction.of("delete_address",
                "删除地址「" + target.getReceiverName() + " - " + target.getDetailAddress() + "」")
                .param("addressId", String.valueOf(target.getId()));
        return AgentQueryResult.actionProposed(data.toString(), getCode(), action);
    }

    // ========== 只读查询 ==========

    private String queryNotifications() {
        try {
            PageResult<SysNotificationDTO> notifications = systemService.listNotifications(1, 5, null, null, 1);
            if (notifications == null || notifications.getList() == null || notifications.getList().isEmpty()) {
                return "暂无系统通知\n";
            }
            StringBuilder sb = new StringBuilder("最新系统通知:\n");
            for (SysNotificationDTO n : notifications.getList()) {
                sb.append("- ").append(n.getTitle() != null ? n.getTitle() : "无标题");
                if (n.getCreateTime() != null) {
                    sb.append(" (").append(new java.text.SimpleDateFormat("MM-dd").format(n.getCreateTime())).append(")");
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.debug("查询通知异常: {}", e.getMessage());
            return "";
        }
    }

    private String queryProfile(AgentContext context) {
        UserDTO user = userService.findById(context.getUserId());
        if (user == null) return "";
        StringBuilder sb = new StringBuilder("用户信息:\n");
        sb.append("- 用户名: ").append(user.getUsername()).append("\n");
        if (user.getNickname() != null) {
            sb.append("- 昵称: ").append(user.getNickname()).append("\n");
        }
        if (user.getPhone() != null) {
            String phone = user.getPhone();
            if (phone.length() > 7) {
                phone = phone.substring(0, 3) + "****" + phone.substring(7);
            }
            sb.append("- 手机: ").append(phone).append("\n");
        }
        boolean hasHeritage = userService.hasHeritageFlag(context.getUserId());
        sb.append("- 传承人标志: ").append(hasHeritage ? "是" : "否").append("\n");
        return sb.toString();
    }
}

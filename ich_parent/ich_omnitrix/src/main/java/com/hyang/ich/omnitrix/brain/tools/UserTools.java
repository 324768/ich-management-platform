package com.hyang.ich.omnitrix.brain.tools;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.omnitrix.agent.AgentUtils;
import com.hyang.ich.omnitrix.brain.AiRequestContext;
import com.hyang.ich.omnitrix.dto.PendingAction;
import com.hyang.ich.omnitrix.orchestrator.ActionExecutor;
import com.hyang.ich.system.SystemService;
import com.hyang.ich.system.dto.SysNotificationDTO;
import com.hyang.ich.user.UserService;
import com.hyang.ich.user.dto.IchUserQualificationDTO;
import com.hyang.ich.user.dto.UserAddressDTO;
import com.hyang.ich.user.dto.UserDTO;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.List;

/**
 * 用户服务工具集 — 替代原 UserSubAgent，提供 LangChain4j @Tool 方法。
 */
@Slf4j
@Component
public class UserTools {

    private final UserService userService;
    private final SystemService systemService;
    private final ActionExecutor actionExecutor;

    public UserTools(UserService userService, SystemService systemService,
                     ActionExecutor actionExecutor) {
        this.userService = userService;
        this.systemService = systemService;
        this.actionExecutor = actionExecutor;
    }

    @Tool("查询当前用户基本信息、个人资料、用户名、手机号")
    public String queryProfile() {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        Long userId = AiRequestContext.getUserId();
        UserDTO user = userService.findById(userId);
        if (user == null) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("未找到用户信息").withSearchEmptyHint("用户信息").toXml();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("- 用户名: ").append(user.getUsername()).append("\n");
        if (user.getNickname() != null) sb.append("- 昵称: ").append(user.getNickname()).append("\n");
        if (user.getPhone() != null) {
            String phone = user.getPhone();
            if (phone.length() > 7) phone = phone.substring(0, 3) + "****" + phone.substring(7);
            sb.append("- 手机: ").append(phone).append("\n");
        }
        boolean hasHeritage = userService.hasHeritageFlag(userId);
        sb.append("- 传承人标志: ").append(hasHeritage ? "是" : "否").append("\n");
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.success("已获取用户资料", sb.toString())
                .withSearchSuccessHint("用户资料").toXml();
    }

    @Tool("查询当前用户的收货地址列表")
    public String queryAddresses() {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        Long userId = AiRequestContext.getUserId();
        List<UserAddressDTO> addresses = userService.listAddresses(userId);
        if (addresses == null || addresses.isEmpty()) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("暂无收货地址").withSearchEmptyHint("收货地址").toXml();
        }
        StringBuilder sb = new StringBuilder();
        for (UserAddressDTO addr : addresses) {
            sb.append("- ").append(addr.getReceiverName())
                    .append(", ").append(addr.getReceiverPhone())
                    .append(", ").append(addr.getProvince()).append(addr.getCity())
                    .append(addr.getDistrict()).append(addr.getDetailAddress());
            if (addr.getIsDefault() != null && addr.getIsDefault() == 1) sb.append(" [默认]");
            sb.append("\n");
        }
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.success("找到" + addresses.size() + "个收货地址", sb.toString())
                .withSearchSuccessHint("收货地址").toXml();
    }

    @Tool("查询当前用户的资格认证、传承人认证状态")
    public String queryQualification() {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        Long userId = AiRequestContext.getUserId();
        IchUserQualificationDTO q = userService.getQualificationByUserId(userId);
        if (q == null) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("您尚未提交资格认证申请").withSearchEmptyHint("资格认证").toXml();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("- 状态: ").append(AgentUtils.formatQualificationStatus(q.getStatus())).append("\n");
        if (q.getRejectReason() != null) sb.append("- 驳回原因: ").append(q.getRejectReason()).append("\n");
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.success("已获取资格认证信息", sb.toString())
                .withSearchSuccessHint("资格认证").toXml();
    }

    @Tool("查看系统通知、平台公告")
    public String queryNotifications() {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        try {
            PageResult<SysNotificationDTO> notifications = systemService.listNotifications(1, 5, null, null, 1);
            if (notifications == null || notifications.getList() == null || notifications.getList().isEmpty()) {
                AiRequestContext.recordToolFailure();
                return ToolResultWrapper.empty("暂无系统通知").withSearchEmptyHint("通知").toXml();
            }
            StringBuilder sb = new StringBuilder();
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd");
            for (SysNotificationDTO n : notifications.getList()) {
                sb.append("- ").append(n.getTitle() != null ? n.getTitle() : "无标题");
                if (n.getCreateTime() != null) sb.append(" (").append(sdf.format(n.getCreateTime())).append(")");
                sb.append("\n");
            }
            AiRequestContext.recordToolSuccess();
            return ToolResultWrapper.success("找到" + notifications.getList().size() + "条通知", sb.toString())
                    .withSearchSuccessHint("系统通知").toXml();
        } catch (Exception e) {
            log.debug("查询通知异常: {}", e.getMessage());
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.error("查询通知时出现异常").withErrorHint().toXml();
        }
    }

    @Tool("设置默认收货地址。参数: 地址关键词（收件人名或地址内容）")
    public String setDefaultAddress(String keyword) {
        Long userId = AiRequestContext.getUserId();
        List<UserAddressDTO> addresses = userService.listAddresses(userId);
        if (addresses == null || addresses.isEmpty()) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("您还没有收货地址").withSearchEmptyHint("收货地址").toXml();
        }
        UserAddressDTO target = findAddress(addresses, keyword);
        if (target == null && addresses.size() > 1) {
            StringBuilder sb = new StringBuilder();
            int idx = 1;
            for (UserAddressDTO a : addresses) {
                sb.append(idx++).append(". ").append(a.getReceiverName()).append(", ")
                        .append(a.getProvince()).append(a.getCity()).append(a.getDetailAddress()).append("\n");
            }
            AiRequestContext.recordToolSuccess();
            return ToolResultWrapper.success("找到" + addresses.size() + "个地址，需用户指定", sb.toString())
                    .withHints("列出所有地址让用户选择要设为默认的地址", "用户未指定具体地址",
                              "不要自动选择地址，等用户明确指定").toXml();
        }
        if (target == null) target = addresses.get(0);
        PendingAction action = PendingAction.of("set_default_address",
                "设置「" + target.getReceiverName() + "」的地址为默认")
                .param("addressId", String.valueOf(target.getId()));
        actionExecutor.savePendingAction(AiRequestContext.getSessionId(), action);
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.actionProposed("已提交设置「" + target.getReceiverName() + "」为默认地址的确认请求", null)
                .withActionProposedHint("设置默认地址").toXml();
    }

    @Tool("删除收货地址。参数: 地址关键词（收件人名或地址内容）")
    public String deleteAddress(String keyword) {
        Long userId = AiRequestContext.getUserId();
        List<UserAddressDTO> addresses = userService.listAddresses(userId);
        if (addresses == null || addresses.isEmpty()) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("您还没有收货地址").withSearchEmptyHint("收货地址").toXml();
        }
        UserAddressDTO target = findAddress(addresses, keyword);
        if (target == null) {
            StringBuilder sb = new StringBuilder();
            int idx = 1;
            for (UserAddressDTO a : addresses) {
                sb.append(idx++).append(". ").append(a.getReceiverName()).append(", ")
                        .append(a.getProvince()).append(a.getCity()).append(a.getDetailAddress()).append("\n");
            }
            AiRequestContext.recordToolSuccess();
            return ToolResultWrapper.success("找到" + addresses.size() + "个地址，需用户指定", sb.toString())
                    .withHints("列出所有地址让用户选择要删除的地址", "用户未指定具体地址",
                              "不要自动选择地址删除，等用户明确指定").toXml();
        }
        PendingAction action = PendingAction.of("delete_address",
                "删除地址「" + target.getReceiverName() + " - " + target.getDetailAddress() + "」")
                .param("addressId", String.valueOf(target.getId()));
        actionExecutor.savePendingAction(AiRequestContext.getSessionId(), action);
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.actionProposed("已提交删除「" + target.getReceiverName() + "」地址的确认请求", null)
                .withActionProposedHint("删除地址").toXml();
    }

    private UserAddressDTO findAddress(List<UserAddressDTO> addresses, String keyword) {
        for (UserAddressDTO addr : addresses) {
            String full = (addr.getReceiverName() != null ? addr.getReceiverName() : "") +
                    (addr.getDetailAddress() != null ? addr.getDetailAddress() : "");
            if (full.contains(keyword) || keyword.contains(addr.getReceiverName() != null ? addr.getReceiverName() : "")) {
                return addr;
            }
        }
        return null;
    }
}

package com.hyang.ich.omnitrix.brain.tools;

import com.hyang.ich.omnitrix.brain.AiRequestContext;
import com.hyang.ich.omnitrix.brain.subbrain.AdminSubBrain;
import com.hyang.ich.omnitrix.brain.subbrain.SubBrainResult;
import com.hyang.ich.omnitrix.brain.subbrain.SubBrainResultWrapper;
import com.hyang.ich.omnitrix.brain.subbrain.UserSubBrain;
import com.hyang.ich.user.UserService;
import com.hyang.ich.user.dto.UserDTO;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * SubBrain 工具集 — 参考 Kortex SystemSubAgentToolProvider 设计。
 * <p>
 * 将 UserSubBrain 和 AdminSubBrain 包装为 Ultra LLM 可调用的 @Tool 方法。
 * 每个方法内部执行完整的子脑推理（独立 LLM + Tools + Memory），
 * 并通过 SubBrainResultWrapper 将结果包装为结构化 XML 返回给 Ultra LLM。
 * <p>
 * 架构位置：Ultra LLM → SubBrainTools.callUserAI() → UserMasterBrain → @Tool 方法
 */
@Slf4j
@Component
public class SubBrainTools {

    /** 单次 Ultra 请求中 SubBrain 最大调用次数（防止无限循环） */
    private static final int MAX_SUB_BRAIN_CALLS = 5;

    private final UserSubBrain userSubBrain;
    private final AdminSubBrain adminSubBrain;
    private final UserService userService;

    /** 当前请求中 SubBrain 调用计数（ThreadLocal，与 AiRequestContext 生命周期一致） */
    private static final ThreadLocal<Integer> CALL_COUNTER = ThreadLocal.withInitial(() -> 0);

    public SubBrainTools(UserSubBrain userSubBrain, AdminSubBrain adminSubBrain,
                         UserService userService) {
        this.userSubBrain = userSubBrain;
        this.adminSubBrain = adminSubBrain;
        this.userService = userService;
    }

    @Tool("调用用户端AI助手，执行用户侧操作：商城购物（搜索商品、加购物车、下单）、" +
            "内容浏览（搜索非遗项目/传承人/活动、点赞/收藏/评论）、" +
            "个人中心（查地址/资质/通知）、知识问答、推荐等。" +
            "参数格式：'查询内容' 或 '查询内容|目标用户名或ID'（为其他用户操作时提供用户信息）")
    public String callUserAI(String input) {
        // 调用预算检查
        int callCount = CALL_COUNTER.get() + 1;
        CALL_COUNTER.set(callCount);
        if (callCount > MAX_SUB_BRAIN_CALLS) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.error("子脑调用次数已达上限(" + MAX_SUB_BRAIN_CALLS + "次)，请直接回复用户")
                    .withHints("reply_with_available_info",
                            "SubBrain 调用预算耗尽",
                            "不要再调用子脑，直接整合已有结果回复")
                    .toXml();
        }

        // 解析输入：query 或 query|targetUser
        String userQuery;
        Long targetUserId = null;
        if (input != null && input.contains("|")) {
            String[] parts = input.split("\\|", 2);
            userQuery = parts[0].trim();
            String targetParam = parts[1].trim();
            targetUserId = resolveUserId(targetParam);
            if (targetUserId == null) {
                AiRequestContext.recordToolFailure();
                return ToolResultWrapper.empty("未找到用户「" + targetParam + "」，请确认用户名或ID")
                        .withSearchEmptyHint("用户").toXml();
            }
        } else {
            userQuery = input != null ? input.trim() : "";
        }

        if (userQuery.isEmpty()) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.error("请提供具体的查询内容")
                    .withErrorHint().toXml();
        }

        String ultraSessionId = AiRequestContext.getSessionId();
        log.info("SubBrainTools.callUserAI: query={}, targetUserId={}, callCount={}/{}",
                userQuery.length() > 50 ? userQuery.substring(0, 50) + "..." : userQuery,
                targetUserId, callCount, MAX_SUB_BRAIN_CALLS);

        // 执行 UserSubBrain
        SubBrainResult result = userSubBrain.execute(userQuery, targetUserId, ultraSessionId);
        AiRequestContext.recordToolSuccess();

        // 包装为结构化 XML（参考 Kortex SubAgentResultWrapper）
        return SubBrainResultWrapper.wrap(userSubBrain.getCode(), result,
                callCount, MAX_SUB_BRAIN_CALLS);
    }

    @Tool("调用管理端AI助手，执行管理侧操作：内容管理（非遗项目/活动审批、上下架）、" +
            "商品管理（商品查询、订单发货）、知识库管理、通知发布等管理运营任务。" +
            "参数：管理操作的查询内容")
    public String callAdminAI(String input) {
        // 调用预算检查
        int callCount = CALL_COUNTER.get() + 1;
        CALL_COUNTER.set(callCount);
        if (callCount > MAX_SUB_BRAIN_CALLS) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.error("子脑调用次数已达上限(" + MAX_SUB_BRAIN_CALLS + "次)，请直接回复用户")
                    .withHints("reply_with_available_info",
                            "SubBrain 调用预算耗尽",
                            "不要再调用子脑，直接整合已有结果回复")
                    .toXml();
        }

        String userQuery = (input != null) ? input.trim() : "";
        if (userQuery.isEmpty()) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.error("请提供具体的管理操作内容")
                    .withErrorHint().toXml();
        }

        String ultraSessionId = AiRequestContext.getSessionId();
        log.info("SubBrainTools.callAdminAI: query={}, callCount={}/{}",
                userQuery.length() > 50 ? userQuery.substring(0, 50) + "..." : userQuery,
                callCount, MAX_SUB_BRAIN_CALLS);

        // 执行 AdminSubBrain
        SubBrainResult result = adminSubBrain.execute(userQuery, null, ultraSessionId);
        AiRequestContext.recordToolSuccess();

        // 包装为结构化 XML
        return SubBrainResultWrapper.wrap(adminSubBrain.getCode(), result,
                callCount, MAX_SUB_BRAIN_CALLS);
    }

    /**
     * 重置 SubBrain 调用计数器 — 在 OrchestratorService 每次请求开始时调用
     */
    public static void resetCallCounter() {
        CALL_COUNTER.set(0);
    }

    /**
     * 清理 ThreadLocal — 在 OrchestratorService 请求结束时调用
     */
    public static void clearCallCounter() {
        CALL_COUNTER.remove();
    }

    // ========== 辅助方法 ==========

    /**
     * 解析用户名或ID为 userId — 复用 UltraTools.findUser 的逻辑
     */
    private Long resolveUserId(String param) {
        if (param == null || param.isEmpty()) return null;
        // 尝试按 ID 解析
        try {
            Long id = Long.parseLong(param.trim());
            UserDTO user = userService.findById(id);
            return user != null ? user.getId() : null;
        } catch (NumberFormatException ignored) {
        }
        // 尝试按昵称查找
        try {
            UserDTO user = userService.findByNickname(param.trim());
            return user != null ? user.getId() : null;
        } catch (Exception ignored) {
        }
        return null;
    }
}

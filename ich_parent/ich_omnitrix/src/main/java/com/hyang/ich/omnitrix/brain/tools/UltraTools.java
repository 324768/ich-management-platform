package com.hyang.ich.omnitrix.brain.tools;

import com.hyang.ich.common.BrowseHistoryService;
import com.hyang.ich.common.dto.BrowseHistoryDTO;
import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.omnitrix.brain.AiRequestContext;
import com.hyang.ich.omnitrix.dto.PendingAction;
import com.hyang.ich.omnitrix.entity.AiUserAiConfig;
import com.hyang.ich.omnitrix.entity.AiUserMemory;
import com.hyang.ich.omnitrix.mapper.AiUserAiConfigMapper;
import com.hyang.ich.omnitrix.mapper.AiUserMemoryMapper;
import com.hyang.ich.omnitrix.orchestrator.ActionExecutor;
import com.hyang.ich.omnitrix.service.SkillConfigService;
import com.hyang.ich.omnitrix.service.UserMemoryService;
import com.hyang.ich.product.ProductService;
import com.hyang.ich.product.dto.CartDTO;
import com.hyang.ich.product.dto.ProductDTO;
import com.hyang.ich.system.SystemService;
import com.hyang.ich.system.dto.SysOperationLogDTO;
import com.hyang.ich.user.UserService;
import com.hyang.ich.user.dto.UserDTO;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ultra 工具集 — 替代原 7 个 Ultra 代理（UltraSystemAgent, UltraUserControlAgent,
 * UltraCrossUserAgent, UltraAnalyticsAgent, UltraSecurityAgent, UltraBrowseHistoryAgent,
 * UltraSkillControlAgent），提供 LangChain4j @Tool 方法。
 *
 * 所有 Ultra 工具仅注册到 "ultra" 角色。
 */
@Slf4j
@Component
public class UltraTools {

    private final UserService userService;
    private final ProductService productService;
    private final SystemService systemService;
    private final AiUserAiConfigMapper configMapper;
    private final AiUserMemoryMapper userMemoryMapper;
    private final UserMemoryService userMemoryService;
    private final BrowseHistoryService browseHistoryService;
    private final SkillConfigService skillConfigService;
    private final ActionExecutor actionExecutor;

    private static final Map<String, String> TYPE_LABELS = new LinkedHashMap<>();
    static {
        TYPE_LABELS.put("ich_item", "非遗项目");
        TYPE_LABELS.put("culture", "非遗文化");
        TYPE_LABELS.put("heritage_man", "传承人");
        TYPE_LABELS.put("activity", "活动");
        TYPE_LABELS.put("product", "文创商品");
        TYPE_LABELS.put("knowledge", "知识");
    }

    private static final Map<String, String> TYPE_MAP = new LinkedHashMap<>();
    static {
        TYPE_MAP.put("非遗", "ich_item");
        TYPE_MAP.put("项目", "ich_item");
        TYPE_MAP.put("传承人", "heritage_man");
        TYPE_MAP.put("活动", "activity");
        TYPE_MAP.put("商品", "product");
        TYPE_MAP.put("文创", "product");
        TYPE_MAP.put("知识", "knowledge");
    }

    public UltraTools(UserService userService, ProductService productService,
                      SystemService systemService, AiUserAiConfigMapper configMapper,
                      AiUserMemoryMapper userMemoryMapper, UserMemoryService userMemoryService,
                      BrowseHistoryService browseHistoryService, SkillConfigService skillConfigService,
                      ActionExecutor actionExecutor) {
        this.userService = userService;
        this.productService = productService;
        this.systemService = systemService;
        this.configMapper = configMapper;
        this.userMemoryMapper = userMemoryMapper;
        this.userMemoryService = userMemoryService;
        this.browseHistoryService = browseHistoryService;
        this.skillConfigService = skillConfigService;
        this.actionExecutor = actionExecutor;
    }

    // ==================== 系统管理 (UltraSystemAgent) ====================

    @Tool("查看用户列表。参数: 搜索关键词（可为空查看全部）")
    public String listUsers(String keyword) {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        try {
            String searchKey = (keyword != null && !keyword.isEmpty() && !keyword.equals("无")) ? keyword : null;
            PageResult<UserDTO> result = userService.listUsers(1, 20, searchKey);
            if (result == null || result.getList() == null || result.getList().isEmpty()) {
                AiRequestContext.recordToolFailure();
                return ToolResultWrapper.empty("未找到匹配的用户").withSearchEmptyHint("用户").toXml();
            }
            StringBuilder sb = new StringBuilder();
            for (UserDTO u : result.getList()) {
                sb.append("- ").append(u.getNickname() != null ? u.getNickname() : u.getUsername())
                        .append(" (ID:").append(u.getId()).append(")");
                sb.append(", 状态: ").append(u.getStatus() != null && u.getStatus() == 1 ? "正常" : "禁用")
                        .append("\n");
            }
            AiRequestContext.recordToolSuccess();
            return ToolResultWrapper.success("共" + result.getTotal() + "个用户", sb.toString())
                    .withSearchSuccessHint("用户列表").toXml();
        } catch (Exception e) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.error("查询用户列表失败: " + e.getMessage()).withErrorHint().toXml();
        }
    }

    @Tool("查询在线用户列表")
    public String queryOnlineUsers() {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        try {
            List<UserDTO> onlineUsers = userService.listOnlineUsers();
            if (onlineUsers == null || onlineUsers.isEmpty()) {
                AiRequestContext.recordToolFailure();
                return ToolResultWrapper.empty("当前没有在线用户").withSearchEmptyHint("在线用户").toXml();
            }
            StringBuilder sb = new StringBuilder();
            for (UserDTO u : onlineUsers) {
                sb.append("- ").append(u.getNickname() != null ? u.getNickname() : u.getUsername())
                        .append(" (ID:").append(u.getId()).append(")").append("\n");
            }
            AiRequestContext.recordToolSuccess();
            return ToolResultWrapper.success("共" + onlineUsers.size() + "个用户在线", sb.toString())
                    .withSearchSuccessHint("在线用户").toXml();
        } catch (Exception e) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.error("查询在线用户失败: " + e.getMessage()).withErrorHint().toXml();
        }
    }

    @Tool("删除用户账号（危险操作）。参数: 用户名或用户ID")
    public String deleteUser(String param) {
        UserDTO user = findUser(param);
        if (user == null) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("未找到用户「" + param + "」").withSearchEmptyHint("用户").toXml();
        }
        PendingAction action = PendingAction.of("ultra_delete_user",
                "删除用户「" + user.getNickname() + "」(ID:" + user.getId() + ")")
                .param("targetUserId", String.valueOf(user.getId()));
        actionExecutor.savePendingAction(AiRequestContext.getSessionId(), action);
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.actionProposed("已提交删除用户「" + user.getNickname() + "」的确认请求（危险操作）", null)
                .withActionProposedHint("删除用户").toXml();
    }

    @Tool("封禁用户账号。参数: 用户名或用户ID")
    public String banUser(String param) {
        UserDTO user = findUser(param);
        if (user == null) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("未找到用户「" + param + "」").withSearchEmptyHint("用户").toXml();
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.error("用户「" + user.getNickname() + "」当前已是封禁状态").withErrorHint().toXml();
        }
        PendingAction action = PendingAction.of("ultra_ban_user",
                "封禁用户「" + user.getNickname() + "」(ID:" + user.getId() + ")")
                .param("targetUserId", String.valueOf(user.getId()));
        actionExecutor.savePendingAction(AiRequestContext.getSessionId(), action);
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.actionProposed("已提交封禁用户「" + user.getNickname() + "」的确认请求", null)
                .withActionProposedHint("封禁用户").toXml();
    }

    @Tool("解封用户账号。参数: 用户名或用户ID")
    public String unbanUser(String param) {
        UserDTO user = findUser(param);
        if (user == null) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("未找到用户「" + param + "」").withSearchEmptyHint("用户").toXml();
        }
        if (user.getStatus() != null && user.getStatus() == 1) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.error("用户「" + user.getNickname() + "」当前已是正常状态").withErrorHint().toXml();
        }
        PendingAction action = PendingAction.of("ultra_unban_user",
                "解封用户「" + user.getNickname() + "」(ID:" + user.getId() + ")")
                .param("targetUserId", String.valueOf(user.getId()));
        actionExecutor.savePendingAction(AiRequestContext.getSessionId(), action);
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.actionProposed("已提交解封用户「" + user.getNickname() + "」的确认请求", null)
                .withActionProposedHint("解封用户").toXml();
    }

    // ==================== 用户AI控制 (UltraUserControlAgent) ====================

    @Tool("禁用某用户的AI功能。参数格式: '用户名或ID|禁用原因'，用竖线分隔")
    public String disableUserAi(String input) {
        String[] parts = input.split("\\|", 2);
        String param = parts[0].trim();
        String reason = parts.length > 1 ? parts[1].trim() : "管理员通过Ultra AI禁用";
        UserDTO user = findUser(param);
        if (user == null) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("未找到用户「" + param + "」").withSearchEmptyHint("用户").toXml();
        }
        PendingAction action = PendingAction.of("ultra_disable_user_ai",
                "禁用用户「" + user.getNickname() + "」的AI功能")
                .param("targetUserId", String.valueOf(user.getId()))
                .param("disabledReason", reason);
        actionExecutor.savePendingAction(AiRequestContext.getSessionId(), action);
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.actionProposed("已提交禁用用户「" + user.getNickname() + "」AI功能的确认请求，原因: " + reason, null)
                .withActionProposedHint("禁用AI功能").toXml();
    }

    @Tool("启用某用户的AI功能。参数: 用户名或用户ID")
    public String enableUserAi(String param) {
        UserDTO user = findUser(param);
        if (user == null) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("未找到用户「" + param + "」").withSearchEmptyHint("用户").toXml();
        }
        AiUserAiConfig config = configMapper.selectByUserId(user.getId());
        if (config == null || config.getAiEnabled() == 1) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.error("用户「" + user.getNickname() + "」的AI功能当前已启用").withErrorHint().toXml();
        }
        PendingAction action = PendingAction.of("ultra_enable_user_ai",
                "启用用户「" + user.getNickname() + "」的AI功能")
                .param("targetUserId", String.valueOf(user.getId()));
        actionExecutor.savePendingAction(AiRequestContext.getSessionId(), action);
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.actionProposed("已提交启用用户「" + user.getNickname() + "」AI功能的确认请求", null)
                .withActionProposedHint("启用AI功能").toXml();
    }

    @Tool("查询某用户的AI状态和权限。参数: 用户名或用户ID")
    public String queryUserAiStatus(String param) {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        UserDTO user = findUser(param);
        if (user == null) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("未找到用户「" + param + "」").withSearchEmptyHint("用户").toXml();
        }
        AiUserAiConfig config = configMapper.selectByUserId(user.getId());
        StringBuilder sb = new StringBuilder();
        if (config == null || config.getAiEnabled() == 1) {
            sb.append("- 状态: 已启用\n");
            sb.append("- 每日查询上限: ").append(config != null ? config.getMaxDailyQueries() : 100).append("次\n");
        } else {
            sb.append("- 状态: 已禁用\n");
            sb.append("- 禁用原因: ").append(config.getDisabledReason() != null ? config.getDisabledReason() : "未记录").append("\n");
        }
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.success("已获取用户「" + user.getNickname() + "」AI状态", sb.toString())
                .withSearchSuccessHint("AI状态").toXml();
    }

    @Tool("列出所有被禁用AI功能的用户")
    public String listDisabledAiUsers() {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        List<AiUserAiConfig> disabledList = configMapper.selectDisabled();
        if (disabledList == null || disabledList.isEmpty()) {
            AiRequestContext.recordToolSuccess();
            return ToolResultWrapper.success("当前没有被禁用AI功能的用户", null)
                    .withSearchSuccessHint("AI禁用用户").toXml();
        }
        StringBuilder sb = new StringBuilder();
        for (AiUserAiConfig c : disabledList) {
            UserDTO user = userService.findById(c.getUserId());
            sb.append("- ").append(user != null ? user.getNickname() : "未知").append(" (ID:").append(c.getUserId()).append(")");
            if (c.getDisabledReason() != null) sb.append(", 原因: ").append(c.getDisabledReason());
            sb.append("\n");
        }
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.success("共" + disabledList.size() + "个用户被AI禁用", sb.toString())
                .withSearchSuccessHint("AI禁用用户").toXml();
    }

    // ==================== 跨用户操作 (UltraCrossUserAgent) ====================

    @Tool("查看某用户的购物车。参数: 用户名或用户ID")
    public String viewUserCart(String param) {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        UserDTO user = findUser(param);
        if (user == null) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("未找到用户「" + param + "」").withSearchEmptyHint("用户").toXml();
        }
        List<CartDTO> cart = productService.listCartItems(user.getId());
        if (cart == null || cart.isEmpty()) {
            AiRequestContext.recordToolSuccess();
            return ToolResultWrapper.success("用户「" + user.getNickname() + "」的购物车为空", null)
                    .withSearchSuccessHint("购物车").toXml();
        }
        StringBuilder sb = new StringBuilder();
        for (CartDTO c : cart) {
            sb.append("- ").append(c.getProductName() != null ? c.getProductName() : "ID:" + c.getProductId())
                    .append(" ×").append(c.getQuantity()).append("\n");
        }
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.success("用户「" + user.getNickname() + "」购物车共" + cart.size() + "件商品", sb.toString())
                .withSearchSuccessHint("购物车").toXml();
    }

    @Tool("给某用户购物车添加商品。参数格式: '用户名或ID|商品关键词'，用竖线分隔")
    public String crossAddToCart(String input) {
        String[] parts = input.split("\\|", 2);
        if (parts.length < 2) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.error("请提供用户和商品信息，格式: 用户名|商品名").withErrorHint().toXml();
        }
        UserDTO user = findUser(parts[0].trim());
        if (user == null) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("未找到用户「" + parts[0].trim() + "」").withSearchEmptyHint("用户").toXml();
        }
        String productKw = parts[1].trim();
        PageResult<ProductDTO> products = productService.listProducts(1, 5, null, productKw, 1);
        if (products == null || products.getList() == null || products.getList().isEmpty()) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("未找到商品「" + productKw + "」").withSearchEmptyHint("商品").toXml();
        }
        ProductDTO product = products.getList().get(0);
        PendingAction action = PendingAction.of("ultra_cross_add_cart",
                "为用户「" + user.getNickname() + "」添加「" + product.getName() + "」到购物车")
                .param("targetUserId", String.valueOf(user.getId()))
                .param("productId", String.valueOf(product.getId()))
                .param("quantity", "1");
        actionExecutor.savePendingAction(AiRequestContext.getSessionId(), action);
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.actionProposed("已提交为用户「" + user.getNickname() + "」添加「" + product.getName() + "」到购物车的确认请求", null)
                .withActionProposedHint("添加购物车").toXml();
    }

    @Tool("从某用户购物车移除商品。参数格式: '用户名或ID|商品关键词'")
    public String crossRemoveFromCart(String input) {
        String[] parts = input.split("\\|", 2);
        if (parts.length < 2) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.error("请提供用户和商品信息，格式: 用户名|商品名").withErrorHint().toXml();
        }
        UserDTO user = findUser(parts[0].trim());
        if (user == null) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("未找到用户「" + parts[0].trim() + "」").withSearchEmptyHint("用户").toXml();
        }
        List<CartDTO> cart = productService.listCartItems(user.getId());
        if (cart == null || cart.isEmpty()) {
            AiRequestContext.recordToolSuccess();
            return ToolResultWrapper.success("用户「" + user.getNickname() + "」购物车为空", null)
                    .withSearchSuccessHint("购物车").toXml();
        }
        String kw = parts[1].trim();
        CartDTO target = null;
        for (CartDTO c : cart) {
            if (c.getProductName() != null && c.getProductName().contains(kw)) { target = c; break; }
        }
        if (target == null) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("未在购物车中找到「" + kw + "」").withSearchEmptyHint("购物车商品").toXml();
        }
        PendingAction action = PendingAction.of("ultra_cross_remove_cart",
                "从用户「" + user.getNickname() + "」购物车移除「" + target.getProductName() + "」")
                .param("targetUserId", String.valueOf(user.getId()))
                .param("productId", String.valueOf(target.getProductId()));
        actionExecutor.savePendingAction(AiRequestContext.getSessionId(), action);
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.actionProposed("已提交从「" + user.getNickname() + "」购物车移除「" + target.getProductName() + "」的确认请求", null)
                .withActionProposedHint("移除购物车商品").toXml();
    }

    @Tool("清空某用户的购物车。参数: 用户名或用户ID")
    public String crossClearCart(String param) {
        UserDTO user = findUser(param);
        if (user == null) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("未找到用户「" + param + "」").withSearchEmptyHint("用户").toXml();
        }
        PendingAction action = PendingAction.of("ultra_cross_clear_cart",
                "清空用户「" + user.getNickname() + "」的购物车")
                .param("targetUserId", String.valueOf(user.getId()));
        actionExecutor.savePendingAction(AiRequestContext.getSessionId(), action);
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.actionProposed("已提交清空用户「" + user.getNickname() + "」购物车的确认请求", null)
                .withActionProposedHint("清空购物车").toXml();
    }

    // ==================== 用户分析 (UltraAnalyticsAgent) ====================

    @Tool("查询某用户的AI画像和偏好。参数: 用户名或用户ID")
    public String queryUserProfile(String param) {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        UserDTO user = findUser(param);
        if (user == null) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("未找到用户「" + param + "」").withSearchEmptyHint("用户").toXml();
        }
        String profile = userMemoryService.buildUserProfile(user.getId());
        if (profile == null || profile.isEmpty()) {
            AiRequestContext.recordToolSuccess();
            return ToolResultWrapper.success("用户「" + user.getNickname() + "」暂无画像数据", null)
                    .withSearchSuccessHint("用户画像").toXml();
        }
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.success("已获取用户「" + user.getNickname() + "」的画像", profile)
                .withSearchSuccessHint("用户画像").toXml();
    }

    @Tool("查询某用户的所有长期记忆。参数: 用户名或用户ID")
    public String queryUserMemories(String param) {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        UserDTO user = findUser(param);
        if (user == null) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("未找到用户「" + param + "」").withSearchEmptyHint("用户").toXml();
        }
        List<AiUserMemory> memories = userMemoryMapper.selectByUserId(user.getId());
        if (memories == null || memories.isEmpty()) {
            AiRequestContext.recordToolSuccess();
            return ToolResultWrapper.success("用户「" + user.getNickname() + "」暂无长期记忆数据", null)
                    .withSearchSuccessHint("用户记忆").toXml();
        }
        StringBuilder sb = new StringBuilder();
        appendMemoryGroup(sb, memories, "preference", "偏好");
        appendMemoryGroup(sb, memories, "interest", "兴趣");
        appendMemoryGroup(sb, memories, "fact", "已知事实");
        appendMemoryGroup(sb, memories, "decision", "重要决策");
        appendMemoryGroup(sb, memories, "lesson", "经验教训");
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.success("用户「" + user.getNickname() + "」共" + memories.size() + "条记忆", sb.toString())
                .withSearchSuccessHint("用户记忆").toXml();
    }

    @Tool("查询某用户的详细账号信息。参数: 用户名或用户ID")
    public String queryUserDetail(String param) {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        UserDTO user = findUser(param);
        if (user == null) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("未找到用户「" + param + "」").withSearchEmptyHint("用户").toXml();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("- 昵称: ").append(user.getNickname() != null ? user.getNickname() : "未设置").append("\n");
        sb.append("- ID: ").append(user.getId()).append("\n");
        sb.append("- 用户名: ").append(user.getUsername() != null ? user.getUsername() : "未知").append("\n");
        sb.append("- 状态: ").append(user.getStatus() != null && user.getStatus() == 1 ? "正常" : "禁用").append("\n");
        if (user.getCreateTime() != null) sb.append("- 注册时间: ").append(user.getCreateTime()).append("\n");
        String profile = userMemoryService.buildUserProfile(user.getId());
        if (profile != null && !profile.isEmpty()) sb.append("\n--- AI画像 ---\n").append(profile);
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.success("已获取用户「" + user.getNickname() + "」详细信息", sb.toString())
                .withSearchSuccessHint("用户详情").toXml();
    }

    // ==================== 安全审计 (UltraSecurityAgent) ====================

    @Tool("执行安全巡检，检测可疑行为和失败操作")
    public String securityCheck() {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        StringBuilder sb = new StringBuilder();
        int todayOps = systemService.countTodayOps();
        sb.append("今日操作总数: ").append(todayOps).append("\n\n");
        List<SysOperationLogDTO> failedOps = systemService.listFailedOps(24, 10);
        sb.append("最近24小时失败操作 (").append(failedOps.size()).append("条):\n");
        if (failedOps.isEmpty()) {
            sb.append("  无失败操作，系统运行正常\n");
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm");
            for (SysOperationLogDTO op : failedOps) {
                sb.append("  - [").append(op.getOperationTime() != null ? sdf.format(op.getOperationTime()) : "").append("] ");
                sb.append(op.getUsername() != null ? op.getUsername() : "未知").append(" ");
                sb.append(op.getOperation() != null ? op.getOperation() : "").append("\n");
            }
        }
        sb.append("\n安全建议:\n");
        if (failedOps.size() >= 5) sb.append("  - 失败操作较多，建议排查异常访问\n");
        if (todayOps > 500) sb.append("  - 今日操作量较大，建议关注系统负载\n");
        if (failedOps.isEmpty() && todayOps <= 500) sb.append("  - 系统运行正常\n");
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.success("安全巡检完成", sb.toString())
                .withSearchSuccessHint("安全巡检").toXml();
    }

    @Tool("查看今日操作概况和操作日志")
    public String opsOverview() {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        int total = systemService.countTodayOps();
        List<SysOperationLogDTO> failedOps = systemService.listFailedOps(24, 5);
        StringBuilder sb = new StringBuilder();
        sb.append("操作总数: ").append(total).append("\n");
        sb.append("失败操作: ").append(failedOps.size()).append(" 条\n");
        if (!failedOps.isEmpty()) {
            sb.append("\n最近失败操作:\n");
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
            for (SysOperationLogDTO op : failedOps) {
                sb.append("- [").append(op.getOperationTime() != null ? sdf.format(op.getOperationTime()) : "").append("] ");
                sb.append(op.getUsername() != null ? op.getUsername() : "").append(" ");
                sb.append(op.getOperation() != null ? op.getOperation() : "").append("\n");
            }
        }
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.success("今日操作概况", sb.toString())
                .withSearchSuccessHint("操作日志").toXml();
    }

    @Tool("查看某用户的操作记录。参数: 用户名或用户ID")
    public String queryUserOps(String param) {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        UserDTO user = findUser(param);
        if (user == null) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("未找到用户「" + param + "」").withSearchEmptyHint("用户").toXml();
        }
        List<SysOperationLogDTO> ops = systemService.listUserRecentOps(user.getId(), 20);
        if (ops == null || ops.isEmpty()) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("用户「" + user.getNickname() + "」暂无操作记录")
                    .withSearchEmptyHint("操作记录").toXml();
        }
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm");
        for (SysOperationLogDTO op : ops) {
            sb.append("- [").append(op.getOperationTime() != null ? sdf.format(op.getOperationTime()) : "").append("] ");
            sb.append(op.getModule() != null ? "[" + op.getModule() + "] " : "");
            sb.append(op.getOperation() != null ? op.getOperation() : "");
            sb.append(op.getStatus() != null && op.getStatus() == 1 ? " 失败" : "").append("\n");
        }
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.success("用户「" + user.getNickname() + "」共" + ops.size() + "条操作记录", sb.toString())
                .withSearchSuccessHint("操作记录").toXml();
    }

    // ==================== 用户行为追踪 (UltraBrowseHistoryAgent) ====================

    @Tool("查看某用户最近的浏览记录。参数: 用户名或用户ID")
    public String trackUserRecent(String param) {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        UserDTO user = findUser(param);
        if (user == null) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("未找到用户「" + param + "」").withSearchEmptyHint("用户").toXml();
        }
        List<BrowseHistoryDTO> records = browseHistoryService.listRecent(user.getId(), 7);
        if (records == null || records.isEmpty()) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("用户「" + user.getNickname() + "」最近7天无浏览记录")
                    .withSearchEmptyHint("浏览记录").toXml();
        }
        Map<String, List<BrowseHistoryDTO>> grouped = new LinkedHashMap<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        for (BrowseHistoryDTO r : records)
            grouped.computeIfAbsent(r.getBrowseDate() != null ? sdf.format(r.getBrowseDate()) : "未知", k -> new java.util.ArrayList<>()).add(r);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<BrowseHistoryDTO>> e : grouped.entrySet()) {
            sb.append(e.getKey()).append(" (").append(e.getValue().size()).append("条)\n");
            formatBrowseRecords(sb, e.getValue());
            sb.append("\n");
        }
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.success("用户「" + user.getNickname() + "」最近7天共" + records.size() + "条浏览记录", sb.toString())
                .withSearchSuccessHint("浏览记录").toXml();
    }

    @Tool("查看某用户某天的浏览记录。参数格式: '用户名或ID|日期(yyyy-MM-dd或昨天/前天)'")
    public String trackUserByDate(String input) {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        String[] parts = input.split("\\|", 2);
        UserDTO user = findUser(parts[0].trim());
        if (user == null) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("未找到用户「" + parts[0].trim() + "」").withSearchEmptyHint("用户").toXml();
        }
        String date = parts.length > 1 ? resolveDate(parts[1].trim()) : LocalDate.now().toString();
        List<BrowseHistoryDTO> records = browseHistoryService.listByDate(user.getId(), date);
        if (records == null || records.isEmpty()) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("用户「" + user.getNickname() + "」在 " + date + " 无浏览记录")
                    .withSearchEmptyHint("浏览记录").toXml();
        }
        StringBuilder sb = new StringBuilder();
        formatBrowseRecords(sb, records);
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.success("用户「" + user.getNickname() + "」" + date + "共" + records.size() + "条浏览", sb.toString())
                .withSearchSuccessHint("浏览记录").toXml();
    }

    @Tool("查看某用户对某类内容的浏览记录。参数格式: '用户名或ID|类型(如商品/非遗/活动)'")
    public String trackUserByType(String input) {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        String[] parts = input.split("\\|", 2);
        UserDTO user = findUser(parts[0].trim());
        if (user == null) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("未找到用户「" + parts[0].trim() + "」").withSearchEmptyHint("用户").toXml();
        }
        String typeKw = parts.length > 1 ? parts[1].trim() : "非遗";
        String targetType = "ich_item";
        for (Map.Entry<String, String> e : TYPE_MAP.entrySet())
            if (typeKw.contains(e.getKey())) { targetType = e.getValue(); break; }
        String label = TYPE_LABELS.getOrDefault(targetType, targetType);
        List<BrowseHistoryDTO> records = browseHistoryService.listByType(user.getId(), targetType, 20);
        if (records == null || records.isEmpty()) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("用户「" + user.getNickname() + "」没有「" + label + "」浏览记录")
                    .withSearchEmptyHint("浏览记录").toXml();
        }
        StringBuilder sb = new StringBuilder();
        formatBrowseRecords(sb, records);
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.success("用户「" + user.getNickname() + "」的「" + label + "」共" + records.size() + "条记录", sb.toString())
                .withSearchSuccessHint("浏览记录").toXml();
    }

    // ==================== Skill 管理 (UltraSkillControlAgent) ====================

    @Tool("查看所有Skill技能的列表和启用状态")
    public String listAllSkills() {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        try {
            var skills = skillConfigService.getAllSkills();
            if (skills == null || skills.isEmpty()) {
                AiRequestContext.recordToolFailure();
                return ToolResultWrapper.empty("暂无Skill配置").withSearchEmptyHint("Skill").toXml();
            }
            StringBuilder sb = new StringBuilder();
            for (var s : skills) {
                sb.append("- ID:").append(s.getSkillId()).append(" ").append(s.getSkillName());
                sb.append(s.getEnabled() != null && s.getEnabled() == 1 ? " [启用]" : " [禁用]");
                sb.append("\n");
            }
            AiRequestContext.recordToolSuccess();
            return ToolResultWrapper.success("共" + skills.size() + "个Skill", sb.toString())
                    .withSearchSuccessHint("Skill列表").toXml();
        } catch (Exception e) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.error("查询Skill列表失败: " + e.getMessage()).withErrorHint().toXml();
        }
    }

    @Tool("启用指定Skill技能。参数: Skill ID")
    public String enableSkill(String skillId) {
        try {
            skillConfigService.enableSkill(skillId.trim());
            AiRequestContext.recordToolSuccess();
            return ToolResultWrapper.success("已启用 Skill ID:" + skillId, null)
                    .withSearchSuccessHint("Skill").toXml();
        } catch (Exception e) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.error("启用Skill失败: " + e.getMessage()).withErrorHint().toXml();
        }
    }

    @Tool("禁用指定Skill技能。参数: Skill ID")
    public String disableSkill(String skillId) {
        try {
            skillConfigService.disableSkill(skillId.trim());
            AiRequestContext.recordToolSuccess();
            return ToolResultWrapper.success("已禁用 Skill ID:" + skillId, null)
                    .withSearchSuccessHint("Skill").toXml();
        } catch (Exception e) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.error("禁用Skill失败: " + e.getMessage()).withErrorHint().toXml();
        }
    }

    // ==================== 辅助方法 ====================

    private UserDTO findUser(String param) {
        if (param == null || param.isEmpty()) return null;
        try { return userService.findById(Long.parseLong(param.trim())); } catch (NumberFormatException ignored) {}
        try { return userService.findByNickname(param.trim()); } catch (Exception ignored) {}
        return null;
    }

    private void appendMemoryGroup(StringBuilder sb, List<AiUserMemory> memories, String type, String label) {
        boolean hasContent = false;
        for (AiUserMemory m : memories) {
            if (type.equals(m.getMemoryType())) {
                if (!hasContent) { sb.append("### ").append(label).append("\n"); hasContent = true; }
                sb.append("- ").append(m.getMemoryValue()).append("\n");
            }
        }
        if (hasContent) sb.append("\n");
    }

    private void formatBrowseRecords(StringBuilder sb, List<BrowseHistoryDTO> records) {
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm");
        for (BrowseHistoryDTO r : records) {
            String label = TYPE_LABELS.getOrDefault(r.getTargetType(), r.getTargetType());
            String time = r.getBrowseTime() != null ? timeFmt.format(r.getBrowseTime()) : "";
            sb.append("  - [").append(time).append("] [").append(label).append("] ")
                    .append(r.getTargetTitle() != null ? r.getTargetTitle() : "ID:" + r.getTargetId()).append("\n");
        }
    }

    private String resolveDate(String param) {
        if (param != null && param.matches("\\d{4}-\\d{2}-\\d{2}")) return param;
        if (param != null) {
            if (param.contains("昨天")) return LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
            if (param.contains("前天")) return LocalDate.now().minusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        return LocalDate.now().toString();
    }
}

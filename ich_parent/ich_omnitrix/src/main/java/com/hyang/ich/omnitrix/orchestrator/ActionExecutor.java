package com.hyang.ich.omnitrix.orchestrator;

import com.hyang.ich.content.ContentService;
import com.hyang.ich.content.dto.IchActivityCommentDTO;
import com.hyang.ich.content.dto.IchActivityDTO;
import com.hyang.ich.content.dto.IchActivityRecordDTO;
import com.hyang.ich.content.dto.IchPostCommentDTO;
import com.hyang.ich.omnitrix.dto.PendingAction;
import com.hyang.ich.order.OrderService;
import com.hyang.ich.order.dto.OrderCreateDTO;
import com.hyang.ich.order.dto.OrderDTO;
import com.hyang.ich.omnitrix.entity.AiUserAiConfig;
import com.hyang.ich.omnitrix.mapper.AiUserAiConfigMapper;
import com.hyang.ich.omnitrix.service.SystemMemoryService;
import com.hyang.ich.product.ProductService;
import com.hyang.ich.product.dto.ProductDTO;
import com.hyang.ich.system.SystemService;
import com.hyang.ich.user.UserService;
import com.hyang.ich.user.dto.UserAddressDTO;
import com.hyang.ich.user.dto.UserDTO;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 行动执行器 —— 执行用户确认的写操作，并将待确认操作存/取 Redis
 */
@Slf4j
@Component
public class ActionExecutor {

    private static final String PENDING_ACTION_KEY_PREFIX = "omnitrix:pending_action:";
    private static final int PENDING_ACTION_TTL_MINUTES = 5;

    private final ProductService productService;
    private final OrderService orderService;
    private final ContentService contentService;
    private final UserService userService;
    private final SystemService systemService;
    private final AiUserAiConfigMapper aiConfigMapper;
    private final SystemMemoryService systemMemoryService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ActionExecutor(ProductService productService,
                          OrderService orderService,
                          ContentService contentService,
                          UserService userService,
                          SystemService systemService,
                          AiUserAiConfigMapper aiConfigMapper,
                          SystemMemoryService systemMemoryService,
                          StringRedisTemplate redisTemplate) {
        this.productService = productService;
        this.orderService = orderService;
        this.contentService = contentService;
        this.userService = userService;
        this.systemService = systemService;
        this.aiConfigMapper = aiConfigMapper;
        this.systemMemoryService = systemMemoryService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    // ========== Redis 存取 ==========

    public void savePendingAction(String sessionId, PendingAction action) {
        try {
            String json = objectMapper.writeValueAsString(action);
            redisTemplate.opsForValue().set(
                    PENDING_ACTION_KEY_PREFIX + sessionId, json,
                    PENDING_ACTION_TTL_MINUTES, TimeUnit.MINUTES);
            log.debug("保存待确认操作: sessionId={}, type={}", sessionId, action.getActionType());
        } catch (Exception e) {
            log.warn("保存待确认操作失败: {}", e.getMessage());
        }
    }

    public PendingAction getPendingAction(String sessionId) {
        try {
            String json = redisTemplate.opsForValue().get(PENDING_ACTION_KEY_PREFIX + sessionId);
            if (json == null) return null;
            PendingAction action = objectMapper.readValue(json, PendingAction.class);
            if (action.isExpired()) {
                clearPendingAction(sessionId);
                return null;
            }
            return action;
        } catch (Exception e) {
            log.debug("获取待确认操作失败: {}", e.getMessage());
            return null;
        }
    }

    public void clearPendingAction(String sessionId) {
        try {
            redisTemplate.delete(PENDING_ACTION_KEY_PREFIX + sessionId);
        } catch (Exception e) {
            log.debug("清除待确认操作失败: {}", e.getMessage());
        }
    }

    // ========== 确认关键词检测 ==========

    private static final String[] CONFIRM_KEYWORDS = {
            "好的", "确认", "是的", "可以", "行", "嗯", "对", "好", "加吧", "报名吧",
            "买吧", "下单吧", "确定", "没问题", "ok", "yes", "sure", "帮我加", "帮我报",
            "就这个", "要的", "收到", "同意"
    };

    private static final String[] REJECT_KEYWORDS = {
            "不要", "取消", "算了", "不用了", "不了", "别", "不行", "no", "cancel", "不想", "不做", "暂时不"
    };

    /**
     * 判断用户消息是否为确认
     * @return 1=确认, -1=拒绝, 0=无关
     */
    public int detectConfirmation(String userMessage) {
        if (userMessage == null) return 0;
        String msg = userMessage.trim().toLowerCase();
        // 短消息更可能是对上文的确认/拒绝
        if (msg.length() > 30) return 0;
        for (String kw : REJECT_KEYWORDS) {
            if (msg.contains(kw)) return -1;
        }
        for (String kw : CONFIRM_KEYWORDS) {
            if (msg.contains(kw)) return 1;
        }
        return 0;
    }

    // ========== 执行操作 ==========

    public String execute(PendingAction action, Long userId) {
        try {
            switch (action.getActionType()) {
                case "add_to_cart":
                    return doAddToCart(action, userId);
                case "remove_from_cart":
                    return doRemoveFromCart(action, userId);
                case "cancel_order":
                    return doCancelOrder(action, userId);
                case "confirm_receive":
                    return doConfirmReceive(action, userId);
                case "register_activity":
                    return doRegisterActivity(action, userId);
                case "like_post":
                    return doLikePost(action, userId);
                case "favorite_post":
                    return doFavoritePost(action, userId);
                case "unlike_post":
                    return doUnlikePost(action, userId);
                case "unfavorite_post":
                    return doUnfavoritePost(action, userId);
                case "comment_activity":
                    return doCommentActivity(action, userId);
                case "comment_post":
                    return doCommentPost(action, userId);
                case "clear_cart":
                    return doClearCart(userId);
                case "update_cart_quantity":
                    return doUpdateCartQuantity(action, userId);
                case "create_order":
                    return doCreateOrder(action, userId);
                case "pay_order":
                    return doPayOrder(action, userId);
                case "set_default_address":
                    return doSetDefaultAddress(action, userId);
                case "delete_address":
                    return doDeleteAddress(action, userId);
                // ---------- 管理员操作 ----------
                case "approve_activity":
                    return doApproveActivity(action, userId);
                case "reject_activity":
                    return doRejectActivity(action, userId);
                case "ship_order":
                    return doShipOrder(action);
                case "publish_notification":
                    return doPublishNotification(action);
                case "update_activity_status":
                    return doUpdateActivityStatus(action);
                // ---------- Ultra 操作 ----------
                case "ultra_disable_user_ai":
                    return doUltraDisableUserAi(action, userId);
                case "ultra_enable_user_ai":
                    return doUltraEnableUserAi(action, userId);
                case "ultra_cross_add_cart":
                    return doUltraCrossAddCart(action);
                case "ultra_cross_remove_cart":
                    return doUltraCrossRemoveCart(action);
                case "ultra_cross_clear_cart":
                    return doUltraCrossClearCart(action);
                case "ultra_delete_user":
                    return doUltraDeleteUser(action, userId);
                case "ultra_ban_user":
                    return doUltraBanUser(action);
                case "ultra_unban_user":
                    return doUltraUnbanUser(action);
                default:
                    return "[操作失败] 不支持的操作类型: " + action.getActionType();
            }
        } catch (Exception e) {
            log.error("执行操作失败: type={}, error={}", action.getActionType(), e.getMessage(), e);
            return "[操作失败] " + e.getMessage();
        }
    }

    // ---------- 商城操作 ----------

    private String doAddToCart(PendingAction action, Long userId) {
        Long productId = action.getParamAsLong("productId");
        Integer quantity = action.getParamAsInt("quantity");
        if (quantity == null) quantity = 1;
        productService.addToCart(userId, productId, quantity);
        ProductDTO product = productService.getProductById(productId);
        String name = product != null ? product.getName() : "商品ID:" + productId;
        return "[操作成功] 已将「" + name + "」×" + quantity + " 加入购物车";
    }

    private String doRemoveFromCart(PendingAction action, Long userId) {
        Long productId = action.getParamAsLong("productId");
        productService.removeFromCart(userId, productId);
        return "[操作成功] 已从购物车移除该商品";
    }

    private String doClearCart(Long userId) {
        productService.clearCart(userId);
        return "[操作成功] 已清空购物车";
    }

    private String doUpdateCartQuantity(PendingAction action, Long userId) {
        Long productId = action.getParamAsLong("productId");
        Integer quantity = action.getParamAsInt("quantity");
        if (quantity == null || quantity < 1) quantity = 1;
        productService.updateCartQuantity(userId, productId, quantity);
        return "[操作成功] 已将购物车商品数量修改为" + quantity;
    }

    private String doCreateOrder(PendingAction action, Long userId) {
        String productIds = action.getParam("productIds");
        String quantities = action.getParam("quantities");
        if (productIds == null || productIds.isEmpty()) {
            return "[操作失败] 未找到要下单的商品";
        }
        // 获取默认地址
        List<UserAddressDTO> addresses = userService.listAddresses(userId);
        Long addressId = null;
        if (addresses != null) {
            for (UserAddressDTO addr : addresses) {
                if (addr.getIsDefault() != null && addr.getIsDefault() == 1) {
                    addressId = addr.getId();
                    break;
                }
            }
            if (addressId == null && !addresses.isEmpty()) {
                addressId = addresses.get(0).getId();
            }
        }
        if (addressId == null) {
            return "[操作失败] 您还没有收货地址，请先添加收货地址";
        }
        OrderCreateDTO createDTO = new OrderCreateDTO();
        createDTO.setUserId(userId);
        createDTO.setAddressId(addressId);
        createDTO.setNote("AI助手下单");
        String[] ids = productIds.split(",");
        String[] qtys = quantities.split(",");
        List<OrderCreateDTO.OrderItemCreateDTO> items = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            OrderCreateDTO.OrderItemCreateDTO item = new OrderCreateDTO.OrderItemCreateDTO();
            item.setProductId(Long.parseLong(ids[i].trim()));
            item.setQuantity(i < qtys.length ? Integer.parseInt(qtys[i].trim()) : 1);
            items.add(item);
        }
        createDTO.setItems(items);
        OrderDTO order = orderService.createOrder(createDTO);
        if (order == null) {
            return "[操作失败] 创建订单失败，可能库存不足";
        }
        return "[操作成功] 订单已创建，订单号: " + order.getOrderNo() + "，请前往订单页面完成支付";
    }

    private String doPayOrder(PendingAction action, Long userId) {
        String orderNo = action.getParam("orderNo");
        orderService.payOrder(orderNo, 1);
        return "[操作成功] 订单 " + orderNo + " 已支付成功";
    }

    private String doCancelOrder(PendingAction action, Long userId) {
        String orderNo = action.getParam("orderNo");
        orderService.cancelOrder(orderNo, userId);
        return "[操作成功] 订单 " + orderNo + " 已取消";
    }

    private String doConfirmReceive(PendingAction action, Long userId) {
        String orderNo = action.getParam("orderNo");
        orderService.confirmReceive(orderNo, userId);
        return "[操作成功] 订单 " + orderNo + " 已确认收货";
    }

    // ---------- 内容操作 ----------

    private String doRegisterActivity(PendingAction action, Long userId) {
        Long activityId = action.getParamAsLong("activityId");
        UserDTO user = userService.findById(userId);

        IchActivityRecordDTO record = new IchActivityRecordDTO();
        record.setActivityId(activityId);
        record.setUserId(userId);
        record.setUserName(user != null ? user.getNickname() : String.valueOf(userId));
        record.setUserPhone(user != null ? user.getPhone() : "");
        record.setStatus(1);

        IchActivityRecordDTO result = contentService.registerActivity(record);
        if (result == null) {
            return "[操作失败] 活动报名人数已满或报名已截止";
        }

        IchActivityDTO activity = contentService.getActivityById(activityId);
        String activityName = activity != null ? activity.getName() : "活动ID:" + activityId;
        return "[操作成功] 已成功报名参加活动「" + activityName + "」";
    }

    private String doLikePost(PendingAction action, Long userId) {
        Long postId = action.getParamAsLong("postId");
        contentService.likePost(postId, userId);
        return "[操作成功] 已点赞";
    }

    private String doUnlikePost(PendingAction action, Long userId) {
        Long postId = action.getParamAsLong("postId");
        contentService.unlikePost(postId, userId);
        return "[操作成功] 已取消点赞";
    }

    private String doFavoritePost(PendingAction action, Long userId) {
        Long postId = action.getParamAsLong("postId");
        contentService.favoritePost(postId, userId);
        return "[操作成功] 已收藏";
    }

    private String doUnfavoritePost(PendingAction action, Long userId) {
        Long postId = action.getParamAsLong("postId");
        contentService.unfavoritePost(postId, userId);
        return "[操作成功] 已取消收藏";
    }

    private String doCommentActivity(PendingAction action, Long userId) {
        Long activityId = action.getParamAsLong("activityId");
        String comment = action.getParam("comment");
        UserDTO user = userService.findById(userId);
        IchActivityCommentDTO commentDTO = new IchActivityCommentDTO();
        commentDTO.setActivityId(activityId);
        commentDTO.setUserId(userId);
        commentDTO.setUserName(user != null ? user.getNickname() : String.valueOf(userId));
        commentDTO.setContent(comment != null ? comment : "好活动！");
        commentDTO.setStatus(1);
        contentService.addActivityComment(commentDTO);
        return "[操作成功] 已发表活动评论";
    }

    private String doCommentPost(PendingAction action, Long userId) {
        Long postId = action.getParamAsLong("postId");
        String comment = action.getParam("comment");
        UserDTO user = userService.findById(userId);
        IchPostCommentDTO commentDTO = new IchPostCommentDTO();
        commentDTO.setPostId(postId);
        commentDTO.setUserId(userId);
        commentDTO.setUserName(user != null ? user.getNickname() : String.valueOf(userId));
        commentDTO.setContent(comment != null ? comment : "好动态！");
        commentDTO.setStatus(1);
        contentService.addPostComment(commentDTO);
        return "[操作成功] 已发表动态评论";
    }

    // ---------- 用户操作 ----------

    private String doSetDefaultAddress(PendingAction action, Long userId) {
        Long addressId = action.getParamAsLong("addressId");
        userService.setDefaultAddress(userId, addressId);
        return "[操作成功] 已设置为默认收货地址";
    }

    private String doDeleteAddress(PendingAction action, Long userId) {
        Long addressId = action.getParamAsLong("addressId");
        userService.deleteAddress(userId, addressId);
        return "[操作成功] 已删除该收货地址";
    }

    // ---------- 管理员操作 ----------

    private String doApproveActivity(PendingAction action, Long adminId) {
        Long activityId = action.getParamAsLong("activityId");
        contentService.updateActivityApprovalStatus(activityId, 1, null, adminId);
        return "[操作成功] 活动已审批通过";
    }

    private String doRejectActivity(PendingAction action, Long adminId) {
        Long activityId = action.getParamAsLong("activityId");
        String reason = action.getParam("rejectReason");
        contentService.updateActivityApprovalStatus(activityId, 2, reason, adminId);
        return "[操作成功] 活动已驳回，原因: " + reason;
    }

    private String doShipOrder(PendingAction action) {
        String orderNo = action.getParam("orderNo");
        orderService.shipOrder(orderNo);
        return "[操作成功] 订单 " + orderNo + " 已发货";
    }

    private String doPublishNotification(PendingAction action) {
        Long notificationId = action.getParamAsLong("notificationId");
        systemService.publishNotification(notificationId);
        return "[操作成功] 通知已发布";
    }

    private String doUpdateActivityStatus(PendingAction action) {
        Long activityId = action.getParamAsLong("activityId");
        Integer status = action.getParamAsInt("status");
        contentService.updateActivityStatus(activityId, status != null ? status : 1);
        return "[操作成功] 活动状态已更新";
    }

    // ---------- Ultra 操作 ----------

    private String doUltraDisableUserAi(PendingAction action, Long adminId) {
        Long targetUserId = action.getParamAsLong("targetUserId");
        String reason = action.getParam("disabledReason");
        AiUserAiConfig config = aiConfigMapper.selectByUserId(targetUserId);
        if (config == null) {
            config = new AiUserAiConfig();
            config.setUserId(targetUserId);
            config.setAiEnabled(0);
            config.setDisabledReason(reason);
            config.setDisabledBy(adminId);
            config.setMaxDailyQueries(100);
            aiConfigMapper.insert(config);
        } else {
            aiConfigMapper.updateAiEnabled(targetUserId, 0, reason, adminId);
        }
        // 记录到系统记忆
        UserDTO user = userService.findById(targetUserId);
        String userName = user != null ? user.getNickname() : "ID:" + targetUserId;
        systemMemoryService.saveDirective("disable_ai_" + targetUserId,
                "已禁用用户「" + userName + "」的AI功能，原因: " + reason, String.valueOf(adminId));
        return "[操作成功] 已禁用用户「" + userName + "」的AI功能";
    }

    private String doUltraEnableUserAi(PendingAction action, Long adminId) {
        Long targetUserId = action.getParamAsLong("targetUserId");
        aiConfigMapper.updateAiEnabled(targetUserId, 1, null, adminId);
        UserDTO user = userService.findById(targetUserId);
        String userName = user != null ? user.getNickname() : "ID:" + targetUserId;
        // 清除系统记忆中的禁用指令
        systemMemoryService.saveDirective("disable_ai_" + targetUserId,
                "已恢复用户「" + userName + "」的AI功能", String.valueOf(adminId));
        return "[操作成功] 已恢复用户「" + userName + "」的AI功能";
    }

    private String doUltraCrossAddCart(PendingAction action) {
        Long targetUserId = action.getParamAsLong("targetUserId");
        Long productId = action.getParamAsLong("productId");
        Integer quantity = action.getParamAsInt("quantity");
        if (quantity == null) quantity = 1;
        productService.addToCart(targetUserId, productId, quantity);
        ProductDTO product = productService.getProductById(productId);
        String name = product != null ? product.getName() : "商品ID:" + productId;
        UserDTO user = userService.findById(targetUserId);
        String userName = user != null ? user.getNickname() : "ID:" + targetUserId;
        return "[操作成功] 已为用户「" + userName + "」的购物车添加「" + name + "」×" + quantity;
    }

    private String doUltraCrossRemoveCart(PendingAction action) {
        Long targetUserId = action.getParamAsLong("targetUserId");
        Long productId = action.getParamAsLong("productId");
        productService.removeFromCart(targetUserId, productId);
        return "[操作成功] 已从目标用户购物车移除该商品";
    }

    private String doUltraCrossClearCart(PendingAction action) {
        Long targetUserId = action.getParamAsLong("targetUserId");
        productService.clearCart(targetUserId);
        UserDTO user = userService.findById(targetUserId);
        String userName = user != null ? user.getNickname() : "ID:" + targetUserId;
        return "[操作成功] 已清空用户「" + userName + "」的购物车";
    }

    private String doUltraDeleteUser(PendingAction action, Long adminId) {
        Long targetUserId = action.getParamAsLong("targetUserId");
        UserDTO user = userService.findById(targetUserId);
        String userName = user != null ? user.getNickname() : "ID:" + targetUserId;
        userService.deleteUser(targetUserId);
        systemMemoryService.saveDirective("delete_user_" + targetUserId,
                "管理员已删除用户「" + userName + "」", String.valueOf(adminId));
        return "[操作成功] 已删除用户「" + userName + "」";
    }

    private String doUltraBanUser(PendingAction action) {
        Long targetUserId = action.getParamAsLong("targetUserId");
        userService.updateUserStatus(targetUserId, 0);
        UserDTO user = null;
        try { user = userService.findById(targetUserId); } catch (Exception ignored) {}
        String userName = user != null ? user.getNickname() : "ID:" + targetUserId;
        return "[操作成功] 已封禁用户「" + userName + "」";
    }

    private String doUltraUnbanUser(PendingAction action) {
        Long targetUserId = action.getParamAsLong("targetUserId");
        userService.updateUserStatus(targetUserId, 1);
        UserDTO user = userService.findById(targetUserId);
        String userName = user != null ? user.getNickname() : "ID:" + targetUserId;
        return "[操作成功] 已解封用户「" + userName + "」";
    }
}

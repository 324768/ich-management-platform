package com.hyang.ich.omnitrix.agent.impl;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.omnitrix.agent.AgentContext;
import com.hyang.ich.omnitrix.agent.SubAgent;
import com.hyang.ich.omnitrix.agent.tool.AgentTool;
import com.hyang.ich.omnitrix.agent.tool.ToolCallResult;
import com.hyang.ich.omnitrix.agent.tool.ToolSelector;
import com.hyang.ich.omnitrix.dto.AgentQueryResult;
import com.hyang.ich.omnitrix.dto.PendingAction;
import com.hyang.ich.product.ProductService;
import com.hyang.ich.product.dto.CartDTO;
import com.hyang.ich.product.dto.ProductDTO;
import com.hyang.ich.user.UserService;
import com.hyang.ich.user.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Ultra 跨用户代理 —— 以管理员身份代替指定用户执行操作
 */
@Slf4j
@Component
public class UltraCrossUserAgent implements SubAgent {

    private final UserService userService;
    private final ProductService productService;
    private final ToolSelector toolSelector;

    private static final List<AgentTool> TOOLS = Arrays.asList(
            AgentTool.of("cross_add_cart", "给某用户购物车添加商品", "用户名+商品名"),
            AgentTool.of("cross_remove_cart", "从某用户购物车移除商品", "用户名+商品名"),
            AgentTool.of("cross_view_cart", "查看某用户的购物车", "用户名或用户ID"),
            AgentTool.of("cross_clear_cart", "清空某用户的购物车", "用户名或用户ID")
    );

    public UltraCrossUserAgent(UserService userService, ProductService productService,
                                ToolSelector toolSelector) {
        this.userService = userService;
        this.productService = productService;
        this.toolSelector = toolSelector;
    }

    @Override
    public String getCode() { return "ultra_cross_user"; }

    @Override
    public String getName() { return "跨用户操作"; }

    @Override
    public String getDescription() { return "代替指定用户执行操作（购物车/订单等），Ultra专用"; }

    @Override
    public String getAgentPrompt() {
        return "## 当前任务模式: Ultra 跨用户操作\n" +
                "你现在拥有最高权限，可以代替任何用户执行操作。\n" +
                "- 需要先确认目标用户和目标商品/操作\n" +
                "- 所有写操作都需要管理员确认\n" +
                "- 明确展示操作对象和操作内容";
    }

    @Override
    public AgentQueryResult execute(String userQuery, AgentContext context) {
        try {
            ToolCallResult toolCall = toolSelector.select(userQuery, TOOLS);
            String selectedTool = toolCall.isNone() ? fallbackTool(userQuery) : toolCall.getToolName();
            String param = toolCall.getParameter() != null && !toolCall.getParameter().isEmpty()
                    ? toolCall.getParameter() : userQuery;

            switch (selectedTool) {
                case "cross_add_cart":
                    return proposeCrossAddCart(param, userQuery, context);
                case "cross_remove_cart":
                    return proposeCrossRemoveCart(param, userQuery, context);
                case "cross_view_cart":
                    return viewUserCart(param);
                case "cross_clear_cart":
                    return proposeCrossClearCart(param, context);
                default:
                    return AgentQueryResult.success("请告诉我您需要代替哪个用户执行什么操作？\n" +
                            "支持: 添加购物车、移除购物车、查看购物车、清空购物车", getCode());
            }
        } catch (Exception e) {
            log.error("UltraCrossUserAgent 执行异常: {}", e.getMessage(), e);
            return AgentQueryResult.error(getCode(), e.getMessage());
        }
    }

    private String fallbackTool(String query) {
        String q = query.toLowerCase();
        if ((q.contains("加入") || q.contains("添加") || q.contains("加")) && q.contains("购物车")) return "cross_add_cart";
        if ((q.contains("移除") || q.contains("删除") || q.contains("去掉")) && q.contains("购物车")) return "cross_remove_cart";
        if ((q.contains("查看") || q.contains("看看")) && q.contains("购物车")) return "cross_view_cart";
        if (q.contains("清空") && q.contains("购物车")) return "cross_clear_cart";
        return "none";
    }

    private AgentQueryResult proposeCrossAddCart(String param, String userQuery, AgentContext context) {
        // 解析用户和商品
        UserDTO targetUser = extractUser(userQuery);
        if (targetUser == null) {
            return AgentQueryResult.success("未能识别目标用户，请明确指定用户名或用户ID", getCode());
        }

        ProductDTO product = extractProduct(userQuery);
        if (product == null) {
            return AgentQueryResult.success("未能识别目标商品，请明确指定商品名称或ID\n" +
                    "已确认目标用户: " + targetUser.getNickname() + " (ID:" + targetUser.getId() + ")", getCode());
        }

        PendingAction action = PendingAction.of("ultra_cross_add_cart",
                "为用户「" + targetUser.getNickname() + "」的购物车添加「" + product.getName() + "」")
                .param("targetUserId", String.valueOf(targetUser.getId()))
                .param("productId", String.valueOf(product.getId()))
                .param("quantity", "1");

        String data = "跨用户操作详情:\n" +
                "- 目标用户: " + targetUser.getNickname() + " (ID:" + targetUser.getId() + ")\n" +
                "- 商品: " + product.getName() + " (ID:" + product.getId() + ")\n" +
                "- 价格: ¥" + (product.getPrice() != null ? product.getPrice() : "未知") + "\n" +
                "- 数量: 1\n" +
                "- 操作: 添加到购物车";
        return AgentQueryResult.actionProposed(data, getCode(), action);
    }

    private AgentQueryResult proposeCrossRemoveCart(String param, String userQuery, AgentContext context) {
        UserDTO targetUser = extractUser(userQuery);
        if (targetUser == null) {
            return AgentQueryResult.success("未能识别目标用户，请明确指定用户名或用户ID", getCode());
        }

        ProductDTO product = extractProduct(userQuery);
        if (product == null) {
            return AgentQueryResult.success("未能识别目标商品，请明确指定商品名称或ID", getCode());
        }

        PendingAction action = PendingAction.of("ultra_cross_remove_cart",
                "从用户「" + targetUser.getNickname() + "」的购物车移除「" + product.getName() + "」")
                .param("targetUserId", String.valueOf(targetUser.getId()))
                .param("productId", String.valueOf(product.getId()));

        String data = "跨用户操作详情:\n" +
                "- 目标用户: " + targetUser.getNickname() + " (ID:" + targetUser.getId() + ")\n" +
                "- 商品: " + product.getName() + "\n" +
                "- 操作: 从购物车移除";
        return AgentQueryResult.actionProposed(data, getCode(), action);
    }

    private AgentQueryResult viewUserCart(String param) {
        UserDTO user = extractUser(param);
        if (user == null) {
            return AgentQueryResult.success("未能识别目标用户，请明确指定用户名或用户ID", getCode());
        }

        List<CartDTO> cartItems = productService.listCartItems(user.getId());
        if (cartItems == null || cartItems.isEmpty()) {
            return AgentQueryResult.success("用户「" + user.getNickname() + "」的购物车为空", getCode());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("用户「").append(user.getNickname()).append("」(ID:").append(user.getId()).append(") 的购物车:\n");
        java.math.BigDecimal total = java.math.BigDecimal.ZERO;
        for (CartDTO item : cartItems) {
            sb.append("- ").append(item.getProductName() != null ? item.getProductName() : "商品ID:" + item.getProductId());
            sb.append(" × ").append(item.getQuantity());
            if (item.getPrice() != null) {
                sb.append(", ¥").append(item.getPrice());
                total = total.add(item.getPrice().multiply(java.math.BigDecimal.valueOf(item.getQuantity())));
            }
            sb.append("\n");
        }
        sb.append("\n共 ").append(cartItems.size()).append(" 件商品，总计: ¥").append(total);
        return AgentQueryResult.success(sb.toString(), getCode());
    }

    private AgentQueryResult proposeCrossClearCart(String param, AgentContext context) {
        UserDTO user = extractUser(param);
        if (user == null) {
            return AgentQueryResult.success("未能识别目标用户，请明确指定用户名或用户ID", getCode());
        }

        PendingAction action = PendingAction.of("ultra_cross_clear_cart",
                "清空用户「" + user.getNickname() + "」的购物车")
                .param("targetUserId", String.valueOf(user.getId()));

        String data = "跨用户操作详情:\n" +
                "- 目标用户: " + user.getNickname() + " (ID:" + user.getId() + ")\n" +
                "- 操作: 清空购物车\n" +
                "\n⚠️ 清空后无法恢复";
        return AgentQueryResult.actionProposed(data, getCode(), action);
    }

    // ========== 辅助方法 ==========

    private UserDTO extractUser(String text) {
        if (text == null) return null;
        // 尝试提取用户名：匹配"给XX"、"帮XX"、"XX的"等模式
        String[] patterns = {"给(.+?)的", "帮(.+?)的", "为(.+?)的", "(.+?)的购物车",
                "给(.+?)购物车", "帮(.+?)加", "查看(.+?)的"};
        for (String p : patterns) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(p).matcher(text);
            if (m.find()) {
                String name = m.group(1).trim();
                // 去除"用户"、"账号"等前缀
                name = name.replaceAll("^(用户|账号)", "").trim();
                if (!name.isEmpty()) {
                    UserDTO user = findUser(name);
                    if (user != null) return user;
                }
            }
        }
        // 如果全文是用户名/ID
        return findUser(text.trim());
    }

    private ProductDTO extractProduct(String text) {
        if (text == null) return null;
        // 提取"加入XX"、"添加XX"模式中的商品名
        String[] patterns = {"加入(.+?)$", "添加(.+?)$", "加(.+?)到", "购物车加入(.+?)$",
                "加入(.+?)(?:到|进)", "商品(.+?)$"};
        for (String p : patterns) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(p).matcher(text);
            if (m.find()) {
                String productName = m.group(1).trim();
                productName = productName.replaceAll("[，。,.]$", "").trim();
                if (!productName.isEmpty()) {
                    ProductDTO product = findProduct(productName);
                    if (product != null) return product;
                }
            }
        }
        return null;
    }

    private UserDTO findUser(String param) {
        if (param == null || param.isEmpty()) return null;
        try {
            Long id = Long.parseLong(param);
            return userService.findById(id);
        } catch (NumberFormatException ignored) {}
        try {
            return userService.findByNickname(param);
        } catch (Exception ignored) {}
        return null;
    }

    private ProductDTO findProduct(String name) {
        if (name == null || name.isEmpty()) return null;
        try {
            Long id = Long.parseLong(name);
            return productService.getProductById(id);
        } catch (NumberFormatException ignored) {}
        try {
            PageResult<ProductDTO> result = productService.listProducts(1, 1, null, name, null);
            if (result != null && result.getList() != null && !result.getList().isEmpty()) {
                return result.getList().get(0);
            }
        } catch (Exception ignored) {}
        return null;
    }
}

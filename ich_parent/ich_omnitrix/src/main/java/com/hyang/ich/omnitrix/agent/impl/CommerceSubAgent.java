package com.hyang.ich.omnitrix.agent.impl;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.order.OrderService;
import com.hyang.ich.order.dto.OrderDTO;
import com.hyang.ich.product.ProductService;
import com.hyang.ich.product.dto.CartDTO;
import com.hyang.ich.product.dto.ProductDTO;
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

@Slf4j
@Component
public class CommerceSubAgent implements SubAgent {

    private final ProductService productService;
    private final OrderService orderService;
    private final ToolSelector toolSelector;

    /** 该代理可调用的工具列表 */
    private static final List<AgentTool> TOOLS = Arrays.asList(
            AgentTool.of("query_orders", "查询用户的订单列表、订单状态、物流、发货、收货信息", "无"),
            AgentTool.of("query_cart", "查询用户的购物车商品", "无"),
            AgentTool.of("search_products", "搜索文创商品、查看商品价格库存", "搜索关键词"),
            AgentTool.of("add_to_cart", "将商品加入购物车、加购、添加到购物车", "商品关键词"),
            AgentTool.of("remove_from_cart", "从购物车移除商品、删除购物车商品", "商品关键词"),
            AgentTool.of("clear_cart", "清空购物车、清除购物车所有商品", "无"),
            AgentTool.of("update_cart_quantity", "修改购物车商品数量、改数量", "商品关键词和数量"),
            AgentTool.of("create_order", "下单、提交订单、购买购物车商品", "无"),
            AgentTool.of("pay_order", "支付订单、付款、结算", "订单号"),
            AgentTool.of("cancel_order", "取消订单", "订单号"),
            AgentTool.of("confirm_receive", "确认收货", "订单号")
    );

    public CommerceSubAgent(ProductService productService, OrderService orderService,
                            ToolSelector toolSelector) {
        this.productService = productService;
        this.orderService = orderService;
        this.toolSelector = toolSelector;
    }

    @Override
    public String getCode() {
        return "commerce_assistant";
    }

    @Override
    public String getName() {
        return "文创商城助手";
    }

    @Override
    public String getDescription() {
        return "回答商品、订单、购物车相关问题";
    }

    @Override
    public String getAgentPrompt() {
        return "## 当前任务模式: 文创商城助手\n" +
                "你现在专注于回答文创商品、订单、购物车相关问题。\n" +
                "- 金额、库存、订单状态必须精确引用查询结果中的数字\n" +
                "- 不要推测价格或库存，没有的就说没有";
    }

    @Override
    public AgentQueryResult execute(String userQuery, AgentContext context) {
        try {
            // 使用 LLM 选择工具（替代硬编码 if-else）
            ToolCallResult toolCall = toolSelector.select(userQuery, TOOLS);
            log.debug("CommerceSubAgent 工具选择: tool={}, param={}", toolCall.getToolName(), toolCall.getParameter());

            StringBuilder data = new StringBuilder();

            String selectedTool = toolCall.isNone() ? fallbackTool(userQuery) : toolCall.getToolName();
            String param = toolCall.getParameter() != null && !toolCall.getParameter().isEmpty()
                    ? toolCall.getParameter() : AgentUtils.extractKeyword(userQuery);

            switch (selectedTool) {
                case "query_orders":
                    data.append(queryOrders(context));
                    break;
                case "query_cart":
                    data.append(queryCart(context));
                    break;
                case "search_products":
                    data.append(searchProducts(param));
                    break;
                case "add_to_cart":
                    return proposeAddToCart(param, context);
                case "remove_from_cart":
                    return proposeRemoveFromCart(param, context);
                case "cancel_order":
                    return proposeCancelOrder(param, context);
                case "confirm_receive":
                    return proposeConfirmReceive(param, context);
                case "clear_cart":
                    return proposeClearCart(context);
                case "update_cart_quantity":
                    return proposeUpdateCartQuantity(param, context);
                case "create_order":
                    return proposeCreateOrder(context);
                case "pay_order":
                    return proposePayOrder(param, context);
                default:
                    data.append(searchProducts(param));
                    break;
            }

            if (data.length() == 0) {
                return AgentQueryResult.empty(getCode());
            }
            return AgentQueryResult.success(data.toString(), getCode());

        } catch (Exception e) {
            log.error("CommerceSubAgent 执行异常: {}", e.getMessage(), e);
            return AgentQueryResult.error(getCode(), e.getMessage());
        }
    }

    /** 关键词回退（LLM 工具选择失败时使用） */
    private String fallbackTool(String query) {
        String q = query.toLowerCase();
        if (q.contains("加入购物车") || q.contains("加购") || q.contains("添加到购物车")) return "add_to_cart";
        if (q.contains("移除购物车") || q.contains("删除购物车")) return "remove_from_cart";
        if (q.contains("清空购物车") || q.contains("清除购物车")) return "clear_cart";
        if (q.contains("改数量") || q.contains("修改数量")) return "update_cart_quantity";
        if (q.contains("下单") || q.contains("提交订单") || q.contains("购买")) return "create_order";
        if (q.contains("支付") || q.contains("付款") || q.contains("结算")) return "pay_order";
        if (q.contains("取消订单")) return "cancel_order";
        if (q.contains("确认收货")) return "confirm_receive";
        if (q.contains("订单") || q.contains("物流") || q.contains("发货")) return "query_orders";
        if (q.contains("购物车")) return "query_cart";
        return "search_products";
    }

    // ========== 写操作提议 ==========

    private AgentQueryResult proposeAddToCart(String keyword, AgentContext context) {
        PageResult<ProductDTO> products = productService.listProducts(1, 3, null, keyword, 1);
        if (products == null || products.getList() == null || products.getList().isEmpty()) {
            return AgentQueryResult.success(
                    "未找到关键词\"" + keyword + "\"对应的商品", getCode());
        }
        ProductDTO first = products.getList().get(0);
        StringBuilder data = new StringBuilder("商品搜索结果:\n");
        for (ProductDTO p : products.getList()) {
            data.append("- ").append(p.getName());
            if (p.getPrice() != null) data.append(", 价格: ¥").append(p.getPrice());
            if (p.getStock() != null) data.append(", 库存: ").append(p.getStock());
            data.append("\n");
        }
        PendingAction action = PendingAction.of("add_to_cart",
                "将「" + first.getName() + "」加入购物车")
                .param("productId", String.valueOf(first.getId()))
                .param("productName", first.getName())
                .param("quantity", "1");
        return AgentQueryResult.actionProposed(data.toString(), getCode(), action);
    }

    private AgentQueryResult proposeRemoveFromCart(String keyword, AgentContext context) {
        List<CartDTO> cartItems = productService.listCartItems(context.getUserId());
        if (cartItems == null || cartItems.isEmpty()) {
            return AgentQueryResult.success("购物车为空，没有可移除的商品", getCode());
        }
        CartDTO target = null;
        for (CartDTO c : cartItems) {
            String name = c.getProductName() != null ? c.getProductName() : "";
            if (name.contains(keyword) || keyword.contains(name)) {
                target = c;
                break;
            }
        }
        StringBuilder data = new StringBuilder("购物车商品:\n");
        for (CartDTO c : cartItems) {
            data.append("- ").append(c.getProductName() != null ? c.getProductName() : "商品ID:" + c.getProductId())
                    .append(", 数量: ").append(c.getQuantity()).append("\n");
        }
        if (target == null) {
            return AgentQueryResult.success(
                    data + "\n未在购物车中找到关键词\"" + keyword + "\"对应的商品", getCode());
        }
        PendingAction action = PendingAction.of("remove_from_cart",
                "从购物车移除「" + target.getProductName() + "」")
                .param("productId", String.valueOf(target.getProductId()));
        return AgentQueryResult.actionProposed(data.toString(), getCode(), action);
    }

    private AgentQueryResult proposeCancelOrder(String orderNo, AgentContext context) {
        PageResult<OrderDTO> orders = orderService.listUserOrders(context.getUserId(), null, 1, 10);
        if (orders == null || orders.getList() == null || orders.getList().isEmpty()) {
            return AgentQueryResult.success("未找到订单记录", getCode());
        }
        OrderDTO target = null;
        for (OrderDTO o : orders.getList()) {
            if (o.getOrderNo() != null && o.getOrderNo().contains(orderNo)) {
                target = o;
                break;
            }
        }
        StringBuilder data = new StringBuilder("用户订单:\n");
        for (OrderDTO o : orders.getList()) {
            data.append("- 订单号: ").append(o.getOrderNo())
                    .append(", 状态: ").append(AgentUtils.formatOrderStatus(o.getStatus()))
                    .append("\n");
        }
        if (target == null) {
            return AgentQueryResult.success(
                    data + "\n未找到订单号包含\"" + orderNo + "\"的订单", getCode());
        }
        PendingAction action = PendingAction.of("cancel_order",
                "取消订单 " + target.getOrderNo())
                .param("orderNo", target.getOrderNo());
        return AgentQueryResult.actionProposed(data.toString(), getCode(), action);
    }

    private AgentQueryResult proposeConfirmReceive(String orderNo, AgentContext context) {
        PageResult<OrderDTO> orders = orderService.listUserOrders(context.getUserId(), null, 1, 10);
        if (orders == null || orders.getList() == null || orders.getList().isEmpty()) {
            return AgentQueryResult.success("未找到订单记录", getCode());
        }
        OrderDTO target = null;
        for (OrderDTO o : orders.getList()) {
            if (o.getOrderNo() != null && o.getOrderNo().contains(orderNo)) {
                target = o;
                break;
            }
        }
        StringBuilder data = new StringBuilder("用户订单:\n");
        for (OrderDTO o : orders.getList()) {
            data.append("- 订单号: ").append(o.getOrderNo())
                    .append(", 状态: ").append(AgentUtils.formatOrderStatus(o.getStatus()))
                    .append("\n");
        }
        if (target == null) {
            return AgentQueryResult.success(
                    data + "\n未找到订单号包含\"" + orderNo + "\"的订单", getCode());
        }
        PendingAction action = PendingAction.of("confirm_receive",
                "确认收货订单 " + target.getOrderNo())
                .param("orderNo", target.getOrderNo());
        return AgentQueryResult.actionProposed(data.toString(), getCode(), action);
    }

    private AgentQueryResult proposeClearCart(AgentContext context) {
        List<CartDTO> cartItems = productService.listCartItems(context.getUserId());
        if (cartItems == null || cartItems.isEmpty()) {
            return AgentQueryResult.success("购物车已经是空的", getCode());
        }
        StringBuilder data = new StringBuilder("当前购物车商品(" + cartItems.size() + "件):\n");
        for (CartDTO c : cartItems) {
            data.append("- ").append(c.getProductName() != null ? c.getProductName() : "商品ID:" + c.getProductId())
                    .append(", 数量: ").append(c.getQuantity()).append("\n");
        }
        PendingAction action = PendingAction.of("clear_cart", "清空购物车(" + cartItems.size() + "件商品)");
        return AgentQueryResult.actionProposed(data.toString(), getCode(), action);
    }

    private AgentQueryResult proposeUpdateCartQuantity(String param, AgentContext context) {
        List<CartDTO> cartItems = productService.listCartItems(context.getUserId());
        if (cartItems == null || cartItems.isEmpty()) {
            return AgentQueryResult.success("购物车为空，无法修改数量", getCode());
        }
        // 尝试从 param 中提取数量
        int quantity = 1;
        String keyword = param;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)").matcher(param);
        if (matcher.find()) {
            quantity = Integer.parseInt(matcher.group(1));
            keyword = param.replaceAll("\\d+", "").trim();
        }
        CartDTO target = null;
        for (CartDTO c : cartItems) {
            String name = c.getProductName() != null ? c.getProductName() : "";
            if (!keyword.isEmpty() && (name.contains(keyword) || keyword.contains(name))) {
                target = c;
                break;
            }
        }
        if (target == null && cartItems.size() == 1) {
            target = cartItems.get(0);
        }
        StringBuilder data = new StringBuilder("购物车商品:\n");
        for (CartDTO c : cartItems) {
            data.append("- ").append(c.getProductName() != null ? c.getProductName() : "商品ID:" + c.getProductId())
                    .append(", 数量: ").append(c.getQuantity()).append("\n");
        }
        if (target == null) {
            return AgentQueryResult.success(
                    data + "\n未能确定要修改哪个商品的数量，请告知具体商品名称", getCode());
        }
        PendingAction action = PendingAction.of("update_cart_quantity",
                "将「" + target.getProductName() + "」数量改为" + quantity)
                .param("productId", String.valueOf(target.getProductId()))
                .param("quantity", String.valueOf(quantity));
        return AgentQueryResult.actionProposed(data.toString(), getCode(), action);
    }

    private AgentQueryResult proposeCreateOrder(AgentContext context) {
        List<CartDTO> cartItems = productService.listCartItems(context.getUserId());
        if (cartItems == null || cartItems.isEmpty()) {
            return AgentQueryResult.success("购物车为空，无法下单", getCode());
        }
        StringBuilder data = new StringBuilder("购物车商品:\n");
        for (CartDTO c : cartItems) {
            data.append("- ").append(c.getProductName() != null ? c.getProductName() : "商品ID:" + c.getProductId())
                    .append(", 数量: ").append(c.getQuantity()).append("\n");
        }
        // 构建商品ID列表存入params
        StringBuilder itemIds = new StringBuilder();
        StringBuilder itemQtys = new StringBuilder();
        for (CartDTO c : cartItems) {
            if (itemIds.length() > 0) { itemIds.append(","); itemQtys.append(","); }
            itemIds.append(c.getProductId());
            itemQtys.append(c.getQuantity());
        }
        PendingAction action = PendingAction.of("create_order",
                "将购物车" + cartItems.size() + "件商品提交订单")
                .param("productIds", itemIds.toString())
                .param("quantities", itemQtys.toString());
        return AgentQueryResult.actionProposed(data.toString(), getCode(), action);
    }

    private AgentQueryResult proposePayOrder(String orderNo, AgentContext context) {
        PageResult<OrderDTO> orders = orderService.listUserOrders(context.getUserId(), null, 1, 10);
        if (orders == null || orders.getList() == null || orders.getList().isEmpty()) {
            return AgentQueryResult.success("未找到订单记录", getCode());
        }
        // 找待支付订单
        OrderDTO target = null;
        for (OrderDTO o : orders.getList()) {
            if (o.getOrderNo() != null && o.getOrderNo().contains(orderNo)) {
                target = o;
                break;
            }
        }
        // 如果没指定订单号，找第一个待支付订单
        if (target == null) {
            for (OrderDTO o : orders.getList()) {
                if (o.getStatus() != null && o.getStatus() == 0) {
                    target = o;
                    break;
                }
            }
        }
        StringBuilder data = new StringBuilder("用户订单:\n");
        for (OrderDTO o : orders.getList()) {
            data.append("- 订单号: ").append(o.getOrderNo())
                    .append(", 状态: ").append(AgentUtils.formatOrderStatus(o.getStatus()))
                    .append(", 金额: ¥").append(o.getPayAmount() != null ? o.getPayAmount() : "0")
                    .append("\n");
        }
        if (target == null) {
            return AgentQueryResult.success(
                    data + "\n未找到待支付的订单", getCode());
        }
        PendingAction action = PendingAction.of("pay_order",
                "支付订单 " + target.getOrderNo() + " (¥" + target.getPayAmount() + ")")
                .param("orderNo", target.getOrderNo());
        return AgentQueryResult.actionProposed(data.toString(), getCode(), action);
    }

    // ========== 只读查询 ==========

    private String queryOrders(AgentContext context) {
        PageResult<OrderDTO> orders = orderService.listUserOrders(context.getUserId(), null, 1, 5);
        if (orders == null || orders.getList() == null || orders.getList().isEmpty()) {
            return "该用户暂无订单记录\n";
        }
        StringBuilder sb = new StringBuilder("用户订单列表:\n");
        for (OrderDTO order : orders.getList()) {
            sb.append("- 订单号: ").append(order.getOrderNo());
            sb.append(", 状态: ").append(AgentUtils.formatOrderStatus(order.getStatus()));
            if (order.getPayAmount() != null) {
                sb.append(", 金额: ¥").append(order.getPayAmount());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String queryCart(AgentContext context) {
        List<CartDTO> cartItems = productService.listCartItems(context.getUserId());
        if (cartItems == null || cartItems.isEmpty()) {
            return "购物车为空\n";
        }
        StringBuilder sb = new StringBuilder("购物车商品:\n");
        for (CartDTO cart : cartItems) {
            sb.append("- ").append(cart.getProductName() != null ? cart.getProductName() : "商品ID:" + cart.getProductId());
            sb.append(", 数量: ").append(cart.getQuantity()).append("\n");
        }
        return sb.toString();
    }

    private String searchProducts(String keyword) {
        PageResult<ProductDTO> products = productService.listProducts(1, 5, null, keyword, 1);
        if (products == null || products.getList() == null || products.getList().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("商品搜索结果:\n");
        for (ProductDTO product : products.getList()) {
            sb.append("- ").append(product.getName());
            if (product.getPrice() != null) sb.append(", 价格: ¥").append(product.getPrice());
            if (product.getStock() != null) sb.append(", 库存: ").append(product.getStock());
            sb.append("\n");
        }
        return sb.toString();
    }
}

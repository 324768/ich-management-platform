package com.hyang.ich.omnitrix.brain.tools;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.omnitrix.agent.AgentUtils;
import com.hyang.ich.omnitrix.brain.AiRequestContext;
import com.hyang.ich.omnitrix.dto.PendingAction;
import com.hyang.ich.omnitrix.orchestrator.ActionExecutor;
import com.hyang.ich.order.OrderService;
import com.hyang.ich.order.dto.OrderDTO;
import com.hyang.ich.product.ProductService;
import com.hyang.ich.product.dto.CartDTO;
import com.hyang.ich.product.dto.ProductDTO;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文创商城工具集 — 替代原 CommerceSubAgent，提供 LangChain4j @Tool 方法。
 */
@Slf4j
@Component
public class CommerceTools {

    private final ProductService productService;
    private final OrderService orderService;
    private final ActionExecutor actionExecutor;

    private static final Pattern PRICE_PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(?:块|元|块钱)|" +
            "(?:以内|以下|不超过|不高于|低于|少于|小于)\\s*(\\d+(?:\\.\\d+)?)\\s*(?:块|元|块钱)|" +
            "(?:在|从)\\s*(\\d+(?:\\.\\d+)?)\\s*(?:到|至|-)\\s*(\\d+(?:\\.\\d+)?)\\s*(?:块|元|块钱)|" +
            "(\\d+(?:\\.\\d+)?)\\s*-\\s*(\\d+(?:\\.\\d+)?)\\s*(?:块|元|块钱)",
            Pattern.CASE_INSENSITIVE);

    public CommerceTools(ProductService productService, OrderService orderService,
                         ActionExecutor actionExecutor) {
        this.productService = productService;
        this.orderService = orderService;
        this.actionExecutor = actionExecutor;
    }

    @Tool("搜索文创商品、查看商品价格库存。参数: 搜索关键词（可包含价格范围如'100元以内'）")
    public String searchProducts(String query) {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        BigDecimal minPrice = null, maxPrice = null;
        String keyword = query;
        Matcher matcher = PRICE_PATTERN.matcher(query);
        if (matcher.find()) {
            if (matcher.group(1) != null) maxPrice = new BigDecimal(matcher.group(1));
            else if (matcher.group(2) != null) maxPrice = new BigDecimal(matcher.group(2));
            else if (matcher.group(3) != null && matcher.group(4) != null) {
                minPrice = new BigDecimal(matcher.group(3));
                maxPrice = new BigDecimal(matcher.group(4));
            } else if (matcher.group(5) != null && matcher.group(6) != null) {
                minPrice = new BigDecimal(matcher.group(5));
                maxPrice = new BigDecimal(matcher.group(6));
            }
            keyword = query.replaceAll(PRICE_PATTERN.pattern(), "").trim();
            if (keyword.isEmpty()) keyword = null;
        }
        PageResult<ProductDTO> products;
        if (minPrice != null || maxPrice != null) {
            products = productService.listProductsByPriceRange(1, 10, minPrice, maxPrice, keyword, 1);
        } else {
            products = productService.listProducts(1, 10, null, keyword, 1);
        }
        if (products == null || products.getList() == null || products.getList().isEmpty()) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("未找到与\"" + query + "\"相关的商品")
                    .withSearchEmptyHint("商品").toXml();
        }
        StringBuilder sb = new StringBuilder();
        for (ProductDTO p : products.getList()) {
            sb.append("- ").append(p.getName());
            if (p.getPrice() != null) sb.append(", ¥").append(p.getPrice());
            if (p.getStock() != null) sb.append(", 库存: ").append(p.getStock());
            sb.append("\n");
        }
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.success("找到" + products.getList().size() + "个商品", sb.toString())
                .withSearchSuccessHint("商品").toXml();
    }

    @Tool("查询当前用户的订单列表")
    public String queryOrders() {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        Long userId = AiRequestContext.getUserId();
        PageResult<OrderDTO> orders = orderService.listUserOrders(userId, null, 1, 5);
        if (orders == null || orders.getList() == null || orders.getList().isEmpty()) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("暂无订单记录").withSearchEmptyHint("订单").toXml();
        }
        StringBuilder sb = new StringBuilder();
        for (OrderDTO o : orders.getList()) {
            sb.append("- 订单号: ").append(o.getOrderNo());
            sb.append(", 状态: ").append(AgentUtils.formatOrderStatus(o.getStatus()));
            if (o.getPayAmount() != null) sb.append(", 金额: ¥").append(o.getPayAmount());
            sb.append("\n");
        }
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.success("找到" + orders.getList().size() + "个订单", sb.toString())
                .withSearchSuccessHint("订单").toXml();
    }

    @Tool("查询当前用户的购物车商品")
    public String queryCart() {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        Long userId = AiRequestContext.getUserId();
        List<CartDTO> cartItems = productService.listCartItems(userId);
        if (cartItems == null || cartItems.isEmpty()) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("购物车为空").withSearchEmptyHint("购物车").toXml();
        }
        StringBuilder sb = new StringBuilder();
        for (CartDTO c : cartItems) {
            sb.append("- ").append(c.getProductName() != null ? c.getProductName() : "商品ID:" + c.getProductId());
            sb.append(", 数量: ").append(c.getQuantity()).append("\n");
        }
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.success("购物车有" + cartItems.size() + "件商品", sb.toString())
                .withSearchSuccessHint("购物车").toXml();
    }

    @Tool("将商品加入购物车。参数: 商品关键词")
    public String addToCart(String keyword) {
        String searchKw = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        PageResult<ProductDTO> products = productService.listProducts(1, 5, null, searchKw, 1);
        if (products == null || products.getList() == null || products.getList().isEmpty()) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("未找到对应商品，请说明商品名称")
                    .withSearchEmptyHint("商品").toXml();
        }
        ProductDTO first = products.getList().get(0);
        StringBuilder data = new StringBuilder();
        for (ProductDTO p : products.getList()) {
            data.append("- ").append(p.getName());
            if (p.getPrice() != null) data.append(", ¥").append(p.getPrice());
            data.append("\n");
        }
        PendingAction action = PendingAction.of("add_to_cart", "将「" + first.getName() + "」加入购物车")
                .param("productId", String.valueOf(first.getId()))
                .param("productName", first.getName())
                .param("quantity", "1");
        actionExecutor.savePendingAction(AiRequestContext.getSessionId(), action);
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.actionProposed("已提交加购「" + first.getName() + "」的确认请求", data.toString())
                .withActionProposedHint("加入购物车").toXml();
    }

    @Tool("从购物车移除商品。参数: 商品关键词")
    public String removeFromCart(String keyword) {
        Long userId = AiRequestContext.getUserId();
        List<CartDTO> cartItems = productService.listCartItems(userId);
        if (cartItems == null || cartItems.isEmpty()) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("购物车为空，没有可移除的商品").withSearchEmptyHint("购物车").toXml();
        }
        CartDTO target = findCartItem(cartItems, keyword);
        if (target == null) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("未在购物车中找到\"" + keyword + "\"对应的商品")
                    .withSearchEmptyHint("购物车商品").toXml();
        }
        PendingAction action = PendingAction.of("remove_from_cart", "从购物车移除「" + target.getProductName() + "」")
                .param("productId", String.valueOf(target.getProductId()));
        actionExecutor.savePendingAction(AiRequestContext.getSessionId(), action);
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.actionProposed("已提交移除「" + target.getProductName() + "」的确认请求", null)
                .withActionProposedHint("移除商品").toXml();
    }

    @Tool("清空购物车")
    public String clearCart() {
        Long userId = AiRequestContext.getUserId();
        List<CartDTO> cartItems = productService.listCartItems(userId);
        if (cartItems == null || cartItems.isEmpty()) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("购物车已经是空的").withSearchEmptyHint("购物车").toXml();
        }
        PendingAction action = PendingAction.of("clear_cart", "清空购物车(" + cartItems.size() + "件商品)");
        actionExecutor.savePendingAction(AiRequestContext.getSessionId(), action);
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.actionProposed("已提交清空购物车(" + cartItems.size() + "件)的确认请求", null)
                .withActionProposedHint("清空购物车").toXml();
    }

    @Tool("修改购物车商品数量。参数格式: '商品关键词|数量'，用竖线分隔")
    public String updateCartQuantity(String input) {
        Long userId = AiRequestContext.getUserId();
        List<CartDTO> cartItems = productService.listCartItems(userId);
        if (cartItems == null || cartItems.isEmpty()) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("购物车为空，无法修改数量").withSearchEmptyHint("购物车").toXml();
        }
        String[] parts = input.split("\\|", 2);
        String keyword = parts[0].trim();
        int quantity = 1;
        if (parts.length > 1) {
            try { quantity = Integer.parseInt(parts[1].trim()); } catch (NumberFormatException ignored) {}
        }
        CartDTO target = findCartItem(cartItems, keyword);
        if (target == null && cartItems.size() == 1) target = cartItems.get(0);
        if (target == null) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("未能确定要修改哪个商品的数量，请告知具体商品名称")
                    .withSearchEmptyHint("购物车商品").toXml();
        }
        PendingAction action = PendingAction.of("update_cart_quantity",
                "将「" + target.getProductName() + "」数量改为" + quantity)
                .param("productId", String.valueOf(target.getProductId()))
                .param("quantity", String.valueOf(quantity));
        actionExecutor.savePendingAction(AiRequestContext.getSessionId(), action);
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.actionProposed("已提交修改「" + target.getProductName() + "」数量为" + quantity + "的确认请求", null)
                .withActionProposedHint("修改数量").toXml();
    }

    @Tool("提交购物车商品为订单、下单购买")
    public String createOrder() {
        Long userId = AiRequestContext.getUserId();
        List<CartDTO> cartItems = productService.listCartItems(userId);
        if (cartItems == null || cartItems.isEmpty()) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("购物车为空，无法下单").withSearchEmptyHint("购物车").toXml();
        }
        StringBuilder itemIds = new StringBuilder();
        StringBuilder itemQtys = new StringBuilder();
        StringBuilder desc = new StringBuilder();
        for (CartDTO c : cartItems) {
            if (itemIds.length() > 0) { itemIds.append(","); itemQtys.append(","); }
            itemIds.append(c.getProductId());
            itemQtys.append(c.getQuantity());
            desc.append("- ").append(c.getProductName() != null ? c.getProductName() : "ID:" + c.getProductId())
                    .append(" ×").append(c.getQuantity()).append("\n");
        }
        PendingAction action = PendingAction.of("create_order", "将购物车" + cartItems.size() + "件商品提交订单")
                .param("productIds", itemIds.toString())
                .param("quantities", itemQtys.toString());
        actionExecutor.savePendingAction(AiRequestContext.getSessionId(), action);
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.actionProposed("已提交" + cartItems.size() + "件商品下单的确认请求", desc.toString())
                .withActionProposedHint("提交订单").toXml();
    }

    @Tool("支付订单。参数: 订单号（可为空，自动选择最近未支付订单）")
    public String payOrder(String orderNo) {
        Long userId = AiRequestContext.getUserId();
        PageResult<OrderDTO> orders = orderService.listUserOrders(userId, null, 1, 10);
        if (orders == null || orders.getList() == null || orders.getList().isEmpty()) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("未找到订单记录").withSearchEmptyHint("订单").toXml();
        }
        OrderDTO target = null;
        if (orderNo != null && !orderNo.trim().isEmpty()) {
            for (OrderDTO o : orders.getList()) {
                if (o.getOrderNo() != null && o.getOrderNo().contains(orderNo.trim())) { target = o; break; }
            }
        }
        if (target == null) {
            for (OrderDTO o : orders.getList()) {
                if (o.getStatus() != null && o.getStatus() == 0) { target = o; break; }
            }
        }
        if (target == null) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("未找到待支付的订单").withSearchEmptyHint("待支付订单").toXml();
        }
        PendingAction action = PendingAction.of("pay_order",
                "支付订单 " + target.getOrderNo() + " (¥" + target.getPayAmount() + ")")
                .param("orderNo", target.getOrderNo());
        actionExecutor.savePendingAction(AiRequestContext.getSessionId(), action);
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.actionProposed("已提交支付订单 " + target.getOrderNo() + "(¥" + target.getPayAmount() + ")的确认请求", null)
                .withActionProposedHint("支付订单").toXml();
    }

    @Tool("取消订单。参数: 订单号")
    public String cancelOrder(String orderNo) {
        Long userId = AiRequestContext.getUserId();
        PageResult<OrderDTO> orders = orderService.listUserOrders(userId, null, 1, 10);
        if (orders == null || orders.getList() == null || orders.getList().isEmpty()) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("未找到订单记录").withSearchEmptyHint("订单").toXml();
        }
        OrderDTO target = null;
        for (OrderDTO o : orders.getList()) {
            if (o.getOrderNo() != null && o.getOrderNo().contains(orderNo)) { target = o; break; }
        }
        if (target == null) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("未找到订单号包含\"" + orderNo + "\"的订单")
                    .withSearchEmptyHint("订单").toXml();
        }
        PendingAction action = PendingAction.of("cancel_order", "取消订单 " + target.getOrderNo())
                .param("orderNo", target.getOrderNo());
        actionExecutor.savePendingAction(AiRequestContext.getSessionId(), action);
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.actionProposed("已提交取消订单 " + target.getOrderNo() + "的确认请求", null)
                .withActionProposedHint("取消订单").toXml();
    }

    @Tool("确认收货。参数: 订单号")
    public String confirmReceive(String orderNo) {
        Long userId = AiRequestContext.getUserId();
        PageResult<OrderDTO> orders = orderService.listUserOrders(userId, null, 1, 10);
        if (orders == null || orders.getList() == null || orders.getList().isEmpty()) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("未找到订单记录").withSearchEmptyHint("订单").toXml();
        }
        OrderDTO target = null;
        for (OrderDTO o : orders.getList()) {
            if (o.getOrderNo() != null && o.getOrderNo().contains(orderNo)) { target = o; break; }
        }
        if (target == null) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("未找到订单号包含\"" + orderNo + "\"的订单")
                    .withSearchEmptyHint("订单").toXml();
        }
        PendingAction action = PendingAction.of("confirm_receive", "确认收货订单 " + target.getOrderNo())
                .param("orderNo", target.getOrderNo());
        actionExecutor.savePendingAction(AiRequestContext.getSessionId(), action);
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.actionProposed("已提交确认收货 " + target.getOrderNo() + "的确认请求", null)
                .withActionProposedHint("确认收货").toXml();
    }

    private CartDTO findCartItem(List<CartDTO> items, String keyword) {
        for (CartDTO c : items) {
            String name = c.getProductName() != null ? c.getProductName() : "";
            if (name.contains(keyword) || keyword.contains(name)) return c;
        }
        return null;
    }
}

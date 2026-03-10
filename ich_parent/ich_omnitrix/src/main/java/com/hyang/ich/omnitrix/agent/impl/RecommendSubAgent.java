package com.hyang.ich.omnitrix.agent.impl;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.content.ContentService;
import com.hyang.ich.content.dto.IchItemDTO;
import com.hyang.ich.product.ProductService;
import com.hyang.ich.product.dto.ProductDTO;
import com.hyang.ich.omnitrix.agent.AgentContext;
import com.hyang.ich.omnitrix.agent.AgentUtils;
import com.hyang.ich.omnitrix.agent.SubAgent;
import com.hyang.ich.omnitrix.agent.tool.AgentTool;
import com.hyang.ich.omnitrix.agent.tool.ToolCallResult;
import com.hyang.ich.omnitrix.agent.tool.HintGenerator;
import com.hyang.ich.omnitrix.agent.tool.ToolSelector;
import com.hyang.ich.omnitrix.dto.AgentQueryResult;
import com.hyang.ich.omnitrix.service.RecommendService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class RecommendSubAgent implements SubAgent {

    private final RecommendService recommendService;
    private final ContentService contentService;
    private final ProductService productService;
    private final ToolSelector toolSelector;

    private static final List<AgentTool> TOOLS = Arrays.asList(
            AgentTool.of("recommend_items", "推荐非遗项目、非遗文化内容", "无"),
            AgentTool.of("recommend_products", "推荐文创商品、文创产品", "无"),
            AgentTool.of("recommend_all", "综合推荐非遗项目和文创商品", "无")
    );

    public RecommendSubAgent(RecommendService recommendService,
                             ContentService contentService,
                             ProductService productService,
                             ToolSelector toolSelector) {
        this.recommendService = recommendService;
        this.contentService = contentService;
        this.productService = productService;
        this.toolSelector = toolSelector;
    }

    @Override
    public String getCode() {
        return "recommend_assistant";
    }

    @Override
    public String getName() {
        return "智能推荐助手";
    }

    @Override
    public String getDescription() {
        return "基于用户行为生成个性化推荐";
    }

    @Override
    public String getAgentPrompt() {
        return "## 当前任务模式: 智能推荐\n" +
                "基于用户的浏览和行为数据，为用户推荐感兴趣的内容。\n" +
                "- 每条推荐附上一句推荐理由\n" +
                "- 推荐内容来自平台真实数据，引导用户查看详情";
    }

    @Override
    public AgentQueryResult execute(String userQuery, AgentContext context) {
        try {
            ToolCallResult toolCall = toolSelector.select(userQuery, TOOLS);
            log.debug("RecommendSubAgent 工具选择: tool={}", toolCall.getToolName());

            StringBuilder data = new StringBuilder();

            // 先附加用户兴趣分析
            data.append(buildInterests(context));

            switch (toolCall.isNone() ? "recommend_all" : toolCall.getToolName()) {
                case "recommend_items":
                    data.append(recommendItems());
                    break;
                case "recommend_products":
                    data.append(recommendProducts());
                    break;
                case "recommend_all":
                default:
                    data.append(recommendItems());
                    data.append(recommendProducts());
                    break;
            }

            if (data.length() == 0) {
                return HintGenerator.applyHints(AgentQueryResult.empty(getCode()), getCode(), "recommend");
            }
            return HintGenerator.applyHints(
                    AgentQueryResult.success(data.toString(), getCode()), getCode(), "recommend");

        } catch (Exception e) {
            log.error("RecommendSubAgent 执行异常: {}", e.getMessage(), e);
            return AgentQueryResult.error(getCode(), e.getMessage());
        }
    }

    private String buildInterests(AgentContext context) {
        List<Map<String, Object>> interests = recommendService.getUserInterests(context.getUserId());
        if (interests == null || interests.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("用户兴趣分析:\n");
        for (Map<String, Object> interest : interests) {
            String keywords = interest.get("keywords") != null ? interest.get("keywords").toString() : "";
            if (!keywords.isEmpty()) {
                sb.append("- 关注关键词: ").append(keywords).append("\n");
            }
        }
        sb.append("\n");
        return sb.toString();
    }

    private String recommendItems() {
        try {
            PageResult<IchItemDTO> items = contentService.listItems(1, 3, null, null, 1);
            if (items == null || items.getList() == null || items.getList().isEmpty()) return "";
            StringBuilder sb = new StringBuilder("推荐非遗项目:\n");
            for (IchItemDTO item : items.getList()) {
                sb.append("- ").append(item.getName());
                if (item.getDescription() != null) {
                    sb.append(" - ").append(AgentUtils.truncateDesc(item.getDescription(), 60));
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.debug("推荐非遗项目查询异常: {}", e.getMessage());
            return "";
        }
    }

    private String recommendProducts() {
        try {
            PageResult<ProductDTO> products = productService.listProducts(1, 3, null, null, 1);
            if (products == null || products.getList() == null || products.getList().isEmpty()) return "";
            StringBuilder sb = new StringBuilder("\n推荐文创商品:\n");
            for (ProductDTO product : products.getList()) {
                sb.append("- ").append(product.getName());
                if (product.getPrice() != null) sb.append(", ¥").append(product.getPrice());
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.debug("推荐商品查询异常: {}", e.getMessage());
            return "";
        }
    }
}

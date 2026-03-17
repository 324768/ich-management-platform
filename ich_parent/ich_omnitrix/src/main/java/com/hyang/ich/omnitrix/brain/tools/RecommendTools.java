package com.hyang.ich.omnitrix.brain.tools;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.content.ContentService;
import com.hyang.ich.content.dto.IchItemDTO;
import com.hyang.ich.omnitrix.agent.AgentUtils;
import com.hyang.ich.omnitrix.brain.AiRequestContext;
import com.hyang.ich.omnitrix.service.RecommendService;
import com.hyang.ich.product.ProductService;
import com.hyang.ich.product.dto.ProductDTO;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 智能推荐工具 — 替代原 RecommendSubAgent（SimpleTool 级别）。
 */
@Slf4j
@Component
public class RecommendTools {

    private final RecommendService recommendService;
    private final ContentService contentService;
    private final ProductService productService;

    public RecommendTools(RecommendService recommendService,
                          ContentService contentService,
                          ProductService productService) {
        this.recommendService = recommendService;
        this.contentService = contentService;
        this.productService = productService;
    }

    @Tool("为当前用户推荐非遗项目和文创商品（基于用户兴趣）")
    public String recommend() {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        Long userId = AiRequestContext.getUserId();
        StringBuilder sb = new StringBuilder();

        // 用户兴趣
        try {
            List<Map<String, Object>> interests = recommendService.getUserInterests(userId);
            if (interests != null && !interests.isEmpty()) {
                sb.append("用户兴趣分析:\n");
                for (Map<String, Object> i : interests) {
                    String kw = i.get("keywords") != null ? i.get("keywords").toString() : "";
                    if (!kw.isEmpty()) sb.append("- 关注关键词: ").append(kw).append("\n");
                }
                sb.append("\n");
            }
        } catch (Exception e) {
            log.debug("获取用户兴趣异常: {}", e.getMessage());
        }

        // 推荐非遗项目
        try {
            PageResult<IchItemDTO> items = contentService.listItems(1, 3, null, null, 1);
            if (items != null && items.getList() != null && !items.getList().isEmpty()) {
                sb.append("推荐非遗项目:\n");
                for (IchItemDTO item : items.getList()) {
                    sb.append("- ").append(item.getName());
                    if (item.getDescription() != null)
                        sb.append(" - ").append(AgentUtils.truncateDesc(item.getDescription(), 60));
                    sb.append("\n");
                }
            }
        } catch (Exception e) {
            log.debug("推荐非遗项目异常: {}", e.getMessage());
        }

        // 推荐商品
        try {
            PageResult<ProductDTO> products = productService.listProducts(1, 3, null, null, 1);
            if (products != null && products.getList() != null && !products.getList().isEmpty()) {
                sb.append("\n推荐文创商品:\n");
                for (ProductDTO p : products.getList()) {
                    sb.append("- ").append(p.getName());
                    if (p.getPrice() != null) sb.append(", ¥").append(p.getPrice());
                    sb.append("\n");
                }
            }
        } catch (Exception e) {
            log.debug("推荐商品异常: {}", e.getMessage());
        }

        if (sb.length() == 0) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("暂无推荐内容").withSearchEmptyHint("推荐").toXml();
        }
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.success("已生成个性化推荐", sb.toString())
                .withSearchSuccessHint("推荐内容").toXml();
    }
}

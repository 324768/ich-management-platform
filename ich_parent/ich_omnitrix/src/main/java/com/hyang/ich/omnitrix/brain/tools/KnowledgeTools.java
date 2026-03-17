package com.hyang.ich.omnitrix.brain.tools;

import com.hyang.ich.omnitrix.brain.AiRequestContext;
import com.hyang.ich.omnitrix.entity.AiKnowledgeBase;
import com.hyang.ich.omnitrix.service.KnowledgeService;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 知识库工具 — 替代原 KnowledgeSubAgent（SimpleTool 级别，单工具）。
 */
@Slf4j
@Component
public class KnowledgeTools {

    private final KnowledgeService knowledgeService;

    public KnowledgeTools(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @Tool("搜索知识库FAQ，回答平台常见问题。参数: 用户问题关键词")
    public String searchKnowledge(String query) {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        try {
            List<AiKnowledgeBase> results = knowledgeService.searchAndRerank(query);
            if (results == null || results.isEmpty()) {
                AiRequestContext.recordToolFailure();
                return ToolResultWrapper.empty("知识库中未找到与\"" + query + "\"相关的内容")
                        .withSearchEmptyHint("知识库").toXml();
            }
            StringBuilder sb = new StringBuilder();
            for (AiKnowledgeBase kb : results) {
                sb.append("Q: ").append(kb.getQuestion()).append("\n");
                sb.append("A: ").append(kb.getAnswer()).append("\n\n");
                try { knowledgeService.incrementHitCount(kb.getId()); } catch (Exception ignored) {}
            }
            AiRequestContext.recordToolSuccess();
            return ToolResultWrapper.success("找到" + results.size() + "条知识库匹配", sb.toString())
                    .withSearchSuccessHint("知识库").toXml();
        } catch (Exception e) {
            log.error("知识库搜索异常: {}", e.getMessage(), e);
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.error("知识库搜索时出现异常").withErrorHint().toXml();
        }
    }
}

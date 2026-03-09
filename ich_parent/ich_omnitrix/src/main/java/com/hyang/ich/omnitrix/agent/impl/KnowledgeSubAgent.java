package com.hyang.ich.omnitrix.agent.impl;

import com.hyang.ich.omnitrix.agent.AgentContext;
import com.hyang.ich.omnitrix.agent.SubAgent;
import com.hyang.ich.omnitrix.dto.AgentQueryResult;
import com.hyang.ich.omnitrix.entity.AiKnowledgeBase;
import com.hyang.ich.omnitrix.service.KnowledgeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class KnowledgeSubAgent implements SubAgent {

    private final KnowledgeService knowledgeService;

    public KnowledgeSubAgent(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @Override
    public String getCode() {
        return "knowledge_assistant";
    }

    @Override
    public String getName() {
        return "知识库问答助手";
    }

    @Override
    public String getDescription() {
        return "基于FAQ知识库回答常见问题";
    }

    @Override
    public String getAgentPrompt() {
        return "## 当前任务模式: 知识库问答\n" +
                "系统在知识库中匹配到了相关问答条目，请基于 [查询结果] 中的知识库内容回答。\n" +
                "- 可以适当润色和扩展，但不要改变核心含义\n" +
                "- 如果知识库答案不完整，可以结合你的知识补充，但注明\"据我所知\"";
    }

    @Override
    public AgentQueryResult execute(String userQuery, AgentContext context) {
        try {
            List<AiKnowledgeBase> results = knowledgeService.searchAndRerank(userQuery);

            if (results == null || results.isEmpty()) {
                return AgentQueryResult.empty(getCode());
            }

            StringBuilder data = new StringBuilder();
            data.append("知识库匹配结果:\n");
            for (AiKnowledgeBase kb : results) {
                data.append("Q: ").append(kb.getQuestion()).append("\n");
                data.append("A: ").append(kb.getAnswer()).append("\n\n");

                // 异步增加命中次数
                try {
                    knowledgeService.incrementHitCount(kb.getId());
                } catch (Exception e) {
                    log.debug("更新命中次数失败: {}", e.getMessage());
                }
            }

            return AgentQueryResult.success(data.toString(), getCode());

        } catch (Exception e) {
            log.error("KnowledgeSubAgent 执行异常: {}", e.getMessage(), e);
            return AgentQueryResult.error(getCode(), e.getMessage());
        }
    }
}

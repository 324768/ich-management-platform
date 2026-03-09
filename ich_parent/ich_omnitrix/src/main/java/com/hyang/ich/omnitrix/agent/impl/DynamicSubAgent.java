package com.hyang.ich.omnitrix.agent.impl;

import com.hyang.ich.omnitrix.agent.AgentContext;
import com.hyang.ich.omnitrix.agent.SubAgent;
import com.hyang.ich.omnitrix.dto.AgentQueryResult;
import com.hyang.ich.omnitrix.entity.AiAgentConfig;
import com.hyang.ich.omnitrix.entity.AiKnowledgeBase;
import com.hyang.ich.omnitrix.service.KnowledgeService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 动态子代理：从数据库配置构建，纯 prompt 驱动。
 * 与系统子代理（@Component 硬编码）并行共存。
 * 不是 Spring Bean，由 SubAgentRegistry 动态管理。
 */
@Slf4j
public class DynamicSubAgent implements SubAgent {

    private final AiAgentConfig config;
    private final KnowledgeService knowledgeService;

    public DynamicSubAgent(AiAgentConfig config, KnowledgeService knowledgeService) {
        this.config = config;
        this.knowledgeService = knowledgeService;
    }

    @Override
    public String getCode() {
        return config.getAgentCode();
    }

    @Override
    public String getName() {
        return config.getAgentName();
    }

    @Override
    public String getDescription() {
        return config.getDescription();
    }

    @Override
    public String getAgentPrompt() {
        return config.getSystemPrompt() != null ? config.getSystemPrompt() : "";
    }

    @Override
    public AgentQueryResult execute(String userQuery, AgentContext context) {
        try {
            // 如果绑定了知识库，搜索关联分类的知识库条目
            if (config.getKnowledgeBaseId() != null && knowledgeService != null) {
                List<AiKnowledgeBase> results = knowledgeService.searchByCategory(userQuery, config.getKnowledgeBaseId());
                if (results != null && !results.isEmpty()) {
                    StringBuilder data = new StringBuilder();
                    data.append("关联知识库查询结果:\n");
                    for (AiKnowledgeBase kb : results) {
                        data.append("Q: ").append(kb.getQuestion()).append("\n");
                        data.append("A: ").append(kb.getAnswer()).append("\n\n");
                    }
                    return AgentQueryResult.success(data.toString(), getCode());
                }
            }

            // 动态 Agent 无业务数据查询逻辑，仅依赖 systemPrompt 驱动 LLM
            return AgentQueryResult.empty(getCode());

        } catch (Exception e) {
            log.error("DynamicSubAgent [{}] 执行异常: {}", getCode(), e.getMessage(), e);
            return AgentQueryResult.error(getCode(), e.getMessage());
        }
    }

    /**
     * 获取配置版本号，用于缓存失效判断
     */
    public Long getVersion() {
        return config.getVersion();
    }

    /**
     * 获取底层配置（供 IntentRouter 读取 routingKeywords）
     */
    public AiAgentConfig getConfig() {
        return config;
    }
}

package com.hyang.ich.omnitrix.infrastructure.vector;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 查询改写器 - 将用户原始查询改写为更适合检索的形式
 * 支持多语言改写、关键词扩展、同义词扩展等
 */
@Slf4j
@Component
public class QueryRewriter {

    private final ChatLanguageModel chatModel;

    public QueryRewriter(ChatLanguageModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 改写查询，使其更适合向量检索
     *
     * @param originalQuery 原始用户查询
     * @param language      目标语言（如 "中文"、"English"）
     * @return 改写后的查询
     */
    public String rewrite(String originalQuery, String language) {
        if (originalQuery == null || originalQuery.trim().isEmpty()) {
            return originalQuery;
        }

        String prompt = String.format("""
                你是一个查询优化专家。请将以下用户查询改写为更适合知识库检索的形式。

                原始查询：%s
                目标语言：%s

                要求：
                1. 保留原意，但使用更规范、更全面的表述
                2. 提取关键概念和术语
                3. 补充可能的相关表达方式
                4. 去除口语化表达
                5. 保持语言一致性（如果原文是中文，改写后也用中文）

                只返回改写后的查询语句，不要其他解释。
                """, originalQuery, language);

        try {
            String rewritten = chatModel.chat(prompt);
            log.debug("查询改写: '{}' -> '{}'", originalQuery, rewritten);
            return rewritten.trim();
        } catch (Exception e) {
            log.warn("查询改写失败，使用原查询: {}", e.getMessage());
            return originalQuery;
        }
    }

    /**
     * 批量改写多个查询（用于多轮检索）
     *
     * @param queries 查询列表
     * @param language 目标语言
     * @return 改写后的查询列表
     */
    public java.util.List<String> rewriteBatch(java.util.List<String> queries, String language) {
        return queries.stream()
                .map(q -> rewrite(q, language))
                .collect(java.util.stream.Collectors.toList());
    }
}

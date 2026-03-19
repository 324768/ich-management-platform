package com.hyang.ich.omnitrix.infrastructure.vector;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 相关性评估器 - 评估检索结果与查询的相关性
 * 决定是否需要重写查询或扩展检索
 */
@Slf4j
@Component
public class RelevanceEvaluator {

    private final ChatLanguageModel chatModel;
    private static final double DEFAULT_THRESHOLD = 0.7;

    public RelevanceEvaluator(ChatLanguageModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 评估检索结果的相关性
     *
     * @param query 查询
     * @param result 检索结果
     * @return 相关性评分（0-1）
     */
    public double evaluate(String query, QdrantVectorStore.SearchResult result) {
        if (result == null || result.getText() == null) {
            return 0.0;
        }

        String prompt = String.format("""
                你是一个相关性评估专家。请评估以下查询和内容的相关性。

                查询：%s

                内容：%s

                评分标准（0-1）：
                - 1.0：完全相关，内容直接回答了查询
                - 0.8-0.9：高度相关，内容大部分回答了查询
                - 0.6-0.7：中等相关，内容部分回答了查询
                - 0.4-0.5：低度相关，内容与查询有一定关联但不直接
                - 0.0-0.3：不相关，内容与查询无关

                只返回一个0到1之间的数字，不要其他内容。
                """, query, result.getText());

        try {
            String response = chatModel.chat(prompt).trim();
            double score = parseScore(response);
            log.debug("相关性评估: query='{}', resultId='{}', score={}",
                    query, result.getId(), score);
            return score;
        } catch (Exception e) {
            log.warn("相关性评估失败，使用向量相似度: {}", e.getMessage());
            return result.getScore();
        }
    }

    /**
     * 判断是否需要重写查询
     *
     * @param query 查询
     * @param results 检索结果列表
     * @return true=需要重写，false=不需要
     */
    public boolean needsRewrite(String query, java.util.List<QdrantVectorStore.SearchResult> results) {
        if (results == null || results.isEmpty()) {
            return true;
        }

        double avgScore = results.stream()
                .mapToDouble(QdrantVectorStore.SearchResult::getScore)
                .average()
                .orElse(0.0);

        boolean isLowScore = avgScore < DEFAULT_THRESHOLD;
        boolean hasRelevant = results.stream()
                .anyMatch(r -> r.getScore() >= DEFAULT_THRESHOLD);

        if (isLowScore && !hasRelevant) {
            log.info("检索结果质量低: avgScore={}, 需要重写查询", avgScore);
            return true;
        }

        return false;
    }

    /**
     * 判断是否需要扩展检索
     *
     * @param query 查询
     * @param results 检索结果列表
     * @return true=需要扩展，false=不需要
     */
    public boolean needsExpansion(String query, java.util.List<QdrantVectorStore.SearchResult> results) {
        if (results == null || results.isEmpty()) {
            return true;
        }

        int relevantCount = (int) results.stream()
                .filter(r -> r.getScore() >= DEFAULT_THRESHOLD)
                .count();

        // 如果相关结果少于2个，考虑扩展
        if (relevantCount < 2) {
            log.info("相关结果不足: relevantCount={}, 需要扩展检索", relevantCount);
            return true;
        }

        return false;
    }

    /**
     * 解析评分
     */
    private double parseScore(String response) {
        try {
            String cleaned = response.replaceAll("[^0-9.]", "");
            double score = Double.parseDouble(cleaned);
            return Math.max(0.0, Math.min(1.0, score));
        } catch (NumberFormatException e) {
            log.warn("无法解析评分: {}", response);
            return 0.5;
        }
    }
}

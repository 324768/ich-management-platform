package com.hyang.ich.omnitrix.infrastructure.context;

import org.springframework.stereotype.Component;

/**
 * 上下文策略 - 根据问题类型选择不同的上下文长度和内容
 */
@Component
public class ContextStrategy {

    /**
     * 问题类型枚举
     */
    public enum QueryType {
        SIMPLE_FACT,        // 简单事实查询（短上下文）
        COMPLEX_ANALYSIS,   // 复杂分析（长上下文）
        SHOPPING,           // 购物相关（中上下文）
        RECOMMENDATION,     // 推荐相关（中上下文）
        GENERAL             // 一般对话（默认上下文）
    }

    /**
     * 判断问题类型（使用contains替代正则，更可靠）
     */
    public QueryType classify(String query) {
        if (query == null || query.isEmpty()) {
            return QueryType.GENERAL;
        }

        String lower = query.toLowerCase();

        // 简单事实查询
        if (lower.contains("什么是") || lower.contains("哪一年") ||
            lower.contains("谁发明") || lower.contains("何时") || lower.contains("哪里")) {
            return QueryType.SIMPLE_FACT;
        }

        // 复杂分析
        if (lower.contains("分析") || lower.contains("比较") ||
            lower.contains("对比") || lower.contains("为什么") || lower.contains("如何实现") ||
            lower.contains("原理")) {
            return QueryType.COMPLEX_ANALYSIS;
        }

        // 购物相关
        if (lower.contains("购买") || lower.contains("买") ||
            lower.contains("价格") || lower.contains("多少钱") || lower.contains("优惠") ||
            lower.contains("下单")) {
            return QueryType.SHOPPING;
        }

        // 推荐相关
        if (lower.contains("推荐") || lower.contains("相似") ||
            lower.contains("类似") || lower.contains("喜欢") || lower.contains("感兴趣")) {
            return QueryType.RECOMMENDATION;
        }

        return QueryType.GENERAL;
    }

    /**
     * 获取该问题类型的上下文预算（token）
     */
    public int getContextBudget(QueryType type) {
        return switch (type) {
            case SIMPLE_FACT -> 2000;
            case COMPLEX_ANALYSIS -> 8000;
            case SHOPPING -> 4000;
            case RECOMMENDATION -> 4000;
            case GENERAL -> 6000;
        };
    }

    /**
     * 是否需要注入用户画像
     */
    public boolean needsUserProfile(QueryType type) {
        return type == QueryType.RECOMMENDATION || type == QueryType.SHOPPING;
    }

    /**
     * 是否需要注入 Skills
     */
    public boolean needsSkills(QueryType type) {
        return type != QueryType.SIMPLE_FACT;
    }
}

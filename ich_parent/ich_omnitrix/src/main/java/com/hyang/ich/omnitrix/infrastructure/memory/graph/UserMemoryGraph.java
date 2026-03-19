package com.hyang.ich.omnitrix.infrastructure.memory.graph;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 用户记忆图谱 - 封装 GraphitiService 的高级接口
 * 自动从对话中提取和存储用户偏好
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "omnitrix.graphiti", name = "enabled", havingValue = "true")
public class UserMemoryGraph {

    private final GraphitiService graphitiService;

    public UserMemoryGraph(GraphitiService graphitiService) {
        this.graphitiService = graphitiService;
    }

    /**
     * 从对话中提取并存储用户偏好
     * 根据对话内容自动识别偏好类型
     *
     * @param userId 用户ID
     * @param query 用户查询
     * @param response AI 回答
     */
    public void extractAndStorePreferences(Long userId, String query, String response) {
        try {
            extractCategoryPreference(userId, query, response);
            extractPricePreference(userId, query, response);
            extractOtherPreferences(userId, query, response);
        } catch (Exception e) {
            log.error("提取和存储用户偏好失败: {}", e.getMessage());
        }
    }

    /**
     * 提取类别偏好
     */
    private void extractCategoryPreference(Long userId, String query, String response) {
        List<String> categories = Arrays.asList(
            "剪纸", "刺绣", "陶瓷", "茶艺", "中医药",
            "戏曲", "舞蹈", "音乐", "书法", "国画",
            "漆器", "木雕", "皮影", "年画", "风筝"
        );

        for (String category : categories) {
            if (query.contains(category) || response.contains(category)) {
                graphitiService.addUserPreference(userId, category, "category", category);
                log.debug("提取类别偏好: userId={}, category={}", userId, category);
                break;
            }
        }
    }

    /**
     * 提取价格偏好
     */
    private void extractPricePreference(Long userId, String query, String response) {
        if (query.contains("便宜") || query.contains("实惠")) {
            graphitiService.addUserPreference(userId, "price", "sensitivity", "budget");
        } else if (query.contains("高端") || query.contains("品质") || query.contains("奢华")) {
            graphitiService.addUserPreference(userId, "price", "sensitivity", "premium");
        }
    }

    /**
     * 提取其他偏好
     */
    private void extractOtherPreferences(Long userId, String query, String response) {
        if (query.contains("推荐") || query.contains("喜欢")) {
            graphitiService.addUserPreference(userId, "interest", "type", "recommendation");
        }
    }

    /**
     * 记录用户偏好
     */
    public void recordPreference(Long userId, String category, String value) {
        String entity = category + ":" + value;
        graphitiService.addUserPreference(userId, entity, "preference", value);
    }

    /**
     * 获取用户偏好摘要
     */
    public String getPreferenceSummary(Long userId) {
        Map<String, String> prefs = graphitiService.getCurrentPreferences(userId);
        if (prefs.isEmpty()) {
            return "";
        }

        StringBuilder summary = new StringBuilder();
        summary.append("【用户偏好摘要】\n");
        prefs.forEach((entity, value) -> {
            summary.append("- ").append(entity).append(": ").append(value).append("\n");
        });
        return summary.toString();
    }
}

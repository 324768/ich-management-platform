package com.hyang.ich.omnitrix.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmClient;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 管理员专属意图路由器 —— 仅路由到管理员可用的代理
 * 与用户端 IntentRouter 完全独立，管理员不会访问用户端代理
 */
@Slf4j
@Component
public class AdminIntentRouter {

    private static final String INTENT_PROMPT =
            "你是一个管理后台意图分类器。根据管理员输入，判断最匹配的意图类别。\n\n" +
            "可选类别：admin_data_agent, admin_action_agent, knowledge_assistant, general_assistant\n\n" +
            "类别说明：\n" +
            "- admin_data_agent: 数据统计、分析、收入、订单趋势、库存预警、用户增长、待审批汇总、平台概况\n" +
            "- admin_action_agent: 审批活动、驳回活动、订单发货、发布通知等管理操作\n" +
            "- knowledge_assistant: 非遗知识问答、百科查询\n" +
            "- general_assistant: 以上都不匹配的通用对话\n\n" +
            "严格按JSON格式输出: {\"intent\":\"类别名\"}\n管理员输入: %s";

    private static final Pattern AGENT_PATTERN = Pattern.compile(
            "(admin_data_agent|admin_action_agent|knowledge_assistant|general_assistant)");

    private static final Map<String, String[]> ROUTE_RULES = new LinkedHashMap<>();

    static {
        // 操作类优先匹配（更具体的意图）
        ROUTE_RULES.put("admin_action_agent", new String[]{
                "审批通过", "审批", "通过", "驳回", "拒绝", "审核",
                "发货", "发出", "寄出",
                "发布通知", "发通知", "发公告",
                "上架", "下架"
        });

        // 数据查询类
        ROUTE_RULES.put("admin_data_agent", new String[]{
                "统计", "数据", "分析", "收入", "营收", "销售额", "趋势", "走势",
                "概况", "总览", "报表", "汇总", "多少", "增长", "下降",
                "低库存", "库存预警", "缺货",
                "有多少待审批", "待审批数", "待处理数", "有多少待发货", "待发货数",
                "用户数", "订单数", "今天", "本周", "本月", "近7天",
                "运营", "平台数据", "用户列表", "管理面板"
        });
    }

    private static final String[] FOLLOW_UP_PATTERNS = {
            "还有吗", "还有呢", "继续", "接着说", "然后呢",
            "详细说说", "展开讲讲", "第一个", "第二个", "第三个"
    };

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${omnitrix.intent.llm-fallback:true}")
    private boolean llmFallbackEnabled;

    public AdminIntentRouter(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public String route(String userQuery) {
        return route(userQuery, null);
    }

    public String route(String userQuery, String lastAgentCode) {
        if (userQuery == null || userQuery.trim().isEmpty()) {
            return "general_assistant";
        }

        // 跟随查询复用上一轮 Agent
        if (lastAgentCode != null && !"general_assistant".equals(lastAgentCode)
                && isFollowUpQuery(userQuery)) {
            log.debug("管理员路由: '{}' 识别为跟随查询 → 复用 {}", userQuery, lastAgentCode);
            return lastAgentCode;
        }

        String query = userQuery.toLowerCase();

        // 关键词匹配
        for (Map.Entry<String, String[]> entry : ROUTE_RULES.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (query.contains(keyword)) {
                    log.debug("管理员路由: '{}' 匹配关键词 '{}' → {}", userQuery, keyword, entry.getKey());
                    return entry.getKey();
                }
            }
        }

        // LLM 兜底
        if (llmFallbackEnabled) {
            String llmIntent = classifyByLlm(userQuery);
            if (llmIntent != null && !"general_assistant".equals(llmIntent)) {
                log.info("管理员路由(LLM): '{}' → {}", userQuery, llmIntent);
                return llmIntent;
            }
        }

        // 默认路由到数据分析(管理员大多数问题都是查数据)
        log.debug("管理员路由: '{}' 无匹配 → admin_data_agent", userQuery);
        return "admin_data_agent";
    }

    private boolean isFollowUpQuery(String query) {
        if (query.length() > 15) return false;
        String q = query.trim();
        for (String pattern : FOLLOW_UP_PATTERNS) {
            if (q.contains(pattern)) return true;
        }
        return false;
    }

    private String classifyByLlm(String userQuery) {
        try {
            String prompt = String.format(INTENT_PROMPT, userQuery);
            LlmResponse response = llmClient.chatAuxiliaryJson(prompt, new ArrayList<>(), "请分类");
            String result = response.getContent();
            if (result != null) {
                try {
                    com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(result.trim());
                    if (node.has("intent")) {
                        String intent = node.get("intent").asText().trim().toLowerCase();
                        Matcher m = AGENT_PATTERN.matcher(intent);
                        if (m.find()) return m.group(1);
                    }
                } catch (Exception ignored) {}
                Matcher matcher = AGENT_PATTERN.matcher(result.trim().toLowerCase());
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        } catch (Exception e) {
            log.warn("管理员LLM意图分类失败: {}", e.getMessage());
        }
        return null;
    }
}

package com.hyang.ich.omnitrix.orchestrator;

import com.hyang.ich.omnitrix.infrastructure.llm.LlmClient;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ultra AI 专属意图路由器 —— 可路由到所有 Agent（用户端 + 管理端 + Ultra独有）
 */
@Slf4j
@Component
public class UltraIntentRouter {

    private static final String INTENT_PROMPT =
            "你是 Omnitrix Ultra 超级管理意图分类器。根据输入判断最匹配的意图类别。\n\n" +
            "可选类别：\n" +
            "- ultra_user_control: 控制用户AI开关、禁用/启用某用户的AI、查看AI状态\n" +
            "- ultra_cross_user: 代替用户执行操作(购物车/订单/报名等)，操作其他用户数据\n" +
            "- ultra_system: 系统级操作(删除用户/查看在线用户/系统配置)\n" +
            "- ultra_analytics: 跨用户分析(用户画像/行为分析/兴趣统计)\n" +
            "- ultra_browse_history: 查看某用户的浏览记录/行为轨迹/做了什么\n" +
            "- ultra_security: 安全审计/操作日志/可疑行为检测/安全巡检\n" +
            "- admin_data_agent: 平台数据统计、运营概况\n" +
            "- admin_action_agent: 审批活动、订单发货、发布通知\n" +
            "- content_assistant: 非遗文化、传承人、项目、活动相关\n" +
            "- commerce_assistant: 商品、订单、购物车、文创商城相关\n" +
            "- knowledge_assistant: 知识问答、百科查询\n" +
            "- general_assistant: 通用对话\n\n" +
            "严格按JSON格式输出: {\"intent\":\"类别名\"}\n输入: %s";

    private static final Pattern AGENT_PATTERN = Pattern.compile(
            "(ultra_user_control|ultra_cross_user|ultra_system|ultra_analytics|ultra_browse_history|ultra_security|" +
            "admin_data_agent|admin_action_agent|content_assistant|commerce_assistant|" +
            "knowledge_assistant|general_assistant)");

    private static final Map<String, String[]> ROUTE_RULES = new LinkedHashMap<>();

    static {
        // Ultra 独有能力（最高优先级）
        ROUTE_RULES.put("ultra_user_control", new String[]{
                "关闭AI", "禁用AI", "启用AI", "开启AI", "恢复AI",
                "AI权限", "AI开关", "禁止使用AI", "AI功能",
                "关闭.*的AI", "禁用.*的AI", "启用.*的AI"
        });

        ROUTE_RULES.put("ultra_system", new String[]{
                "删除账号", "删除用户", "封禁用户", "解封用户",
                "在线用户", "在线状态", "有哪些用户在线", "谁在线",
                "用户列表", "所有用户", "查看用户"
        });

        ROUTE_RULES.put("ultra_cross_user", new String[]{
                "给.*购物车", "帮.*加入", "帮.*下单", "帮.*报名",
                "给.*加入购物车", "为.*购物车", "代.*操作",
                "替.*下单", "给.*买"
        });

        ROUTE_RULES.put("ultra_analytics", new String[]{
                "用户画像", "行为分析", "兴趣分析", "偏好分析",
                ".*的画像", ".*的兴趣", ".*的偏好", ".*的行为"
        });

        ROUTE_RULES.put("ultra_browse_history", new String[]{
                "浏览记录", "浏览历史", "看过什么", "看了什么",
                "行为轨迹", "做了什么", "访问记录"
        });

        ROUTE_RULES.put("ultra_security", new String[]{
                "安全审计", "操作日志", "可疑行为", "安全巡检",
                "失败操作", "异常操作", "安全检查", "操作记录"
        });

        // 管理操作
        ROUTE_RULES.put("admin_action_agent", new String[]{
                "审批通过", "审批", "通过", "驳回", "拒绝", "审核",
                "发货", "发出", "寄出", "发布通知", "发通知", "发公告",
                "上架", "下架"
        });

        // 数据查询
        ROUTE_RULES.put("admin_data_agent", new String[]{
                "统计", "数据", "分析", "收入", "营收", "销售额", "趋势",
                "概况", "总览", "报表", "汇总", "多少", "增长",
                "低库存", "库存预警", "缺货", "运营"
        });

        // 内容
        ROUTE_RULES.put("content_assistant", new String[]{
                "非遗", "传承人", "非物质文化", "京剧", "皮影", "剪纸",
                "活动", "展览", "文化", "遗产"
        });

        // 商城
        ROUTE_RULES.put("commerce_assistant", new String[]{
                "商品", "购买", "订单", "购物车", "价格", "库存", "文创",
                "多少钱", "便宜", "实惠", "贵", "预算", "块钱", "元",
                "推荐", "有什么", "看看", "有没有"
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

    public UltraIntentRouter(LlmClient llmClient) {
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
            log.debug("Ultra路由: '{}' 识别为跟随查询 → 复用 {}", userQuery, lastAgentCode);
            return lastAgentCode;
        }

        String query = userQuery.toLowerCase();

        // 关键词匹配（Ultra 关键词支持正则模式）
        for (Map.Entry<String, String[]> entry : ROUTE_RULES.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (keyword.contains(".*")) {
                    // 正则匹配
                    try {
                        if (query.matches(".*" + keyword + ".*")) {
                            log.debug("Ultra路由: '{}' 匹配正则 '{}' → {}", userQuery, keyword, entry.getKey());
                            return entry.getKey();
                        }
                    } catch (Exception ignored) {}
                } else {
                    if (query.contains(keyword)) {
                        log.debug("Ultra路由: '{}' 匹配关键词 '{}' → {}", userQuery, keyword, entry.getKey());
                        return entry.getKey();
                    }
                }
            }
        }

        // LLM 兜底
        if (llmFallbackEnabled) {
            String llmIntent = classifyByLlm(userQuery);
            if (llmIntent != null && !"general_assistant".equals(llmIntent)) {
                log.info("Ultra路由(LLM): '{}' → {}", userQuery, llmIntent);
                return llmIntent;
            }
        }

        log.debug("Ultra路由: '{}' 无匹配 → general_assistant", userQuery);
        return "general_assistant";
    }

    private boolean isFollowUpQuery(String query) {
        if (query.length() > 15) return false;
        for (String pattern : FOLLOW_UP_PATTERNS) {
            if (query.trim().contains(pattern)) return true;
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
                if (matcher.find()) return matcher.group(1);
            }
        } catch (Exception e) {
            log.warn("Ultra LLM意图分类失败: {}", e.getMessage());
        }
        return null;
    }
}

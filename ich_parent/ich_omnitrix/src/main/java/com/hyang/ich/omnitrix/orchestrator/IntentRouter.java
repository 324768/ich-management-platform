package com.hyang.ich.omnitrix.orchestrator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyang.ich.omnitrix.agent.SubAgentRegistry;
import com.hyang.ich.omnitrix.agent.impl.DynamicSubAgent;
import com.hyang.ich.omnitrix.entity.AiAgentConfig;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmClient;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmResponse;
import com.hyang.ich.omnitrix.service.KnowledgeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class IntentRouter {

    private static final String INTENT_PROMPT =
            "你是一个意图分类器。根据用户输入，判断最匹配的意图类别。\n\n" +
            "可选类别：content_assistant, commerce_assistant, " +
            "user_assistant, recommend_assistant, knowledge_assistant, general_assistant\n\n" +
            "类别说明：\n" +
            "- content_assistant: 非遗文化、传承人、项目、活动、动态、帖子、点赞、收藏、评论相关\n" +
            "- commerce_assistant: 商品、订单、购物车、文创商城、下单、付款、发货相关\n" +
            "- user_assistant: 个人信息、地址、认证、通知相关\n" +
            "- recommend_assistant: 推荐、猜你喜欢相关\n" +
            "- knowledge_assistant: 知识问答、百科查询相关\n" +
            "- general_assistant: 以上都不匹配的通用对话\n\n" +
            "严格按JSON格式输出: {\"intent\":\"类别名\"}\n用户输入: %s";

    private static final Pattern AGENT_PATTERN = Pattern.compile(
            "(content_assistant|commerce_assistant|user_assistant|" +
            "recommend_assistant|knowledge_assistant|general_assistant)");

    private final KnowledgeService knowledgeService;
    private final SubAgentRegistry subAgentRegistry;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${omnitrix.intent.llm-fallback:true}")
    private boolean llmFallbackEnabled;

    public IntentRouter(KnowledgeService knowledgeService, @Lazy SubAgentRegistry subAgentRegistry,
                        LlmClient llmClient) {
        this.knowledgeService = knowledgeService;
        this.subAgentRegistry = subAgentRegistry;
        this.llmClient = llmClient;
    }

    private static final Map<String, String[]> ROUTE_RULES = new LinkedHashMap<>();

    static {
        // 优先级1: 非遗内容 + 动态/帖子交互
        ROUTE_RULES.put("content_assistant", new String[]{
                "非遗", "传承人", "非物质文化", "京剧", "皮影", "剪纸", "刺绣",
                "活动", "展览", "文化", "遗产", "项目", "民间", "手工艺", "戏曲",
                "技艺", "陶瓷", "木偶", "苏绣", "昆曲", "民俗", "国家级",
                "报名", "点赞", "取消点赞", "收藏", "取消收藏", "评论",
                "动态", "帖子", "我的动态", "我的收藏"
        });

        // 优先级2: 商城/订单/购物车
        ROUTE_RULES.put("commerce_assistant", new String[]{
                "商品", "购买", "订单", "购物车", "发货", "价格", "库存", "文创",
                "上架", "付款", "收货", "退货", "物流", "下单", "买",
                "加入购物车", "加购物车", "清空购物车", "移除", "数量",
                "结算", "确认收货", "取消订单"
        });

        // 优先级3: 用户个人
        ROUTE_RULES.put("user_assistant", new String[]{
                "我的信息", "地址", "资格认证", "传承人认证", "修改密码",
                "个人", "收货地址", "我的资料", "个人信息",
                "默认地址", "删除地址", "通知", "消息"
        });

        // 优先级4: 浏览历史
        ROUTE_RULES.put("browse_history_assistant", new String[]{
                "浏览记录", "浏览历史", "看过什么", "看过的", "浏览了什么",
                "今天看了", "昨天看了", "最近看了", "历史记录"
        });

        // 优先级5: 推荐
        ROUTE_RULES.put("recommend_assistant", new String[]{
                "推荐", "有什么好看", "类似的", "相关的", "感兴趣", "猜你喜欢"
        });
    }

    /** 短查询 / 模糊跟随词，应复用上一轮 Agent */
    private static final String[] FOLLOW_UP_PATTERNS = {
            "还有吗", "还有呢", "继续", "接着说", "然后呢", "还有其他",
            "详细说说", "展开讲讲", "多说一点", "举个例子",
            "第一个", "第二个", "第三个", "上面的", "刚才的",
            "是什么", "怎么样", "好的", "明白了", "谢谢"
    };

    /**
     * 根据用户输入匹配最佳子代理
     */
    public String route(String userQuery) {
        return route(userQuery, null);
    }

    /**
     * 上下文感知路由：对短/模糊查询自动复用上一轮 Agent
     */
    public String route(String userQuery, String lastAgentCode) {
        if (userQuery == null || userQuery.trim().isEmpty()) {
            return "general_assistant";
        }

        // 上下文感知：短查询 + 有上一轮 Agent → 复用
        if (lastAgentCode != null && !"general_assistant".equals(lastAgentCode)
                && isFollowUpQuery(userQuery)) {
            log.debug("意图路由: '{}' 识别为跟随查询 → 复用 {}", userQuery, lastAgentCode);
            return lastAgentCode;
        }

        String query = userQuery.toLowerCase();

        // 优先级2: 关键词匹配（内存操作，零延迟）
        for (Map.Entry<String, String[]> entry : ROUTE_RULES.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (query.contains(keyword)) {
                    log.debug("意图路由: '{}' 匹配关键词 '{}' → {}", userQuery, keyword, entry.getKey());
                    return entry.getKey();
                }
            }
        }

        // 优先级3: 知识库命中检测（需DB查询，放在关键词之后）
        try {
            if (knowledgeService.hasMatch(userQuery)) {
                log.debug("意图路由: '{}' 命中知识库 → knowledge_assistant", userQuery);
                return "knowledge_assistant";
            }
        } catch (Exception e) {
            log.debug("知识库查询异常(忽略): {}", e.getMessage());
        }

        // 优先级4: 动态代理路由关键词匹配
        try {
            for (DynamicSubAgent dynamicAgent : subAgentRegistry.getDynamicAgents()) {
                AiAgentConfig config = dynamicAgent.getConfig();
                if (config != null && config.getRoutingKeywords() != null) {
                    List<String> keywords = objectMapper.readValue(
                            config.getRoutingKeywords(), new TypeReference<List<String>>() {});
                    for (String kw : keywords) {
                        if (query.contains(kw.toLowerCase())) {
                            log.debug("意图路由: '{}' 匹配动态代理关键词 '{}' → {}",
                                    userQuery, kw, dynamicAgent.getCode());
                            return dynamicAgent.getCode();
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("动态代理路由匹配异常(忽略): {}", e.getMessage());
        }

        // 优先级5: LLM 意图分类（兜底）
        if (llmFallbackEnabled) {
            String llmIntent = classifyByLlm(userQuery);
            if (llmIntent != null && !"general_assistant".equals(llmIntent)) {
                log.info("意图路由(LLM): '{}' → {}", userQuery, llmIntent);
                return llmIntent;
            }
        }

        log.debug("意图路由: '{}' 无匹配 → general_assistant", userQuery);
        return "general_assistant";
    }

    /**
     * 判断是否为跟随/追问类短查询
     */
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
                // 优先尝试解析 JSON {"intent":"xxx"}
                try {
                    com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(result.trim());
                    if (node.has("intent")) {
                        String intent = node.get("intent").asText().trim().toLowerCase();
                        Matcher m = AGENT_PATTERN.matcher(intent);
                        if (m.find()) return m.group(1);
                    }
                } catch (Exception ignored) {}
                // 回退: 正则匹配
                Matcher matcher = AGENT_PATTERN.matcher(result.trim().toLowerCase());
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        } catch (Exception e) {
            log.warn("LLM 意图分类失败(回退关键词): {}", e.getMessage());
        }
        return null;
    }

}

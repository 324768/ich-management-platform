package com.hyang.ich.omnitrix.agent.tool;

import com.hyang.ich.omnitrix.dto.AgentQueryResult;

import java.util.HashMap;
import java.util.Map;

/**
 * 智能体 hint 三元组生成器（借鉴 KortexAI SubAgentResultWrapper）。
 *
 * 根据 Agent 类型和工具名称，自动为 AgentQueryResult 附加
 * ACTION / REASON / DO_NOT 提示，引导主脑 LLM 生成更精准的回复。
 *
 * 用法：HintGenerator.applyHints(result, agentCode, toolName)
 */
public class HintGenerator {

    private static final Map<String, String[]> HINT_TEMPLATES = new HashMap<>();

    static {
        // ===== ContentSubAgent =====
        reg("content_assistant", "search_items",
                "向用户展示搜索到的非遗项目，按相关度排列，引导用户了解详情",
                "用户正在探索非遗文化内容",
                "不要编造平台中不存在的非遗项目名称或信息");
        reg("content_assistant", "search_heritage_men",
                "向用户介绍搜索到的传承人，突出其技艺特色和成就",
                "用户对非遗传承人感兴趣",
                "不要编造传承人的个人信息或履历");
        reg("content_assistant", "search_activities",
                "向用户展示活动列表，突出时间、地点和报名状态，提示可报名的活动",
                "用户在查找非遗活动",
                "不要编造活动的时间、地点或报名人数");
        reg("content_assistant", "search_all",
                "综合展示非遗项目和传承人搜索结果，帮助用户全面了解",
                "用户发起了综合搜索",
                "不要混淆项目和传承人信息");
        reg("content_assistant", "query_user_posts",
                "展示用户发布的动态列表",
                "用户查看自己的内容",
                "不要透露其他用户的私人动态");
        reg("content_assistant", "query_user_favorites",
                "展示用户收藏的动态列表，可提示用户查看或取消收藏",
                "用户查看自己的收藏",
                "不要建议收藏不存在的内容");

        // ===== CommerceSubAgent =====
        reg("commerce_assistant", "search_products",
                "向用户展示商品搜索结果，突出价格和库存信息，引导用户加购",
                "用户在浏览文创商品",
                "不要编造商品价格或库存数量，必须精确引用查询结果");
        reg("commerce_assistant", "query_orders",
                "向用户展示订单列表和状态，提供可用操作（支付/取消/确认收货）",
                "用户在查看订单",
                "不要推测物流进度或预计送达时间");
        reg("commerce_assistant", "query_cart",
                "向用户展示购物车内容和总计，提示可下单或修改",
                "用户在查看购物车",
                "不要自动触发下单操作");
        reg("commerce_assistant", "add_to_cart",
                "明确告诉用户已找到商品，并提示回复「好的」或「确认」即可加入购物车，不要说系统维护或暂不可用",
                "用户要将某商品加入购物车",
                "必须基于查询结果中的商品名称和价格回复，并引导用户确认");

        // ===== UserSubAgent =====
        reg("user_assistant", "query_profile",
                "展示用户的个人信息",
                "用户查看个人资料",
                "不要暴露敏感信息如完整手机号");
        reg("user_assistant", "query_addresses",
                "展示用户的收货地址列表",
                "用户管理收货地址",
                "不要建议删除所有地址");
        reg("user_assistant", "query_notifications",
                "展示用户的通知消息，按时间排列",
                "用户查看通知",
                "不要编造不存在的通知内容");

        // ===== RecommendSubAgent =====
        reg("recommend_assistant", "recommend",
                "向用户推荐内容或商品，说明推荐理由",
                "用户需要个性化推荐",
                "不要推荐不存在的内容或商品，严格基于查询结果");

        // ===== KnowledgeSubAgent =====
        reg("knowledge_assistant", "search",
                "基于知识库内容回答用户的非遗问题，注明信息来源",
                "用户在询问非遗知识",
                "不要将知识库内容与自身训练知识混淆，区分来源");
    }

    private static void reg(String agent, String tool, String action, String reason, String doNot) {
        HINT_TEMPLATES.put(agent + "::" + tool, new String[]{action, reason, doNot});
    }

    /**
     * 为 AgentQueryResult 自动附加 hint 三元组。
     * 如果没有匹配的模板，写操作会附加通用确认 hint。
     */
    public static AgentQueryResult applyHints(AgentQueryResult result,
                                               String agentCode, String toolName) {
        if (result == null || agentCode == null) return result;

        // 精确匹配 agent::tool
        if (toolName != null) {
            String[] hints = HINT_TEMPLATES.get(agentCode + "::" + toolName);
            if (hints != null) {
                return result.withHints(hints[0], hints[1], hints[2]);
            }
        }

        // 写操作通用 hint
        if (result.getStatus() == AgentQueryResult.Status.ACTION_PROPOSED) {
            return result.withHints(
                    "向用户确认操作详情，列出关键信息后询问是否执行",
                    "用户发起了需要确认的写操作",
                    "不要自动执行操作，必须等待用户明确确认");
        }

        // EMPTY 通用 hint
        if (result.getStatus() == AgentQueryResult.Status.EMPTY) {
            return result.withHints(
                    "告知用户平台暂无收录该内容，可建议换个关键词或浏览推荐",
                    "平台数据库未命中用户查询",
                    "不要编造不存在的数据来填补空白");
        }

        return result;
    }
}

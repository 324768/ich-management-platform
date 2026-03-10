package com.hyang.ich.omnitrix.blackboard;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmClient;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 主脑（MasterBrain）—— LLM 驱动的智能任务规划器。
 *
 * 混合模式的核心：
 * - 显式分解：基于规则的快速路径判断（低成本）
 * - LLM 驱动：基于语义理解的智能决策（高灵活性）
 *
 * 职责：
 * 1. 判断任务复杂度（isComplexQuery）
 * 2. 决定是否需要 LLM 驱动（shouldUseLlmBrain）
 * 3. LLM 驱动分解：让 LLM 决定调用哪些 Agent、如何调用
 * 4. 将决策结果写入黑板
 * 5. 动态再规划：根据执行结果决定是否调整计划
 *
 * 与 TaskDecomposer 的区别：
 * - TaskDecomposer：规则 + LLM 分解，主要依赖 LLM 做分解
 * - MasterBrain：规则 + LLM 决策，不仅分解，还做执行策略决策
 */
@Slf4j
@Component
public class MasterBrain {

    /** 快速判断是否可能是复杂查询的关键词 */
    private static final String[][] CROSS_DOMAIN_HINTS = {
            {"活动", "商品"}, {"活动", "订单"}, {"报名", "购买"},
            {"搜索", "加入购物车"}, {"推荐", "购买"}, {"推荐", "报名"},
            {"非遗", "下单"}, {"传承人", "商品"}, {"收藏", "购买"},
            {"活动", "购物车"}, {"报名", "加购"}, {"点赞", "下单"},
            // 管理员跨域
            {"审批", "统计"}, {"发货", "数据"}, {"审批", "发货"},
            {"通知", "审批"}, {"统计", "发布"}
    };

    private static final Pattern JSON_PATTERN = Pattern.compile("\\{.*}", Pattern.DOTALL);

    /** 智能决策 Prompt：让 LLM 决定执行策略 */
    private static final String DECISION_PROMPT =
            "你是一个智能任务规划专家。分析用户请求，决定最佳执行策略。\n\n" +
            "【可用的执行路径】\n" +
            "1. quick_path：单 Agent 直接执行（简单查询、闲聊）\n" +
            "2. blackboard：多 Agent 协作（复杂任务、需要多步操作）\n\n" +
            "【可用的子智能体】\n" +
            "- content_assistant: 非遗项目/传承人/活动 搜索、报名活动、点赞/收藏/评论动态\n" +
            "- commerce_assistant: 商品搜索、购物车操作、订单操作\n" +
            "- user_assistant: 个人信息、地址、认证、通知\n" +
            "- browse_history_assistant: 浏览历史记录查询\n" +
            "- recommend_assistant: 推荐内容/商品\n" +
            "- knowledge_assistant: 非遗知识问答\n" +
            "- general_assistant: 通用闲聊\n\n" +
            "【决策规则】\n" +
            "- 如果请求只涉及单一领域且意图明确 → quick_path\n" +
            "- 如果请求跨越多个领域或有多个步骤 → blackboard\n" +
            "- 如果请求包含\"然后\"、\"接着\"、\"同时\"等连接词 → blackboard\n" +
            "- 如果请求需要先获取信息再做决策 → blackboard\n\n" +
            "【输出格式】（严格JSON）\n" +
            "快速路径: {\"path\":\"quick_path\",\"agent\":\"content_assistant\",\"reason\":\"单领域简单查询\"}\n" +
            "多Agent路径: {\"path\":\"blackboard\",\"tasks\":[\n" +
            "  {\"agent\":\"content_assistant\",\"query\":\"搜索剪纸活动\",\"dependsOn\":[]},\n" +
            "  {\"agent\":\"commerce_assistant\",\"query\":\"搜索剪纸文创商品\",\"dependsOn\":[]}\n" +
            "],\"reason\":\"跨领域查询\"}\n\n" +
            "【用户请求】\n%s\n\n" +
            "JSON:";

    /** 管理员版本的决策 Prompt */
    private static final String ADMIN_DECISION_PROMPT =
            "你是一个智能任务规划专家。分析管理员请求，决定最佳执行策略。\n\n" +
            "【可用的执行路径】\n" +
            "1. quick_path：单 Agent 直接执行\n" +
            "2. blackboard：多 Agent 协作\n\n" +
            "【可用的子智能体】\n" +
            "- admin_data_agent: 数据统计、分析、报表\n" +
            "- admin_action_agent: 审批活动、发货、发布通知等管理操作\n" +
            "- knowledge_assistant: 非遗知识问答\n" +
            "- general_assistant: 通用闲聊\n\n" +
            "【决策规则】\n" +
            "- 单一操作 → quick_path\n" +
            "- 多个操作、需要先查数据再做操作 → blackboard\n\n" +
            "【输出格式】（严格JSON）\n" +
            "快速路径: {\"path\":\"quick_path\",\"agent\":\"admin_data_agent\",\"reason\":\"数据查询\"}\n" +
            "多Agent路径: {\"path\":\"blackboard\",\"tasks\":[\n" +
            "  {\"agent\":\"admin_data_agent\",\"query\":\"查看待审批活动\",\"dependsOn\":[]},\n" +
            "  {\"agent\":\"admin_action_agent\",\"query\":\"审批通过活动ID=10\",\"dependsOn\":[0]}\n" +
            "],\"reason\":\"需要先查询再操作\"}\n\n" +
            "【管理员请求】\n%s\n\n" +
            "JSON:";

    /** 再规划 Prompt：审视中间结果，动态调整计划 */
    private static final String REPLAN_PROMPT =
            "你是任务动态规划器。用户的原始请求和已完成任务的结果如下，判断是否需要调整执行计划。\n\n" +
            "【可用的子智能体】\n" +
            "- content_assistant: 非遗项目/传承人/活动\n" +
            "- commerce_assistant: 商品、订单\n" +
            "- user_assistant: 用户信息\n" +
            "- recommend_assistant: 推荐\n" +
            "- knowledge_assistant: 知识问答\n\n" +
            "【原始请求】\n%s\n\n" +
            "【已完成任务及结果】\n%s\n\n" +
            "【决策规则】\n" +
            "1. 如果原始请求已完全满足 → 无需追加\n" +
            "2. 如果结果中有可用的 ID/名称，可以追加更精确的后续任务 → 追加任务\n" +
            "3. 如果发现遗漏的意图 → 追加任务\n" +
            "4. 不要重复已完成的任务\n" +
            "5. 最多追加3个任务\n\n" +
            "【输出格式】（严格JSON）\n" +
            "无需追加: {\"replan\":false,\"reason\":\"请求已满足\"}\n" +
            "需要追加: {\"replan\":true,\"tasks\":[\n" +
            "  {\"agent\":\"content_assistant\",\"query\":\"报名ID=42的剪纸活动\"}\n" +
            "],\"reason\":\"发现可用的活动ID\"}\n\n" +
            "JSON:";

    private static final int MAX_TASKS = 5;
    private static final int MAX_REPLAN_TASKS = 3;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MasterBrain(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * 快速判断是否可能是复杂查询（零 LLM 调用）
     */
    public boolean isComplexQuery(String userQuery) {
        if (userQuery == null || userQuery.length() < 6) return false;
        String q = userQuery.toLowerCase();
        for (String[] pair : CROSS_DOMAIN_HINTS) {
            if (q.contains(pair[0]) && q.contains(pair[1])) {
                log.debug("MasterBrain: 检测到跨域关键词 ['{}', '{}']", pair[0], pair[1]);
                return true;
            }
        }
        if (q.contains("然后") || q.contains("接着") || q.contains("之后")
                || q.contains("同时") || q.contains("顺便") || q.contains("并且")) {
            log.debug("MasterBrain: 检测到多步骤连接词");
            return true;
        }
        return false;
    }

    /**
     * 决定是否使用 LLM 驱动
     *
     * 策略：
     * - 简单查询 → 规则判断，不需要 LLM
     * - 复杂查询 → 使用 LLM 智能决策
     */
    public boolean shouldUseLlmBrain(String userQuery) {
        // 先用规则快速判断
        if (!isComplexQuery(userQuery)) {
            return false;
        }
        // 规则判断为复杂，使用 LLM 进一步决策
        return true;
    }

    /**
     * 智能决策：让 LLM 决定执行路径和任务分解
     *
     * @param userQuery 用户请求
     * @param isAdmin   是否管理员
     * @return 决策结果
     */
    public Decision decide(String userQuery, boolean isAdmin) {
        try {
            String prompt = String.format(isAdmin ? ADMIN_DECISION_PROMPT : DECISION_PROMPT, userQuery);
            LlmResponse response = llmClient.chatAuxiliaryJson(prompt, new ArrayList<>(), "MasterBrain决策");
            String content = response.getContent();

            if (content == null || content.isEmpty()) {
                log.debug("MasterBrain: LLM 返回空，使用默认快速路径");
                return Decision.quickPath("general_assistant", "LLM返回空，默认快速路径");
            }

            String json = extractJson(content);
            if (json == null) {
                log.debug("MasterBrain: 无法提取JSON，使用默认快速路径");
                return Decision.quickPath("general_assistant", "JSON解析失败，默认快速路径");
            }

            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});

            String path = (String) parsed.get("path");
            String reason = (String) parsed.getOrDefault("reason", "");

            if ("blackboard".equals(path)) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> tasks = (List<Map<String, Object>>) parsed.get("tasks");
                if (tasks == null || tasks.isEmpty()) {
                    return Decision.quickPath("general_assistant", "无任务，默认为快速路径");
                }
                // 限制任务数
                if (tasks.size() > MAX_TASKS) {
                    tasks = tasks.subList(0, MAX_TASKS);
                }
                return Decision.blackboard(tasks, reason);
            } else {
                // quick_path
                String agent = (String) parsed.get("agent");
                if (agent == null) {
                    agent = "general_assistant";
                }
                return Decision.quickPath(agent, reason);
            }

        } catch (Exception e) {
            log.warn("MasterBrain 决策失败，使用默认快速路径: {}", e.getMessage());
            return Decision.quickPath("general_assistant", "决策异常，默认快速路径");
        }
    }

    /**
     * 动态再规划：根据执行结果决定是否调整计划
     */
    public ReplanResult replan(String userQuery, String completedSummary) {
        try {
            String prompt = String.format(REPLAN_PROMPT, userQuery, completedSummary);
            LlmResponse response = llmClient.chatAuxiliaryJson(prompt, new ArrayList<>(), "MasterBrain再规划");
            String content = response.getContent();

            if (content == null || content.isEmpty()) {
                return ReplanResult.noReplan("LLM返回空");
            }

            String json = extractJson(content);
            if (json == null) {
                return ReplanResult.noReplan("JSON解析失败");
            }

            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});

            Object replanFlag = parsed.get("replan");
            if (replanFlag == null || !Boolean.TRUE.equals(replanFlag)) {
                String reason = (String) parsed.getOrDefault("reason", "无需调整");
                return ReplanResult.noReplan(reason);
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> newTasks = (List<Map<String, Object>>) parsed.get("tasks");
            if (newTasks == null || newTasks.isEmpty()) {
                return ReplanResult.noReplan("无新任务");
            }

            if (newTasks.size() > MAX_REPLAN_TASKS) {
                newTasks = newTasks.subList(0, MAX_REPLAN_TASKS);
            }

            String reason = (String) parsed.getOrDefault("reason", "动态调整");
            return ReplanResult.replan(newTasks, reason);

        } catch (Exception e) {
            log.warn("MasterBrain 再规划失败: {}", e.getMessage());
            return ReplanResult.noReplan("再规划异常");
        }
    }

    /**
     * 将决策结果转换为 TaskBoard
     */
    public TaskBoard toTaskBoard(Decision decision) {
        TaskBoard board = new TaskBoard();
        if (decision.getPath() != Decision.Path.BLACKBOARD) {
            return board;
        }

        List<TaskNode> createdNodes = new ArrayList<>();
        for (Decision.Task task : decision.getTasks()) {
            TaskNode node = board.create(task.getAgent(), task.getQuery());
            createdNodes.add(node);
        }

        // 设置依赖
        for (int i = 0; i < decision.getTasks().size() && i < createdNodes.size(); i++) {
            Decision.Task task = decision.getTasks().get(i);
            for (Integer depIndex : task.getDependsOn()) {
                if (depIndex >= 0 && depIndex < createdNodes.size() && depIndex != i) {
                    board.depend(createdNodes.get(i).getId(), createdNodes.get(depIndex).getId());
                }
            }
        }

        return board;
    }

    private String extractJson(String content) {
        if (content == null) return null;
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        }
        Matcher matcher = JSON_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    /**
     * 决策结果
     */
    @Data
    public static class Decision {
        public enum Path { QUICK_PATH, BLACKBOARD }

        private Path path;
        private String agent;          // quick_path 时使用
        private List<Task> tasks;      // blackboard 时使用
        private String reason;

        public static Decision quickPath(String agent, String reason) {
            Decision d = new Decision();
            d.path = Path.QUICK_PATH;
            d.agent = agent;
            d.reason = reason;
            return d;
        }

        public static Decision blackboard(List<Map<String, Object>> tasks, String reason) {
            Decision d = new Decision();
            d.path = Path.BLACKBOARD;
            d.reason = reason;
            d.tasks = new ArrayList<>();
            for (Map<String, Object> taskDef : tasks) {
                Task t = new Task();
                t.setAgent((String) taskDef.get("agent"));
                t.setQuery((String) taskDef.get("query"));
                Object depsObj = taskDef.get("dependsOn");
                if (depsObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> deps = (List<Object>) depsObj;
                    t.setDependsOn(new ArrayList<>());
                    for (Object dep : deps) {
                        if (dep instanceof Number) {
                            t.getDependsOn().add(((Number) dep).intValue());
                        }
                    }
                }
                d.tasks.add(t);
            }
            return d;
        }

        public boolean isQuickPath() {
            return path == Path.QUICK_PATH;
        }

        public boolean isBlackboard() {
            return path == Path.BLACKBOARD;
        }

        @Data
        public static class Task {
            private String agent;
            private String query;
            private List<Integer> dependsOn = new ArrayList<>();
        }
    }

    /**
     * 再规划结果
     */
    @Data
    public static class ReplanResult {
        private boolean needReplan;
        private List<Decision.Task> newTasks;
        private String reason;

        public static ReplanResult noReplan(String reason) {
            ReplanResult r = new ReplanResult();
            r.needReplan = false;
            r.reason = reason;
            return r;
        }

        public static ReplanResult replan(List<Map<String, Object>> taskDefs, String reason) {
            ReplanResult r = new ReplanResult();
            r.needReplan = true;
            r.reason = reason;
            r.newTasks = new ArrayList<>();
            for (Map<String, Object> taskDef : taskDefs) {
                Decision.Task t = new Decision.Task();
                t.setAgent((String) taskDef.get("agent"));
                t.setQuery((String) taskDef.get("query"));
                r.newTasks.add(t);
            }
            return r;
        }
    }
}

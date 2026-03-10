package com.hyang.ich.omnitrix.blackboard;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmClient;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 主脑（Main Brain）—— 唯一的"发牌官"（Write-Only）。
 *
 * 职责：
 * 1. 判断用户查询是否为复杂多步骤任务（isComplexQuery）
 * 2. 将复杂任务拆解为粒度足够小的 TaskNode，钉在黑板上（decompose）
 * 3. 排好先后顺序（设置依赖）
 *
 * 权限：只能执行 bd create（创建任务）和 bd depend（设置依赖）
 * 禁忌：绝对不执行 bd ready 去抢活干
 * 发完牌后，主脑直接退出，不占用后续流程
 */
@Slf4j
@Component
public class TaskDecomposer {

    private static final String DECOMPOSE_PROMPT =
            "你是一个任务分解器。分析用户的请求，判断是否需要多个子智能体协作完成。\n\n" +
            "可用的子智能体：\n" +
            "- content_assistant: 非遗项目/传承人/活动 搜索、报名活动、点赞/收藏/评论动态\n" +
            "- commerce_assistant: 商品搜索、购物车操作、订单操作\n" +
            "- user_assistant: 个人信息、地址、认证、通知\n" +
            "- browse_history_assistant: 浏览历史记录查询\n" +
            "- recommend_assistant: 推荐内容/商品\n" +
            "- knowledge_assistant: 非遗知识问答\n" +
            "- general_assistant: 通用闲聊\n\n" +
            "分析规则：\n" +
            "1. 如果请求只涉及单一领域，输出 {\"complex\":false}\n" +
            "2. 如果请求跨越多个领域或有先后步骤，输出任务列表\n\n" +
            "输出格式（严格JSON）：\n" +
            "单任务: {\"complex\":false}\n" +
            "多任务: {\"complex\":true,\"tasks\":[\n" +
            "  {\"agent\":\"content_assistant\",\"query\":\"搜索剪纸相关活动\",\"dependsOn\":[]},\n" +
            "  {\"agent\":\"commerce_assistant\",\"query\":\"搜索剪纸相关文创商品\",\"dependsOn\":[]},\n" +
            "  {\"agent\":\"content_assistant\",\"query\":\"报名第一个剪纸活动\",\"dependsOn\":[0]}\n" +
            "]}\n\n" +
            "dependsOn 中的数字是 tasks 数组的索引（0-based），表示该任务依赖哪些前置任务。\n" +
            "用户请求: %s";

    /** 管理员版本的分解提示 */
    private static final String ADMIN_DECOMPOSE_PROMPT =
            "你是一个管理后台任务分解器。分析管理员的请求，判断是否需要多个步骤协作完成。\n\n" +
            "可用的子智能体：\n" +
            "- admin_data_agent: 数据统计、分析、报表\n" +
            "- admin_action_agent: 审批活动、发货、发布通知等管理操作\n" +
            "- knowledge_assistant: 非遗知识问答\n" +
            "- general_assistant: 通用闲聊\n\n" +
            "分析规则：\n" +
            "1. 如果请求只涉及单一操作，输出 {\"complex\":false}\n" +
            "2. 如果请求包含多个管理操作，输出任务列表\n\n" +
            "输出格式（严格JSON）：\n" +
            "单任务: {\"complex\":false}\n" +
            "多任务: {\"complex\":true,\"tasks\":[\n" +
            "  {\"agent\":\"admin_data_agent\",\"query\":\"查看待审批活动\",\"dependsOn\":[]},\n" +
            "  {\"agent\":\"admin_action_agent\",\"query\":\"审批通过第一个活动\",\"dependsOn\":[0]}\n" +
            "]}\n\n" +
            "dependsOn 中的数字是 tasks 数组的索引（0-based）。\n" +
            "管理员请求: %s";

    /** 再规划提示词：主脑审视中间结果后决定是否追加任务 */
    private static final String REPLAN_PROMPT =
            "你是任务再规划器。用户的原始请求和已完成任务的结果如下，请判断是否需要追加新任务。\n\n" +
            "可用的子智能体：\n" +
            "- content_assistant: 非遗项目/传承人/活动 搜索、报名活动、点赞/收藏/评论动态\n" +
            "- commerce_assistant: 商品搜索、购物车操作、订单操作\n" +
            "- user_assistant: 个人信息、地址、认证、通知\n" +
            "- recommend_assistant: 推荐内容/商品\n" +
            "- knowledge_assistant: 非遗知识问答\n\n" +
            "原始用户请求：%s\n\n" +
            "已完成的任务及结果：\n%s\n\n" +
            "判断规则：\n" +
            "1. 对照原始请求，检查是否所有意图都已被覆盖\n" +
            "2. 如果已完成的结果中包含可用于后续操作的具体信息（如ID、名称），可以追加更精确的后续任务\n" +
            "3. 不要重复已完成的任务\n" +
            "4. 最多追加3个新任务\n\n" +
            "输出格式（严格JSON）：\n" +
            "无需追加: {\"replan\":false}\n" +
            "需要追加: {\"replan\":true,\"tasks\":[\n" +
            "  {\"agent\":\"content_assistant\",\"query\":\"报名ID=42的剪纸活动\"}\n" +
            "]}";

    /** 管理员版本的再规划提示词 */
    private static final String ADMIN_REPLAN_PROMPT =
            "你是管理后台任务再规划器。管理员的原始请求和已完成任务的结果如下，请判断是否需要追加新任务。\n\n" +
            "可用的子智能体：\n" +
            "- admin_data_agent: 数据统计、分析、报表\n" +
            "- admin_action_agent: 审批活动、发货、发布通知等管理操作\n" +
            "- knowledge_assistant: 非遗知识问答\n\n" +
            "原始管理员请求：%s\n\n" +
            "已完成的任务及结果：\n%s\n\n" +
            "判断规则：\n" +
            "1. 对照原始请求，检查是否所有意图都已被覆盖\n" +
            "2. 如果已完成的结果中包含可用于后续操作的具体信息（如ID、名称），可以追加更精确的后续任务\n" +
            "3. 不要重复已完成的任务\n" +
            "4. 最多追加3个新任务\n\n" +
            "输出格式（严格JSON）：\n" +
            "无需追加: {\"replan\":false}\n" +
            "需要追加: {\"replan\":true,\"tasks\":[\n" +
            "  {\"agent\":\"admin_action_agent\",\"query\":\"审批通过ID=10的活动\"}\n" +
            "]}";

    private static final int MAX_REPLAN_TASKS = 3;

    /** 快速判断是否可能是复杂查询的关键词组合 */
    private static final String[][] CROSS_DOMAIN_HINTS = {
            // {领域A关键词, 领域B关键词} - 同时出现则可能跨域
            {"活动", "商品"}, {"活动", "订单"}, {"报名", "购买"},
            {"搜索", "加入购物车"}, {"推荐", "购买"}, {"推荐", "报名"},
            {"非遗", "下单"}, {"传承人", "商品"}, {"收藏", "购买"},
            {"活动", "购物车"}, {"报名", "加购"}, {"点赞", "下单"},
            // 管理员跨域
            {"审批", "统计"}, {"发货", "数据"}, {"审批", "发货"},
            {"通知", "审批"}, {"统计", "发布"}
    };

    private static final Pattern JSON_PATTERN = Pattern.compile("\\{.*}", Pattern.DOTALL);

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TaskDecomposer(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * 快速判断用户查询是否可能需要多智能体协作。
     * 使用关键词交叉检测（零延迟），避免对简单查询浪费 LLM 调用。
     */
    public boolean isComplexQuery(String userQuery) {
        if (userQuery == null || userQuery.length() < 6) return false;
        String q = userQuery.toLowerCase();
        for (String[] pair : CROSS_DOMAIN_HINTS) {
            if (q.contains(pair[0]) && q.contains(pair[1])) {
                log.debug("主脑: 检测到跨域关键词 ['{}', '{}'] → 可能是复杂查询", pair[0], pair[1]);
                return true;
            }
        }
        // 包含 "然后"、"接着"、"之后"、"同时"、"顺便" 等连接词也暗示多步骤
        if (q.contains("然后") || q.contains("接着") || q.contains("之后")
                || q.contains("同时") || q.contains("顺便") || q.contains("并且")) {
            log.debug("主脑: 检测到多步骤连接词 → 可能是复杂查询");
            return true;
        }
        return false;
    }

    /**
     * 主脑核心方法：将复杂查询分解为 TaskBoard 上的原子任务。
     *
     * 主脑发完牌后直接退出（返回 TaskBoard），不参与后续执行。
     *
     * @param userQuery 用户原始查询
     * @param isAdmin   是否管理员
     * @return 填充好任务的黑板，如果分解失败返回 null（回退到单Agent路径）
     */
    public TaskBoard decompose(String userQuery, boolean isAdmin) {
        try {
            String prompt = String.format(isAdmin ? ADMIN_DECOMPOSE_PROMPT : DECOMPOSE_PROMPT, userQuery);
            LlmResponse response = llmClient.chatAuxiliaryJson(prompt, new ArrayList<>(), userQuery);
            String content = response.getContent();

            if (content == null || content.isEmpty()) {
                log.debug("主脑: LLM 返回空，回退单Agent路径");
                return null;
            }

            // 提取 JSON
            String json = extractJson(content);
            if (json == null) {
                log.debug("主脑: 无法提取JSON，回退单Agent路径");
                return null;
            }

            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});

            // 简单查询
            Object complexFlag = parsed.get("complex");
            if (complexFlag == null || !Boolean.TRUE.equals(complexFlag)) {
                log.debug("主脑: 判定为简单查询 → 回退单Agent路径");
                return null;
            }

            // 复杂查询：构建黑板
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tasks = (List<Map<String, Object>>) parsed.get("tasks");
            if (tasks == null || tasks.size() < 2) {
                log.debug("主脑: tasks 不足2个 → 回退单Agent路径");
                return null;
            }

            // 限制最大任务数，防止 LLM 幻觉生成过多任务
            if (tasks.size() > 5) {
                log.warn("主脑: LLM 生成了 {} 个任务，截断到5个", tasks.size());
                tasks = tasks.subList(0, 5);
            }

            TaskBoard board = new TaskBoard();
            List<TaskNode> createdNodes = new ArrayList<>();

            // Phase 1: bd create —— 在黑板上创建所有任务
            for (Map<String, Object> taskDef : tasks) {
                String agent = (String) taskDef.get("agent");
                String query = (String) taskDef.get("query");
                if (agent == null || query == null) continue;
                TaskNode node = board.create(agent, query);
                createdNodes.add(node);
            }

            // Phase 2: bd depend —— 设置依赖关系
            for (int i = 0; i < tasks.size() && i < createdNodes.size(); i++) {
                Object depsObj = tasks.get(i).get("dependsOn");
                if (depsObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> deps = (List<Object>) depsObj;
                    for (Object dep : deps) {
                        int depIndex;
                        if (dep instanceof Integer) {
                            depIndex = (Integer) dep;
                        } else if (dep instanceof Number) {
                            depIndex = ((Number) dep).intValue();
                        } else {
                            continue;
                        }
                        if (depIndex >= 0 && depIndex < createdNodes.size() && depIndex != i) {
                            board.depend(createdNodes.get(i).getId(), createdNodes.get(depIndex).getId());
                        }
                    }
                }
            }

            log.info("主脑发牌完成: {}个任务已钉在黑板上\n{}", board.size(), board);
            return board;

        } catch (Exception e) {
            log.warn("主脑任务分解失败，回退单Agent路径: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 主脑再规划：审视已完成任务的中间结果，决定是否追加新任务。
     *
     * 工作流程：
     * 1. 收集已完成任务的摘要
     * 2. 调用辅助 LLM 判断是否需要追加任务
     * 3. 将新任务铉在黑板上
     *
     * @param board       当前黑板
     * @param userQuery   用户原始请求
     * @param isAdmin     是否管理员
     * @return 新追加的任务数，0表示无需再规划
     */
    public int replan(TaskBoard board, String userQuery, boolean isAdmin) {
        if (board == null || board.getPendingCount() > 0) {
            // 还有未完成的任务，等它们完成后再再规划
            return 0;
        }

        String completedSummary = board.getCompletedSummary();
        if (completedSummary.isEmpty()) {
            return 0;
        }

        try {
            String prompt = String.format(
                    isAdmin ? ADMIN_REPLAN_PROMPT : REPLAN_PROMPT,
                    userQuery, completedSummary);
            LlmResponse response = llmClient.chatAuxiliaryJson(prompt, new ArrayList<>(), "再规划");
            String content = response.getContent();

            if (content == null || content.isEmpty()) {
                log.debug("主脑再规划: LLM返回空，无需追加");
                return 0;
            }

            String json = extractJson(content);
            if (json == null) return 0;

            Map<String, Object> parsed = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});

            Object replanFlag = parsed.get("replan");
            if (replanFlag == null || !Boolean.TRUE.equals(replanFlag)) {
                log.debug("主脑再规划: 判定无需追加任务");
                return 0;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> newTasks = (List<Map<String, Object>>) parsed.get("tasks");
            if (newTasks == null || newTasks.isEmpty()) {
                return 0;
            }

            // 截断保护
            if (newTasks.size() > MAX_REPLAN_TASKS) {
                newTasks = newTasks.subList(0, MAX_REPLAN_TASKS);
            }

            int added = 0;
            for (Map<String, Object> taskDef : newTasks) {
                String agent = (String) taskDef.get("agent");
                String query = (String) taskDef.get("query");
                if (agent == null || query == null) continue;
                board.create(agent, query);
                added++;
            }

            if (added > 0) {
                log.info("主脑再规划: 追加了 {} 个新任务\n{}", added, board);
            }
            return added;

        } catch (Exception e) {
            log.warn("主脑再规划失败: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 从 LLM 返回内容中提取 JSON 部分
     */
    private String extractJson(String content) {
        if (content == null) return null;
        String trimmed = content.trim();
        // 去掉 Markdown 代码块
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        }
        Matcher matcher = JSON_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }
}

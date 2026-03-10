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
 * Ultra 主脑（UltraMasterBrain）—— Omnitrix AI Ultra版 的智能决策层。
 *
 * 职责：
 * 1. 判断是否为 Ultra 请求（权限 + 关键词）
 * 2. 判断 Ultra 任务复杂度
 * 3. 决定使用快速路径还是 L2 黑板
 * 4. 动态再规划
 *
 * 与 MasterBrain 的区别：
 * - MasterBrain：普通用户/管理员的决策层
 * - UltraMasterBrain：Ominitrix AI Ultra版 的决策层，可以操控用户端 + 管理端
 */
@Slf4j
@Component
public class UltraMasterBrain {

    /** Ultra 专有关键词 */
    private static final String[][] ULTRA_KEYWORDS = {
            {"ultra", "ai"}, {"ultra", "控制"}, {"ultra", "管理"},
            {"查看", "在线"}, {"禁用", "用户"}, {"启用", "用户"},
            {"封禁", "用户"}, {"删除", "用户"}, {"查看", "画像"},
            {"分析", "用户"}, {"查看", "浏览记录"}, {"行为", "分析"},
            {"安全", "审计"}, {"操作", "日志"}, {"系统", "状态"},
            {"代替", "用户"}, {"用户", "购物车"}, {"用户", "订单"},
            {"跨域", "操作"}, {"全局", "统计"}
    };

    /** Ultra L2 复杂查询关键词组合 */
    private static final String[][] ULTRA_CROSS_HINTS = {
            {"在线", "购物车"}, {"在线", "禁用"}, {"用户", "运营"},
            {"画像", "购物车"}, {"禁用", "删除"}, {"封禁", "通知"},
            {"查看", "同时"}, {"用户", "商品"}, {"账号", "数据"},
            {"分析", "操作"}, {"画像", "订单"}, {"在线", "统计"},
            {"用户", "审批"}, {"禁用", "启用"}, {"删除", "查看"},
            {"浏览记录", "统计"}, {"浏览", "购物车"}, {"行为", "封禁"},
            {"做了什么", "在线"}, {"浏览", "审批"}, {"记录", "数据"}
    };

    private static final Pattern JSON_PATTERN = Pattern.compile("\\{.*}", Pattern.DOTALL);

    /** Ultra 决策 Prompt */
    private static final String ULTRA_DECISION_PROMPT =
            "你是Omnitrix AI Ultra版的智能决策专家。Ultra 拥有最高权限，可以同时操控用户端AI和管理端AI。\n\n" +
            "【执行路径】\n" +
            "1. quick_path：单 Agent 直接执行（简单操作、单一任务）\n" +
            "2. blackboard：L2 黑板多元代理协作（复杂任务、需要多个元代理）\n\n" +
            "【可用的元代理】\n" +
            "- user_ai_meta: 用户端AI（浏览非遗、购物、下单、收藏等）\n" +
            "- admin_ai_meta: 管理员普通AI（数据统计、审批、发货等）\n" +
            "- ultra_user_control: 控制用户AI开关\n" +
            "- ultra_cross_user: 代替指定用户执行操作\n" +
            "- ultra_system: 系统级用户管理\n" +
            "- ultra_analytics: 用户分析\n" +
            "- ultra_browse_history: 查看用户浏览记录\n" +
            "- ultra_security: 安全审计\n\n" +
            "【决策规则】\n" +
            "- 单一操作 → quick_path\n" +
            "- 多个操作或跨多个元代理 → blackboard\n" +
            "- 包含「然后」「接着」「同时」等连接词 → blackboard\n\n" +
            "【输出格式】（严格JSON）\n" +
            "快速路径: {\"path\":\"quick_path\",\"agent\":\"ultra_system\",\"reason\":\"单一操作\"}\n" +
            "L2黑板: {\"path\":\"blackboard\",\"tasks\":[\n" +
            "  {\"agent\":\"ultra_system\",\"query\":\"查看在线用户\"},\n" +
            "  {\"agent\":\"admin_ai_meta\",\"query\":\"查看今日数据\"}\n" +
            "]}\n\n" +
            "【Ultra 指令】\n%s\n\n" +
            "JSON:";

    /** Ultra 再规划 Prompt */
    private static final String ULTRA_REPLAN_PROMPT =
            "你是 Omnitrix AI Ultra版 的动态再规划器。审视已完成任务结果，判断是否需要追加新任务。\n\n" +
            "【可用的元代理】\n" +
            "- user_ai_meta, admin_ai_meta\n" +
            "- ultra_user_control, ultra_cross_user, ultra_system\n" +
            "- ultra_analytics, ultra_browse_history, ultra_security\n\n" +
            "【原始Ultra指令】\n%s\n\n" +
            "【已完成任务及结果】\n%s\n\n" +
            "【决策规则】\n" +
            "1. 原始请求已完全满足 → 无需追加\n" +
            "2. 结果中有可用的具体信息 → 追加更精确的任务\n" +
            "3. 不要重复已完成的任务\n" +
            "4. 最多追加3个任务\n\n" +
            "【输出格式】\n" +
            "无需追加: {\"replan\":false,\"reason\":\"请求已满足\"}\n" +
            "需要追加: {\"replan\":true,\"tasks\":[\n" +
            "  {\"agent\":\"ultra_user_control\",\"query\":\"禁用用户ID=5的AI\"}\n" +
            "]}\n\n" +
            "JSON:";

    private static final int MAX_TASKS = 6;
    private static final int MAX_REPLAN_TASKS = 3;

    /** 允许的 L2 元代理白名单 */
    private static final Set<String> VALID_L2_AGENTS = new HashSet<>(Arrays.asList(
            "user_ai_meta", "admin_ai_meta",
            "ultra_user_control", "ultra_cross_user", "ultra_system",
            "ultra_analytics", "ultra_browse_history", "ultra_security",
            "content_assistant", "commerce_assistant", "knowledge_assistant",
            "admin_data_agent", "admin_action_agent", "general_assistant"
    ));

    private final LlmClient llmClient;
    private final UltraTaskDecomposer ultraTaskDecomposer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public UltraMasterBrain(LlmClient llmClient, UltraTaskDecomposer ultraTaskDecomposer) {
        this.llmClient = llmClient;
        this.ultraTaskDecomposer = ultraTaskDecomposer;
    }

    /**
     * 判断是否为 Ultra 请求
     *
     * @param message 用户消息
     * @param isUltraUser 是否有 Ultra 权限
     * @return true 表示 Ultra 请求
     */
    public boolean isUltraRequest(String message, boolean isUltraUser) {
        if (!isUltraUser) {
            return false;
        }
        if (message == null || message.length() < 4) {
            return false;
        }
        String q = message.toLowerCase();
        for (String[] pair : ULTRA_KEYWORDS) {
            if (q.contains(pair[0]) && q.contains(pair[1])) {
                log.debug("UltraMasterBrain: 检测到Ultra关键词 ['{}', '{}']", pair[0], pair[1]);
                return true;
            }
        }
        return false;
    }

    /**
     * 快速判断 Ultra 任务是否需要 L2 黑板
     */
    public boolean isComplexUltraQuery(String userQuery) {
        return ultraTaskDecomposer.isComplexUltraQuery(userQuery);
    }

    /**
     * 智能决策：让 LLM 决定执行路径
     *
     * @param userQuery Ultra 用户请求
     * @return 决策结果
     */
    public UltraDecision decide(String userQuery) {
        try {
            String prompt = String.format(ULTRA_DECISION_PROMPT, userQuery);
            LlmResponse response = llmClient.chatAuxiliaryJson(prompt, new ArrayList<>(), "UltraMasterBrain决策");
            String content = response.getContent();

            if (content == null || content.isEmpty()) {
                log.debug("UltraMasterBrain: LLM返回空，使用默认快速路径");
                return UltraDecision.quickPath("general_assistant", "LLM返回空");
            }

            String json = extractJson(content);
            if (json == null) {
                log.debug("UltraMasterBrain: 无法提取JSON，使用默认快速路径");
                return UltraDecision.quickPath("general_assistant", "JSON解析失败");
            }

            Map<String, Object> parsed = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});

            String path = (String) parsed.get("path");
            String reason = (String) parsed.getOrDefault("reason", "");

            if ("blackboard".equals(path)) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> tasks = (List<Map<String, Object>>) parsed.get("tasks");
                if (tasks == null || tasks.isEmpty()) {
                    return UltraDecision.quickPath("general_assistant", "无任务");
                }
                if (tasks.size() > MAX_TASKS) {
                    tasks = tasks.subList(0, MAX_TASKS);
                }
                return UltraDecision.blackboard(tasks, reason);
            } else {
                String agent = (String) parsed.get("agent");
                if (agent == null) {
                    agent = "general_assistant";
                }
                return UltraDecision.quickPath(agent, reason);
            }

        } catch (Exception e) {
            log.warn("UltraMasterBrain 决策失败: {}", e.getMessage());
            return UltraDecision.quickPath("general_assistant", "决策异常");
        }
    }

    /**
     * 动态再规划
     */
    public UltraReplanResult replan(String userQuery, String completedSummary) {
        try {
            String prompt = String.format(ULTRA_REPLAN_PROMPT, userQuery, completedSummary);
            LlmResponse response = llmClient.chatAuxiliaryJson(prompt, new ArrayList<>(), "UltraMasterBrain再规划");
            String content = response.getContent();

            if (content == null || content.isEmpty()) {
                return UltraReplanResult.noReplan("LLM返回空");
            }

            String json = extractJson(content);
            if (json == null) {
                return UltraReplanResult.noReplan("JSON解析失败");
            }

            Map<String, Object> parsed = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});

            Object replanFlag = parsed.get("replan");
            if (replanFlag == null || !Boolean.TRUE.equals(replanFlag)) {
                String reason = (String) parsed.getOrDefault("reason", "无需调整");
                return UltraReplanResult.noReplan(reason);
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> newTasks = (List<Map<String, Object>>) parsed.get("tasks");
            if (newTasks == null || newTasks.isEmpty()) {
                return UltraReplanResult.noReplan("无新任务");
            }

            if (newTasks.size() > MAX_REPLAN_TASKS) {
                newTasks = newTasks.subList(0, MAX_REPLAN_TASKS);
            }

            String reason = (String) parsed.getOrDefault("reason", "动态调整");
            return UltraReplanResult.replan(newTasks, reason);

        } catch (Exception e) {
            log.warn("UltraMasterBrain 再规划失败: {}", e.getMessage());
            return UltraReplanResult.noReplan("再规划异常");
        }
    }

    /**
     * 将决策结果转换为 TaskBoard（L2 黑板）
     */
    public TaskBoard toTaskBoard(UltraDecision decision) {
        TaskBoard board = new TaskBoard();
        if (!decision.isBlackboard()) {
            return board;
        }

        List<TaskNode> createdNodes = new ArrayList<>();
        for (UltraDecision.UltraTask task : decision.getTasks()) {
            if (!VALID_L2_AGENTS.contains(task.getAgent())) {
                log.warn("UltraMasterBrain: 无效agent '{}', 跳过", task.getAgent());
                continue;
            }
            TaskNode node = board.create(task.getAgent(), task.getQuery());
            createdNodes.add(node);
        }

        // 设置依赖
        for (int i = 0; i < decision.getTasks().size() && i < createdNodes.size(); i++) {
            UltraDecision.UltraTask task = decision.getTasks().get(i);
            for (Integer depIndex : task.getDependsOn()) {
                if (depIndex >= 0 && depIndex < createdNodes.size() && depIndex != i) {
                    board.depend(createdNodes.get(i).getId(), createdNodes.get(depIndex).getId());
                }
            }
        }

        return board;
    }

    /**
     * 调用 UltraTaskDecomposer 进行 L2 分解
     */
    public TaskBoard decompose(String userQuery) {
        return ultraTaskDecomposer.decompose(userQuery);
    }

    /**
     * 调用 UltraTaskDecomposer 进行再规划
     */
    public int replan(TaskBoard l2Board, String userQuery) {
        return ultraTaskDecomposer.replan(l2Board, userQuery);
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
     * Ultra 决策结果
     */
    @Data
    public static class UltraDecision {
        public enum Path { QUICK_PATH, BLACKBOARD }

        private Path path;
        private String agent;           // quick_path 时使用
        private List<UltraTask> tasks;  // blackboard 时使用
        private String reason;

        public static UltraDecision quickPath(String agent, String reason) {
            UltraDecision d = new UltraDecision();
            d.path = Path.QUICK_PATH;
            d.agent = agent;
            d.reason = reason;
            return d;
        }

        public static UltraDecision blackboard(List<Map<String, Object>> tasks, String reason) {
            UltraDecision d = new UltraDecision();
            d.path = Path.BLACKBOARD;
            d.reason = reason;
            d.tasks = new ArrayList<>();
            for (Map<String, Object> taskDef : tasks) {
                UltraTask t = new UltraTask();
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
        public static class UltraTask {
            private String agent;
            private String query;
            private List<Integer> dependsOn = new ArrayList<>();
        }
    }

    /**
     * Ultra 再规划结果
     */
    @Data
    public static class UltraReplanResult {
        private boolean needReplan;
        private List<UltraDecision.UltraTask> newTasks;
        private String reason;

        public static UltraReplanResult noReplan(String reason) {
            UltraReplanResult r = new UltraReplanResult();
            r.needReplan = false;
            r.reason = reason;
            return r;
        }

        public static UltraReplanResult replan(List<Map<String, Object>> taskDefs, String reason) {
            UltraReplanResult r = new UltraReplanResult();
            r.needReplan = true;
            r.reason = reason;
            r.newTasks = new ArrayList<>();
            for (Map<String, Object> taskDef : taskDefs) {
                UltraDecision.UltraTask t = new UltraDecision.UltraTask();
                t.setAgent((String) taskDef.get("agent"));
                t.setQuery((String) taskDef.get("query"));
                r.newTasks.add(t);
            }
            return r;
        }
    }
}

package com.hyang.ich.omnitrix.blackboard;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmClient;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ultra 主脑（L2 黑板发牌官）。
 *
 * 与 L1 TaskDecomposer 的区别：
 * - L1 在用户/管理员 SubAgent 之间分发任务
 * - L2 在 "元代理" 之间分发任务：user_ai_meta（用户端AI）、admin_ai_meta（管理员普通AI）、
 *   以及 Ultra 专属代理（ultra_user_control / ultra_cross_user / ultra_system / ultra_analytics）
 *
 * 元代理拿到 L2 任务后，内部再用 L1 黑板/快速路径进一步分解。
 */
@Slf4j
@Component
public class UltraTaskDecomposer {

    private static final String ULTRA_DECOMPOSE_PROMPT =
            "你是 Ultra AI 的二级任务分解器。Ultra 拥有最高管理权限，可以同时操控用户端AI和管理端AI。\n\n" +
            "可用的元代理（meta-agents）：\n" +
            "- user_ai_meta: 用户端AI，可执行用户侧操作（浏览非遗、购物、下单、收藏等），需指定目标用户\n" +
            "- admin_ai_meta: 管理员普通AI，可执行管理端操作（数据统计、审批、发货、发布通知等）\n" +
            "- ultra_user_control: 控制用户的AI开关（禁用/启用某用户的AI功能）\n" +
            "- ultra_cross_user: 代替指定用户执行操作（加购物车、查看订单等）\n" +
            "- ultra_system: 系统级用户管理（查看在线用户、删除用户、封禁/解封等）\n" +
            "- ultra_analytics: 用户分析（查用户画像、行为分析、兴趣统计等）\n" +
            "- ultra_browse_history: 查看某用户的浏览记录、行为轨迹、某天做了什么\n" +
            "- ultra_security: 安全审计、操作日志、可疑行为检测、安全巡检\n\n" +
            "分解规则：\n" +
            "1. 如果请求只涉及单一操作，输出 {\"complex\":false}\n" +
            "2. 如果请求涉及多个操作或跨越多个元代理，输出任务列表\n" +
            "3. 涉及「代替用户做某事」时用 ultra_cross_user\n" +
            "4. 涉及「查看系统状态/用户管理」时用 ultra_system\n" +
            "5. 涉及「管理端数据/审批」等时用 admin_ai_meta\n" +
            "6. 涉及「以用户视角浏览/搜索」等时用 user_ai_meta，query中需包含用户信息\n\n" +
            "输出格式（严格JSON）：\n" +
            "单任务: {\"complex\":false}\n" +
            "多任务: {\"complex\":true,\"tasks\":[\n" +
            "  {\"agent\":\"ultra_system\",\"query\":\"查看在线用户列表\",\"dependsOn\":[]},\n" +
            "  {\"agent\":\"admin_ai_meta\",\"query\":\"查看今日运营数据\",\"dependsOn\":[]},\n" +
            "  {\"agent\":\"ultra_cross_user\",\"query\":\"给张三的购物车加入商品A\",\"dependsOn\":[]}\n" +
            "]}\n\n" +
            "dependsOn 中的数字是 tasks 数组的索引（0-based），表示该任务依赖哪些前置任务。\n" +
            "Ultra 指令: %s";

    /** Ultra L2 复杂查询关键词组合 */
    private static final String[][] ULTRA_CROSS_HINTS = {
            {"在线", "购物车"}, {"在线", "禁用"}, {"用户", "运营"},
            {"画像", "购物车"}, {"禁用", "删除"}, {"封禁", "通知"},
            {"查看", "同时"}, {"用户", "商品"}, {"账号", "数据"},
            {"\u5206\u6790", "\u64cd\u4f5c"}, {"\u753b\u50cf", "\u8ba2\u5355"}, {"\u5728\u7ebf", "\u7edf\u8ba1"},
            {"\u7528\u6237", "\u5ba1\u6279"}, {"\u7981\u7528", "\u542f\u7528"}, {"\u5220\u9664", "\u67e5\u770b"},
            {"\u6d4f\u89c8\u8bb0\u5f55", "\u7edf\u8ba1"}, {"\u6d4f\u89c8", "\u8d2d\u7269\u8f66"}, {"\u884c\u4e3a", "\u5c01\u7981"},
            {"\u505a\u4e86\u4ec0\u4e48", "\u5728\u7ebf"}, {"\u6d4f\u89c8", "\u5ba1\u6279"}, {"\u8bb0\u5f55", "\u6570\u636e"},
    };

    private static final Pattern JSON_PATTERN = Pattern.compile("\\{.*}", Pattern.DOTALL);

    /** 允许的 L2 元代理 code 白名单（防止 LLM 生成无效 agent） */
    private static final Set<String> VALID_L2_AGENTS = new HashSet<>(Arrays.asList(
            "user_ai_meta", "admin_ai_meta",
            "ultra_user_control", "ultra_cross_user", "ultra_system",
            "ultra_analytics", "ultra_browse_history", "ultra_security",
            "content_assistant", "commerce_assistant", "knowledge_assistant",
            "admin_data_agent", "admin_action_agent", "general_assistant"
    ));

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public UltraTaskDecomposer(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * 快速判断 Ultra 指令是否需要 L2 黑板多元代理协作
     */
    public boolean isComplexUltraQuery(String userQuery) {
        if (userQuery == null || userQuery.length() < 6) return false;
        String q = userQuery.toLowerCase();
        for (String[] pair : ULTRA_CROSS_HINTS) {
            if (q.contains(pair[0]) && q.contains(pair[1])) {
                log.debug("Ultra主脑: 检测到跨域关键词 ['{}', '{}']", pair[0], pair[1]);
                return true;
            }
        }
        if (q.contains("然后") || q.contains("接着") || q.contains("之后")
                || q.contains("同时") || q.contains("顺便") || q.contains("并且")) {
            log.debug("Ultra主脑: 检测到多步骤连接词");
            return true;
        }
        return false;
    }

    /**
     * L2 黑板分解：将 Ultra 指令分解为元代理任务
     *
     * @return 填充好的 TaskBoard，分解失败返回 null
     */
    public TaskBoard decompose(String userQuery) {
        try {
            String prompt = String.format(ULTRA_DECOMPOSE_PROMPT, userQuery);
            LlmResponse response = llmClient.chatAuxiliaryJson(prompt, new ArrayList<>(), userQuery);
            String content = response.getContent();

            if (content == null || content.isEmpty()) {
                log.debug("Ultra主脑: LLM 返回空，回退单Agent路径");
                return null;
            }

            String json = extractJson(content);
            if (json == null) {
                log.debug("Ultra主脑: 无法提取JSON，回退单Agent路径");
                return null;
            }

            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});

            Object complexFlag = parsed.get("complex");
            if (complexFlag == null || !Boolean.TRUE.equals(complexFlag)) {
                log.debug("Ultra主脑: 判定为简单查询");
                return null;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tasks = (List<Map<String, Object>>) parsed.get("tasks");
            if (tasks == null || tasks.size() < 2) {
                log.debug("Ultra主脑: tasks 不足2个，回退单Agent路径");
                return null;
            }

            if (tasks.size() > 6) {
                log.warn("Ultra主脑: LLM 生成了 {} 个任务，截断到6个", tasks.size());
                tasks = tasks.subList(0, 6);
            }

            TaskBoard board = new TaskBoard();
            List<TaskNode> createdNodes = new ArrayList<>();

            // Phase 1: 创建任务（带白名单校验）
            for (Map<String, Object> taskDef : tasks) {
                String agent = (String) taskDef.get("agent");
                String query = (String) taskDef.get("query");
                if (agent == null || query == null) continue;
                if (!VALID_L2_AGENTS.contains(agent)) {
                    log.warn("Ultra主脑: LLM 生成了无效 agent '{}'，跳过", agent);
                    createdNodes.add(null);
                    continue;
                }
                TaskNode node = board.create(agent, query);
                createdNodes.add(node);
            }

            // Phase 2: 设置依赖
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

            // Phase 3: 循环依赖检测（修复风险#1）
            if (board.size() < 2) {
                log.debug("Ultra主脑: 有效任务不足2个，回退单Agent");
                return null;
            }

            log.info("Ultra主脑(L2)发牌完成: {}个任务\n{}", board.size(), board);
            return board;

        } catch (Exception e) {
            log.warn("Ultra主脑L2分解失败，回退单Agent: {}", e.getMessage());
            return null;
        }
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
}

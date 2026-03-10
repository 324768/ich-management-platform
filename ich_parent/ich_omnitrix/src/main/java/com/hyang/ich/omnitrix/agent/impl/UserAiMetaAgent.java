package com.hyang.ich.omnitrix.agent.impl;

import com.hyang.ich.omnitrix.agent.AgentContext;
import com.hyang.ich.omnitrix.agent.SubAgent;
import com.hyang.ich.omnitrix.agent.SubAgentRegistry;
import com.hyang.ich.omnitrix.blackboard.TaskBoard;
import com.hyang.ich.omnitrix.blackboard.TaskDecomposer;
import com.hyang.ich.omnitrix.blackboard.TaskNode;
import com.hyang.ich.omnitrix.dto.AgentQueryResult;
import com.hyang.ich.omnitrix.orchestrator.IntentRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 用户端 AI 元代理 —— Ultra L2 黑板上的一个 "元子智能体"。
 *
 * 当 Ultra 主脑通过 L2 黑板把任务分发给 user_ai_meta 时，
 * 该元代理内部会启动 L1 流程：
 * 1. 用 IntentRouter（用户端）路由到具体的用户端 SubAgent
 * 2. 如果 TaskDecomposer 判定为复杂查询，则使用 L1 黑板并行执行
 * 3. 否则走快速路径直接执行单个 SubAgent
 *
 * 这实现了 "Ultra → L2黑板 → 用户端AI → L1黑板 → 用户端SubAgents" 的层级编排。
 */
@Slf4j
@Component
public class UserAiMetaAgent implements SubAgent {

    private static final int L1_TIMEOUT_SECONDS = 8;
    private static final int L1_MAX_ROUNDS = 3;
    private static final int L1_MAX_REPLANS = 1;

    private final IntentRouter intentRouter;
    private final SubAgentRegistry subAgentRegistry;
    private final TaskDecomposer taskDecomposer;
    private final Executor agentExecutor;

    public UserAiMetaAgent(IntentRouter intentRouter,
                           @Lazy SubAgentRegistry subAgentRegistry,
                           TaskDecomposer taskDecomposer,
                           @org.springframework.beans.factory.annotation.Qualifier("aiAsyncExecutor") Executor agentExecutor) {
        this.intentRouter = intentRouter;
        this.subAgentRegistry = subAgentRegistry;
        this.taskDecomposer = taskDecomposer;
        this.agentExecutor = agentExecutor;
    }

    @Override
    public String getCode() { return "user_ai_meta"; }

    @Override
    public String getName() { return "用户端AI"; }

    @Override
    public String getDescription() { return "用户端AI元代理，通过L1黑板调用用户端子智能体"; }

    @Override
    public String getAgentPrompt() {
        return "你是用户端AI的代理入口。将收到的指令路由到用户端的子智能体执行。";
    }

    @Override
    public AgentQueryResult execute(String userQuery, AgentContext context) {
        log.info("UserAiMetaAgent 收到L2任务: '{}'", userQuery);

        try {
            // 尝试 L1 黑板路径
            if (taskDecomposer.isComplexQuery(userQuery)) {
                TaskBoard board = taskDecomposer.decompose(userQuery, false);
                if (board != null) {
                    String result = executeL1Blackboard(board, context, userQuery);
                    if (result != null && !result.isEmpty()) {
                        log.info("UserAiMetaAgent L1黑板完成: {} 个子任务", board.size());
                        return AgentQueryResult.success(result, getCode());
                    }
                }
            }

            // L1 快速路径：单 SubAgent
            String agentCode = intentRouter.route(userQuery, null);
            SubAgent subAgent = subAgentRegistry.getOrDefault(agentCode);
            log.info("UserAiMetaAgent L1快速路径: '{}' → {}", userQuery, subAgent.getCode());

            AgentQueryResult result = subAgent.execute(userQuery, context);
            log.info("UserAiMetaAgent 执行完成: agent={}, status={}", subAgent.getCode(), result.getStatus());
            return result;

        } catch (Exception e) {
            log.error("UserAiMetaAgent 执行异常: {}", e.getMessage(), e);
            return AgentQueryResult.error(getCode(), "用户端AI执行失败: " + e.getMessage());
        }
    }

    /**
     * 执行 L1 黑板多 Agent 协作
     */
    private String executeL1Blackboard(TaskBoard board, AgentContext context, String userQuery) {
        int replanCount = 0;
        for (int round = 0; round < L1_MAX_ROUNDS && !board.isAllDone(); round++) {
            List<TaskNode> readyTasks = board.ready();
            if (readyTasks.isEmpty()) {
                if (replanCount < L1_MAX_REPLANS && board.isAllDone()) {
                    int added = taskDecomposer.replan(board, userQuery, false);
                    if (added > 0) {
                        replanCount++;
                        log.info("UserAiMetaAgent L1再规划: 追加{}个任务", added);
                        continue;
                    }
                }
                break;
            }

            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (TaskNode task : readyTasks) {
                task.setStatus(TaskNode.Status.RUNNING);
                futures.add(CompletableFuture.runAsync(() -> {
                    long start = System.currentTimeMillis();
                    SubAgent agent = subAgentRegistry.getOrDefault(task.getAgentCode());
                    AgentQueryResult result = agent.execute(task.getTaskQuery(), context);
                    int latency = (int) (System.currentTimeMillis() - start);
                    if (result.getStatus() == AgentQueryResult.Status.ERROR) {
                        board.fail(task.getId(), result.getData());
                    } else {
                        board.close(task.getId(), result.getData() != null ? result.getData() : "", latency);
                    }
                }, agentExecutor));
            }

            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(L1_TIMEOUT_SECONDS * 2L, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("UserAiMetaAgent L1黑板轮次 {} 超时: {}", round, e.getMessage());
                break;
            }

            // 本轮完毕，尝试再规划
            if (board.isAllDone() && replanCount < L1_MAX_REPLANS) {
                int added = taskDecomposer.replan(board, userQuery, false);
                if (added > 0) {
                    replanCount++;
                    log.info("UserAiMetaAgent L1再规划: 追加{}个任务", added);
                }
            }
        }

        return board.collectResults();
    }
}

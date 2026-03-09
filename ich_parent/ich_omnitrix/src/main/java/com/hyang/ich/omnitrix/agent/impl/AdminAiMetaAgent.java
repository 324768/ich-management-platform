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
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 管理员普通AI元代理 —— Ultra L2 黑板上的元子智能体。
 *
 * 当 Ultra 主脑把管理端相关任务分发到 admin_ai_meta 时，
 * 该元代理启动 L1 流程：IntentRouter(admin) → SubAgent 执行。
 */
@Slf4j
@Component
public class AdminAiMetaAgent implements SubAgent {

    private static final int L1_TIMEOUT_SECONDS = 8;
    private static final int L1_MAX_ROUNDS = 3;

    private final IntentRouter intentRouter;
    private final SubAgentRegistry subAgentRegistry;
    private final TaskDecomposer taskDecomposer;
    private final Executor agentExecutor;

    public AdminAiMetaAgent(IntentRouter intentRouter,
                            SubAgentRegistry subAgentRegistry,
                            TaskDecomposer taskDecomposer,
                            @org.springframework.beans.factory.annotation.Qualifier("aiAsyncExecutor") Executor agentExecutor) {
        this.intentRouter = intentRouter;
        this.subAgentRegistry = subAgentRegistry;
        this.taskDecomposer = taskDecomposer;
        this.agentExecutor = agentExecutor;
    }

    @Override
    public String getCode() { return "admin_ai_meta"; }

    @Override
    public String getName() { return "管理员AI"; }

    @Override
    public String getDescription() { return "管理员普通AI元代理，通过L1黑板调用管理端子智能体"; }

    @Override
    public String getAgentPrompt() {
        return "你是管理员AI的代理入口。将收到的指令路由到管理端的子智能体执行。";
    }

    @Override
    public AgentQueryResult execute(String userQuery, AgentContext context) {
        log.info("AdminAiMetaAgent 收到L2任务: '{}'", userQuery);

        try {
            // 尝试 L1 黑板路径
            if (taskDecomposer.isComplexQuery(userQuery)) {
                TaskBoard board = taskDecomposer.decompose(userQuery, true);
                if (board != null) {
                    String result = executeL1Blackboard(board, context);
                    if (result != null && !result.isEmpty()) {
                        log.info("AdminAiMetaAgent L1黑板完成: {} 个子任务", board.size());
                        return AgentQueryResult.success(result, getCode());
                    }
                }
            }

            // L1 快速路径
            String agentCode = intentRouter.route(userQuery, null);
            SubAgent subAgent = subAgentRegistry.getOrDefault(agentCode);
            log.info("AdminAiMetaAgent L1快速路径: '{}' → {}", userQuery, subAgent.getCode());

            AgentQueryResult result = subAgent.execute(userQuery, context);
            return result;

        } catch (Exception e) {
            log.error("AdminAiMetaAgent 执行异常: {}", e.getMessage(), e);
            return AgentQueryResult.error(getCode(), "管理员AI执行失败: " + e.getMessage());
        }
    }

    private String executeL1Blackboard(TaskBoard board, AgentContext context) {
        for (int round = 0; round < L1_MAX_ROUNDS && !board.isAllDone(); round++) {
            List<TaskNode> readyTasks = board.ready();
            if (readyTasks.isEmpty()) break;

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
                log.warn("AdminAiMetaAgent L1黑板轮次 {} 超时: {}", round, e.getMessage());
                break;
            }
        }

        return board.collectResults();
    }
}

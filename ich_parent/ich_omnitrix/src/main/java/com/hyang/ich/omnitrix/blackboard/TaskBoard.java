package com.hyang.ich.omnitrix.blackboard;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 黑板（TaskBoard）—— 智能体共享的中央任务看板。
 *
 * 严格权限分离：
 * - 主脑（TaskDecomposer）：只能 create + depend（发牌，Write-Only）
 * - 子智能体（SubAgent）：只能 ready + close（干活，Read & Close-Only）
 *
 * 生命周期：每次用户请求创建一个 TaskBoard 实例，请求结束即销毁。
 * 无需 Redis 持久化，因为黑板的生命周期与单次请求对齐。
 */
@Slf4j
public class TaskBoard {

    /** 所有任务节点 */
    private final Map<String, TaskNode> nodes = new ConcurrentHashMap<>();

    /** 任务创建顺序（保持拓扑序） */
    private final List<String> executionOrder = new ArrayList<>();

    /** ID 生成计数器 */
    private int idCounter = 0;

    // ========== 主脑操作 (Write-Only) ==========

    /**
     * bd create —— 在黑板上创建一个新任务（无Skills）
     * 只有主脑（TaskDecomposer）可以调用
     *
     * @param agentCode 指定哪个子智能体来处理
     * @param taskQuery 任务的具体指令
     * @return 创建的任务节点
     */
    public TaskNode create(String agentCode, String taskQuery) {
        return create(agentCode, taskQuery, null);
    }

    /**
     * bd create —— 在黑板上创建一个新任务（带Skills）
     * 只有主脑（TaskDecomposer）可以调用
     *
     * @param agentCode 指定哪个子智能体来处理
     * @param taskQuery 任务的具体指令
     * @param requiredSkills 当前任务需要的Skills（可null）
     * @return 创建的任务节点
     */
    public TaskNode create(String agentCode, String taskQuery, List<String> requiredSkills) {
        String taskId = "task_" + (++idCounter);
        TaskNode node = TaskNode.create(taskId, agentCode, taskQuery, requiredSkills);
        nodes.put(taskId, node);
        executionOrder.add(taskId);
        log.debug("黑板 create: [{}] → {} ({}) skills={}", taskId, agentCode, taskQuery, requiredSkills);
        return node;
    }

    /**
     * bd depend —— 设置任务间的依赖关系
     * 只有主脑（TaskDecomposer）可以调用
     *
     * @param taskId       需要等待的任务ID
     * @param dependsOnId  被依赖的前置任务ID
     */
    public void depend(String taskId, String dependsOnId) {
        TaskNode node = nodes.get(taskId);
        TaskNode depNode = nodes.get(dependsOnId);
        if (node == null || depNode == null) {
            log.warn("黑板 depend 失败: taskId={} 或 dependsOnId={} 不存在", taskId, dependsOnId);
            return;
        }
        node.addDependency(dependsOnId);
        log.debug("黑板 depend: [{}] 依赖于 [{}]", taskId, dependsOnId);
    }

    // ========== 子智能体操作 (Read & Close-Only) ==========

    /**
     * bd ready —— 查看黑板上有哪些可以领取的任务
     * 只有子智能体可以调用
     *
     * @return 所有前置依赖已满足、状态为 READY 的任务列表
     */
    public List<TaskNode> ready() {
        // 先刷新状态：将满足依赖条件的 PENDING 任务提升为 READY
        for (TaskNode node : nodes.values()) {
            if (node.getStatus() == TaskNode.Status.PENDING
                    && node.areDependenciesMet(nodes)) {
                node.setStatus(TaskNode.Status.READY);
                log.debug("黑板: [{}] 依赖已满足 → READY", node.getId());
            }
        }

        return executionOrder.stream()
                .map(nodes::get)
                .filter(n -> n != null && n.getStatus() == TaskNode.Status.READY)
                .collect(Collectors.toList());
    }

    /**
     * bd close —— 子智能体完成任务后，擦掉黑板上的任务
     * 只有子智能体可以调用
     *
     * @param taskId 任务ID
     * @param result 执行结果
     * @param latencyMs 执行耗时
     */
    public void close(String taskId, String result, int latencyMs) {
        TaskNode node = nodes.get(taskId);
        if (node == null) {
            log.warn("黑板 close 失败: taskId={} 不存在", taskId);
            return;
        }
        node.setStatus(TaskNode.Status.DONE);
        node.setResult(result);
        node.setLatencyMs(latencyMs);
        log.debug("黑板 close: [{}] 完成 ({}ms)", taskId, latencyMs);
    }

    /**
     * 标记任务失败
     */
    public void fail(String taskId, String errorMsg) {
        TaskNode node = nodes.get(taskId);
        if (node == null) return;
        node.setStatus(TaskNode.Status.FAILED);
        node.setResult(errorMsg);
        log.warn("黑板 fail: [{}] 失败: {}", taskId, errorMsg);
    }

    // ========== 取消操作 ==========

    /** 是否已取消 */
    private volatile boolean cancelled = false;

    /**
     * 取消所有未完成的任务
     * @return 被取消的任务数量
     */
    public int cancel() {
        this.cancelled = true;
        int count = 0;
        for (TaskNode node : nodes.values()) {
            if (node.getStatus() == TaskNode.Status.PENDING
                    || node.getStatus() == TaskNode.Status.READY) {
                node.setStatus(TaskNode.Status.FAILED);
                node.setResult("任务已取消");
                count++;
            }
        }
        log.info("黑板取消: {} 个任务被取消", count);
        return count;
    }

    /**
     * 取消单个任务
     */
    public void cancelTask(String taskId) {
        TaskNode node = nodes.get(taskId);
        if (node == null) return;
        if (node.getStatus() == TaskNode.Status.PENDING
                || node.getStatus() == TaskNode.Status.READY) {
            node.setStatus(TaskNode.Status.FAILED);
            node.setResult("任务已取消");
            log.info("黑板取消单任务: [{}]", taskId);
        }
    }

    /** 是否已被取消 */
    public boolean isCancelled() {
        return cancelled;
    }

    // ========== 状态查询 ==========

    /**
     * 黑板上是否所有任务都已完成或失败
     */
    public boolean isAllDone() {
        return nodes.values().stream()
                .allMatch(n -> n.getStatus() == TaskNode.Status.DONE
                        || n.getStatus() == TaskNode.Status.FAILED);
    }

    /**
     * 收集所有已完成任务的结果，按执行顺序拼接
     * 用于注入到最终的 LLM Prompt 中
     */
    public String collectResults() {
        StringBuilder sb = new StringBuilder();
        for (String taskId : executionOrder) {
            TaskNode node = nodes.get(taskId);
            if (node == null) continue;
            if (node.getStatus() == TaskNode.Status.DONE
                    && node.getResult() != null && !node.getResult().isEmpty()) {
                sb.append("[").append(node.getAgentCode()).append(" 执行结果]\n");
                sb.append(node.getResult()).append("\n\n");
            } else if (node.getStatus() == TaskNode.Status.FAILED) {
                sb.append("[").append(node.getAgentCode()).append(" 执行失败]\n");
                sb.append(node.getResult() != null ? node.getResult() : "未知错误").append("\n\n");
            }
        }
        return sb.toString().trim();
    }

    /**
     * 获取任务总数
     */
    public int size() {
        return nodes.size();
    }

    /**
     * 获取所有任务节点（只读视图）
     */
    public Collection<TaskNode> getAllNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    /**
     * 获取指定任务的结果（供依赖任务获取前置结果）
     */
    public String getResult(String taskId) {
        TaskNode node = nodes.get(taskId);
        return node != null ? node.getResult() : null;
    }

    /**
     * 获取所有已完成任务的摘要（供主脑再规划时审视中间结果）
     * 格式：[agent_code] query → 结果摘要
     */
    public String getCompletedSummary() {
        StringBuilder sb = new StringBuilder();
        for (String taskId : executionOrder) {
            TaskNode node = nodes.get(taskId);
            if (node == null) continue;
            if (node.getStatus() == TaskNode.Status.DONE && node.getResult() != null) {
                sb.append("[").append(node.getAgentCode()).append("] ")
                        .append(node.getTaskQuery()).append(" → ");
                String result = node.getResult();
                if (result.length() > 200) {
                    sb.append(result, 0, 200).append("...");
                } else {
                    sb.append(result);
                }
                sb.append("\n");
            } else if (node.getStatus() == TaskNode.Status.FAILED) {
                sb.append("[" ).append(node.getAgentCode()).append("] ")
                        .append(node.getTaskQuery()).append(" → 失败: ")
                        .append(node.getResult() != null ? node.getResult() : "未知错误")
                        .append("\n");
            }
        }
        return sb.toString().trim();
    }

    /**
     * 获取尚未完成的任务数量（PENDING / READY / RUNNING）
     */
    public int getPendingCount() {
        int count = 0;
        for (TaskNode node : nodes.values()) {
            if (node.getStatus() != TaskNode.Status.DONE
                    && node.getStatus() != TaskNode.Status.FAILED) {
                count++;
            }
        }
        return count;
    }

    /**
     * 获取已完成（含失败）的任务数量
     */
    public int getCompletedCount() {
        int count = 0;
        for (TaskNode node : nodes.values()) {
            if (node.getStatus() == TaskNode.Status.DONE
                    || node.getStatus() == TaskNode.Status.FAILED) {
                count++;
            }
        }
        return count;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("TaskBoard {\n");
        for (String taskId : executionOrder) {
            TaskNode node = nodes.get(taskId);
            if (node == null) continue;
            sb.append("  [").append(taskId).append("] ")
                    .append(node.getAgentCode())
                    .append(" | ").append(node.getStatus())
                    .append(" | deps=").append(node.getDependsOn())
                    .append(" | query=").append(node.getTaskQuery())
                    .append("\n");
        }
        sb.append("}");
        return sb.toString();
    }
}

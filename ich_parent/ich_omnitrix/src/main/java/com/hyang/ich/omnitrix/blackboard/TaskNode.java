package com.hyang.ich.omnitrix.blackboard;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 黑板任务节点 —— 黑板（TaskBoard）上的一个原子任务。
 *
 * 设计原则（参考 Multi-Agent Blackboard Pattern）：
 * - 主脑只能 create 和 depend（发牌）
 * - 子智能体只能 ready 和 close（干活）
 * - 每个节点有明确的依赖关系和生命周期
 */
@Data
public class TaskNode {

    public enum Status {
        /** 已创建，但有未完成的前置依赖 */
        PENDING,
        /** 所有前置依赖已完成，可被子智能体领取 */
        READY,
        /** 正在被某个子智能体执行 */
        RUNNING,
        /** 执行完成 */
        DONE,
        /** 执行失败 */
        FAILED
    }

    /** 任务唯一ID */
    private String id;

    /** 目标子智能体编码（由主脑指定谁来做） */
    private String agentCode;

    /** 任务具体指令（传给子智能体的查询） */
    private String taskQuery;

    /** 前置依赖任务ID列表 */
    private List<String> dependsOn;

    /** 当前状态 */
    private Status status;

    /** 执行结果（子智能体 close 时写入） */
    private String result;

    /** 执行耗时（ms） */
    private int latencyMs;

    /** 创建时间戳 */
    private long createdAt;

    /**
     * 主脑创建任务节点
     */
    public static TaskNode create(String id, String agentCode, String taskQuery) {
        TaskNode node = new TaskNode();
        node.setId(id);
        node.setAgentCode(agentCode);
        node.setTaskQuery(taskQuery);
        node.setDependsOn(new ArrayList<>());
        node.setStatus(Status.READY); // 无依赖时默认 READY
        node.setCreatedAt(System.currentTimeMillis());
        return node;
    }

    /**
     * 添加前置依赖（主脑调用）
     * 添加依赖后状态自动降为 PENDING
     */
    public void addDependency(String taskId) {
        if (this.dependsOn == null) {
            this.dependsOn = new ArrayList<>();
        }
        this.dependsOn.add(taskId);
        this.status = Status.PENDING;
    }

    /**
     * 检查是否所有前置依赖都已完成
     */
    public boolean areDependenciesMet(java.util.Map<String, TaskNode> allNodes) {
        if (dependsOn == null || dependsOn.isEmpty()) return true;
        for (String depId : dependsOn) {
            TaskNode dep = allNodes.get(depId);
            if (dep == null || dep.getStatus() != Status.DONE) {
                return false;
            }
        }
        return true;
    }
}

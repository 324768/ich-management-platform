package com.hyang.ich.omnitrix.brain.subbrain;

import lombok.Getter;

/**
 * SubBrain 执行结果 DTO — 子脑返回给 SubBrainTools 的中间结果。
 * <p>
 * 包含原始 LLM 回复内容和 PendingAction 状态，
 * SubBrainTools 据此通过 SubBrainResultWrapper 生成最终结构化 XML。
 */
@Getter
public class SubBrainResult {

    /** 子脑 LLM 的原始回复内容 */
    private final String content;

    /** 子脑执行过程中是否产生了 PendingAction（写操作确认） */
    private final boolean hasPendingAction;

    /** PendingAction 的描述（如有） */
    private final String pendingActionDescription;

    public SubBrainResult(String content, boolean hasPendingAction, String pendingActionDescription) {
        this.content = content;
        this.hasPendingAction = hasPendingAction;
        this.pendingActionDescription = pendingActionDescription;
    }

    /** 成功结果 */
    public static SubBrainResult of(String content) {
        return new SubBrainResult(content, false, null);
    }

    /** 带 PendingAction 的结果 */
    public static SubBrainResult withPendingAction(String content, String actionDescription) {
        return new SubBrainResult(content, true, actionDescription);
    }

    /** 错误结果 */
    public static SubBrainResult error(String errorMessage) {
        return new SubBrainResult("[子脑执行失败] " + errorMessage, false, null);
    }
}

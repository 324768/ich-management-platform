package com.hyang.ich.omnitrix.brain.subbrain;

/**
 * SubBrain 接口 — 参考 Kortex SystemSubAgent 设计。
 * <p>
 * 定义子脑的标准契约，每个子脑封装一个完整的 MasterBrain（含独立 LLM + Tools + Memory），
 * Ultra MasterBrain 通过 SubBrainTools 将其作为 SubAgent 调用。
 * <p>
 * 架构：Ultra LLM（编排）→ SubBrain（独立 LLM 推理）→ @Tool 方法（业务操作）
 */
public interface SubBrain {

    /** 子脑代码（唯一标识） */
    String getCode();

    /** 子脑名称（显示用） */
    String getName();

    /** 子脑能力描述（注入 Ultra LLM 的工具描述） */
    String getDescription();

    /**
     * 执行子脑
     *
     * @param userQuery      用户查询内容
     * @param targetUserId   目标用户 ID（跨用户操作时非空，自身操作时为 null）
     * @param ultraSessionId Ultra 的会话 ID（用于 PendingAction 传递）
     * @return 子脑执行结果
     */
    SubBrainResult execute(String userQuery, Long targetUserId, String ultraSessionId);
}

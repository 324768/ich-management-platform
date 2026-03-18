package com.hyang.ich.omnitrix.brain.subbrain;

import com.hyang.ich.omnitrix.brain.AiRequestContext;
import com.hyang.ich.omnitrix.brain.MasterBrainFactory;
import com.hyang.ich.omnitrix.dto.PendingAction;
import com.hyang.ich.omnitrix.orchestrator.ActionExecutor;
import dev.langchain4j.service.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Ultra 专属 SubBrain — 封装 UltraMasterBrain 为 Ultra 可调用的子脑。
 * <p>
 * 参考 Kortex SystemSubAgent 设计：
 * 1. 构建独立 session 的 UltraMasterBrain（不含 SubBrainTools，避免循环调用）
 * 2. 执行 Ultra AI 完整推理（含系统管理、安全审计、数据分析等）
 * 3. 将产生的 PendingAction 传递到 Ultra 的 session
 * 4. 返回 SubBrainResult
 * <p>
 * 架构位置：UltraMasterBrain → SubBrainTools.callUltraAI() → UltraSubBrain
 */
@Slf4j
@Component
public class UltraSubBrain implements SubBrain {

    private final MasterBrainFactory masterBrainFactory;
    private final ActionExecutor actionExecutor;

    public UltraSubBrain(MasterBrainFactory masterBrainFactory, ActionExecutor actionExecutor) {
        this.masterBrainFactory = masterBrainFactory;
        this.actionExecutor = actionExecutor;
    }

    @Override
    public String getCode() {
        return "ultra_ai";
    }

    @Override
    public String getName() {
        return "超级管理AI助手";
    }

    @Override
    public String getDescription() {
        return "调用超级管理AI助手，执行Ultra专属操作：系统管理（用户列表、封禁/解封、删除用户）、" +
                "安全审计（安全巡检、操作日志）、数据分析（用户画像、浏览记录）、" +
                "AI控制（禁用/启用用户AI）、Skill管理等。参数：管理操作查询内容";
    }

    @Override
    public SubBrainResult execute(String userQuery, Long targetUserId, String ultraSessionId) {
        // 保存原始上下文
        Long originalUserId = AiRequestContext.getUserId();
        String originalSessionId = AiRequestContext.getSessionId();

        // 构建子脑独立 session
        String subSessionId = ultraSessionId + ":sub:" + getCode();

        try {
            // 切换 session（保持 userId，仅切独立 session 避免记忆污染）
            AiRequestContext.set(originalUserId, subSessionId);
            log.info("SubBrain[{}] 执行开始: subSession={}, query={}",
                    getCode(), subSessionId,
                    userQuery.length() > 80 ? userQuery.substring(0, 80) + "..." : userQuery);

            // 构建 UltraMasterBrain（同步模式，独立 session）
            // 注意：UltraSubBrain 不需要 profile（这是 Ultra 专属能力）
            // 使用 buildUltraBrainForSubBrain 避免循环调用
            var brain = masterBrainFactory.buildUltraBrainForSubBrain(subSessionId);
            String skills = masterBrainFactory.buildDynamicSkillsPrompt(userQuery);

            // 执行 Ultra AI 完整推理
            Result<String> result = brain.chat(userQuery, skills);
            String rawContent = result.content();

            // 检查子脑 session 中是否产生了 PendingAction
            PendingAction subPending = actionExecutor.getPendingAction(subSessionId);
            if (subPending != null) {
                actionExecutor.savePendingAction(ultraSessionId, subPending);
                actionExecutor.clearPendingAction(subSessionId);
                log.info("SubBrain[{}] PendingAction 已传递到 Ultra session: type={}",
                        getCode(), subPending.getActionType());
                return SubBrainResult.withPendingAction(rawContent, subPending.getDescription());
            }

            log.info("SubBrain[{}] 执行完成: resultLength={}", getCode(),
                    rawContent != null ? rawContent.length() : 0);
            return SubBrainResult.of(rawContent);

        } catch (Exception e) {
            log.error("SubBrain[{}] 执行失败: {}", getCode(), e.getMessage(), e);
            return SubBrainResult.error(e.getMessage());
        } finally {
            // 恢复原始上下文
            AiRequestContext.set(originalUserId, originalSessionId);
        }
    }
}

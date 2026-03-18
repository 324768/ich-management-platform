package com.hyang.ich.omnitrix.brain.subbrain;

import com.hyang.ich.omnitrix.brain.AdminMasterBrain;
import com.hyang.ich.omnitrix.brain.AiRequestContext;
import com.hyang.ich.omnitrix.brain.MasterBrainFactory;
import com.hyang.ich.omnitrix.dto.PendingAction;
import com.hyang.ich.omnitrix.orchestrator.ActionExecutor;
import dev.langchain4j.service.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 管理端 SubBrain — 封装 AdminMasterBrain 为 Ultra 可调用的子脑。
 * <p>
 * 参考 Kortex SystemSubAgent 设计，Ultra 调用时会：
 * 1. 构建独立 session 的 AdminMasterBrain
 * 2. 执行管理端 AI 完整推理（含审批、发货、统计等管理操作）
 * 3. 将产生的 PendingAction 传递到 Ultra 的 session
 * 4. 返回 SubBrainResult
 * <p>
 * 注意：AdminSubBrain 不需要切换 userId（管理操作不绑定特定用户）
 */
@Slf4j
@Component
public class AdminSubBrain implements SubBrain {

    private final MasterBrainFactory masterBrainFactory;
    private final ActionExecutor actionExecutor;

    public AdminSubBrain(MasterBrainFactory masterBrainFactory, ActionExecutor actionExecutor) {
        this.masterBrainFactory = masterBrainFactory;
        this.actionExecutor = actionExecutor;
    }

    @Override
    public String getCode() {
        return "admin_ai";
    }

    @Override
    public String getName() {
        return "管理端AI助手";
    }

    @Override
    public String getDescription() {
        return "调用管理端AI助手，执行管理侧操作：内容管理（非遗项目/活动的审批、上下架）、" +
                "商品管理（商品查询、订单发货）、知识库管理、通知发布等管理员日常运营任务。" +
                "参数：管理操作的查询内容";
    }

    @Override
    public SubBrainResult execute(String userQuery, Long targetUserId, String ultraSessionId) {
        // 保存原始上下文
        Long originalUserId = AiRequestContext.getUserId();
        String originalSessionId = AiRequestContext.getSessionId();

        // Admin 操作使用原始管理员 ID，不切换用户
        String subSessionId = ultraSessionId + ":sub:" + getCode();

        try {
            // 切换 session（保持 userId，仅切独立 session 避免记忆污染）
            AiRequestContext.set(originalUserId, subSessionId);
            log.info("SubBrain[{}] 执行开始: subSession={}, query={}",
                    getCode(), subSessionId,
                    userQuery.length() > 80 ? userQuery.substring(0, 80) + "..." : userQuery);

            // 构建 AdminMasterBrain（同步模式，独立 session）
            AdminMasterBrain brain = masterBrainFactory.buildAdminBrain(subSessionId);
            String skills = masterBrainFactory.buildDynamicSkillsPrompt(userQuery);

            // 执行管理端 AI 完整推理
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

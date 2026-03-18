package com.hyang.ich.omnitrix.brain.subbrain;

import com.hyang.ich.omnitrix.brain.AiRequestContext;
import com.hyang.ich.omnitrix.brain.MasterBrainFactory;
import com.hyang.ich.omnitrix.brain.UserMasterBrain;
import com.hyang.ich.omnitrix.dto.PendingAction;
import com.hyang.ich.omnitrix.orchestrator.ActionExecutor;
import dev.langchain4j.service.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 用户端 SubBrain — 封装 UserMasterBrain 为 Ultra 可调用的子脑。
 * <p>
 * 参考 Kortex SystemSubAgent + SubAgentTool 设计：
 * 1. 临时切换 AiRequestContext 到目标用户（参考 Kortex BaseToolContext 上下文传播）
 * 2. 构建独立 session 的 UserMasterBrain（参考 Kortex 独立会话 ID 格式）
 * 3. 执行用户端 AI 完整推理（含多轮 Function Calling）
 * 4. 将产生的 PendingAction 传递到 Ultra 的 session（参考 Kortex 结果回传机制）
 * 5. 恢复上下文并返回 SubBrainResult
 */
@Slf4j
@Component
public class UserSubBrain implements SubBrain {

    private final MasterBrainFactory masterBrainFactory;
    private final ActionExecutor actionExecutor;

    public UserSubBrain(MasterBrainFactory masterBrainFactory, ActionExecutor actionExecutor) {
        this.masterBrainFactory = masterBrainFactory;
        this.actionExecutor = actionExecutor;
    }

    @Override
    public String getCode() {
        return "user_ai";
    }

    @Override
    public String getName() {
        return "用户端AI助手";
    }

    @Override
    public String getDescription() {
        return "调用用户端AI助手，执行用户侧操作：商城购物（搜索商品、加购物车、下单、查订单）、" +
                "内容浏览（搜索非遗项目/传承人/活动、点赞/收藏/评论、报名活动）、" +
                "个人中心（查地址/资质/通知）、知识问答、个性化推荐等。" +
                "参数格式：'查询内容' 或 '查询内容|目标用户名或ID'（跨用户操作时提供目标用户）";
    }

    @Override
    public SubBrainResult execute(String userQuery, Long targetUserId, String ultraSessionId) {
        // 保存原始上下文
        Long originalUserId = AiRequestContext.getUserId();
        String originalSessionId = AiRequestContext.getSessionId();

        // 确定实际操作的用户 ID
        Long effectiveUserId = (targetUserId != null) ? targetUserId : originalUserId;
        // 构建子脑独立 session（参考 Kortex: userId:sessionId:sub-agent:agentCode）
        String subSessionId = ultraSessionId + ":sub:" + getCode();

        try {
            // 切换上下文到目标用户
            AiRequestContext.set(effectiveUserId, subSessionId);
            log.info("SubBrain[{}] 执行开始: effectiveUserId={}, subSession={}, query={}",
                    getCode(), effectiveUserId, subSessionId,
                    userQuery.length() > 80 ? userQuery.substring(0, 80) + "..." : userQuery);

            // 构建 UserMasterBrain（同步模式，使用独立 session 避免记忆污染）
            UserMasterBrain brain = masterBrainFactory.buildUserBrain(effectiveUserId, subSessionId);
            String skills = masterBrainFactory.buildDynamicSkillsPrompt(userQuery);
            String profile = masterBrainFactory.buildUserProfile(effectiveUserId);

            // 执行用户端 AI 完整推理（内部会自动多轮 Function Calling）
            Result<String> result = brain.chat(userQuery, skills, profile);
            String rawContent = result.content();

            // 检查子脑 session 中是否产生了 PendingAction
            PendingAction subPending = actionExecutor.getPendingAction(subSessionId);
            if (subPending != null) {
                // 将 PendingAction 传递到 Ultra 的主 session（参考 Kortex 结果传递）
                actionExecutor.savePendingAction(ultraSessionId, subPending);
                // 清理子脑 session 的 PendingAction
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
            // 恢复原始上下文（关键：保证 Ultra 后续工具调用不受影响）
            AiRequestContext.set(originalUserId, originalSessionId);
        }
    }
}

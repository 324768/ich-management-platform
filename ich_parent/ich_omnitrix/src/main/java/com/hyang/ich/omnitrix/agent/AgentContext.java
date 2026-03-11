package com.hyang.ich.omnitrix.agent;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class AgentContext {

    private Long userId;
    private String sessionId;
    private Long conversationId;
    private String userRole;
    /** 当前用户问题（用于Skill动态选择） */
    private String userQuery;
    /** L2 黑板原始指令（元代理可知晓 Ultra 的完整意图） */
    private String parentQuery;
    /** L2 元数据（任务ID、来源Agent等） */
    private Map<String, String> l2Metadata;
    /** Agent 执行耗时（毫秒），供 Span 追踪使用 */
    private int agentDurationMs;

    public static AgentContext of(Long userId, String sessionId, Long conversationId, String userRole) {
        return of(userId, sessionId, conversationId, userRole, null);
    }

    public static AgentContext of(Long userId, String sessionId, Long conversationId, String userRole, String userQuery) {
        AgentContext ctx = new AgentContext();
        ctx.setUserId(userId);
        ctx.setSessionId(sessionId);
        ctx.setConversationId(conversationId);
        ctx.setUserRole(userRole);
        ctx.setUserQuery(userQuery);
        return ctx;
    }

    /** 为 L2 黑板任务创建子上下文 */
    public AgentContext withL2(String parentQuery, String taskId, String sourceAgent) {
        AgentContext child = AgentContext.of(this.userId, this.sessionId, this.conversationId, this.userRole);
        child.setParentQuery(parentQuery);
        Map<String, String> meta = new HashMap<>();
        meta.put("taskId", taskId);
        meta.put("sourceAgent", sourceAgent);
        meta.put("level", "L2");
        child.setL2Metadata(meta);
        return child;
    }
}

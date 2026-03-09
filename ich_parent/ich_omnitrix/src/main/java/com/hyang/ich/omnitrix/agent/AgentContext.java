package com.hyang.ich.omnitrix.agent;

import lombok.Data;

@Data
public class AgentContext {

    private Long userId;
    private String sessionId;
    private Long conversationId;
    private String userRole;

    public static AgentContext of(Long userId, String sessionId, Long conversationId, String userRole) {
        AgentContext ctx = new AgentContext();
        ctx.setUserId(userId);
        ctx.setSessionId(sessionId);
        ctx.setConversationId(conversationId);
        ctx.setUserRole(userRole);
        return ctx;
    }
}

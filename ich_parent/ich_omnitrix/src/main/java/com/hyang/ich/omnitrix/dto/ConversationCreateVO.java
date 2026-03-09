package com.hyang.ich.omnitrix.dto;

import lombok.Data;

@Data
public class ConversationCreateVO {

    private Long id;
    private String sessionId;
    private String title;

    public static ConversationCreateVO from(com.hyang.ich.omnitrix.entity.AiConversation c) {
        ConversationCreateVO vo = new ConversationCreateVO();
        vo.setId(c.getId());
        vo.setSessionId(c.getSessionId());
        vo.setTitle(c.getTitle());
        return vo;
    }
}

package com.hyang.ich.omnitrix.dto;

import lombok.Data;

import java.util.List;

@Data
public class ConversationDetailVO {

    private Long id;
    private Long userId;
    private String sessionId;
    private String title;
    private Integer messageCount;
    private List<MessageVO> messages;

    public static ConversationDetailVO from(com.hyang.ich.omnitrix.entity.AiConversation c, List<MessageVO> messages) {
        ConversationDetailVO vo = new ConversationDetailVO();
        vo.setId(c.getId());
        vo.setUserId(c.getUserId());
        vo.setSessionId(c.getSessionId());
        vo.setTitle(c.getTitle());
        vo.setMessageCount(c.getMessageCount());
        vo.setMessages(messages);
        return vo;
    }
}

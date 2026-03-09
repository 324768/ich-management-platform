package com.hyang.ich.omnitrix.dto;

import lombok.Data;

import java.util.Date;

@Data
public class ConversationVO {

    private Long id;
    private Long userId;
    private String sessionId;
    private String title;
    private Integer messageCount;
    private Date createTime;
    private Date updateTime;

    public static ConversationVO from(com.hyang.ich.omnitrix.entity.AiConversation c) {
        ConversationVO vo = new ConversationVO();
        vo.setId(c.getId());
        vo.setUserId(c.getUserId());
        vo.setSessionId(c.getSessionId());
        vo.setTitle(c.getTitle());
        vo.setMessageCount(c.getMessageCount());
        vo.setCreateTime(c.getCreateTime());
        vo.setUpdateTime(c.getUpdateTime());
        return vo;
    }
}

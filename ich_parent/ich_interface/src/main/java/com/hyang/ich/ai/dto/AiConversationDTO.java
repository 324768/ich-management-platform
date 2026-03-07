package com.hyang.ich.ai.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
public class AiConversationDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String title;
    private Date createTime;
    private Date updateTime;

    /** 对话消息列表 */
    private List<AiMessageDTO> messages;
}

package com.hyang.ich.ai.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class AiChatRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private Long conversationId;
    private String message;
}

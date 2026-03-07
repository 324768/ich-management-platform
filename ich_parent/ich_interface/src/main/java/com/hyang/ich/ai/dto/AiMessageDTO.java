package com.hyang.ich.ai.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class AiMessageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long conversationId;
    private String role;
    private String content;
    private Integer tokens;
    private String model;
    private Date createTime;
}

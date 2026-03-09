package com.hyang.ich.omnitrix.dto;

import lombok.Data;

import java.util.Date;

@Data
public class MessageVO {

    private Long id;
    private String role;
    private String content;
    private String subAgent;
    private Integer latencyMs;
    private Date createTime;

    public static MessageVO from(com.hyang.ich.omnitrix.entity.AiMessage m) {
        MessageVO vo = new MessageVO();
        vo.setId(m.getId());
        vo.setRole(m.getRole());
        vo.setContent(m.getContent());
        vo.setSubAgent(m.getSubAgent());
        vo.setLatencyMs(m.getLatencyMs());
        vo.setCreateTime(m.getCreateTime());
        return vo;
    }
}

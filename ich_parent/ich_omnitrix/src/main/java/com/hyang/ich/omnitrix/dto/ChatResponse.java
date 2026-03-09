package com.hyang.ich.omnitrix.dto;

import lombok.Data;

@Data
public class ChatResponse {

    private Long messageId;
    private String sessionId;
    private String content;
    private String subAgent;
    private Integer latencyMs;

    public static ChatResponse of(Long messageId, String sessionId, String content,
                                   String subAgent, int latencyMs) {
        ChatResponse resp = new ChatResponse();
        resp.setMessageId(messageId);
        resp.setSessionId(sessionId);
        resp.setContent(content);
        resp.setSubAgent(subAgent);
        resp.setLatencyMs(latencyMs);
        return resp;
    }
}

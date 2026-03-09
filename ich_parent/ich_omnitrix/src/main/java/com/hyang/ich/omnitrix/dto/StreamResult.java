package com.hyang.ich.omnitrix.dto;

import lombok.Data;

@Data
public class StreamResult {

    private String content;
    private int estimatedInputTokens;
    private int estimatedOutputTokens;

    public static StreamResult of(String content, int inputTokens, int outputTokens) {
        StreamResult r = new StreamResult();
        r.setContent(content);
        r.setEstimatedInputTokens(inputTokens);
        r.setEstimatedOutputTokens(outputTokens);
        return r;
    }
}

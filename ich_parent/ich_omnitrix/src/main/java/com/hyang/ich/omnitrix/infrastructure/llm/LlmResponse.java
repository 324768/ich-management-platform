package com.hyang.ich.omnitrix.infrastructure.llm;

import lombok.Data;

import java.util.List;

@Data
public class LlmResponse {

    private List<Choice> choices;
    private Usage usage;

    @Data
    public static class Choice {
        private Message message;
    }

    @Data
    public static class Message {
        private String role;
        private String content;
    }

    @Data
    public static class Usage {
        private int prompt_tokens;
        private int completion_tokens;
        private int total_tokens;
    }

    public String getContent() {
        if (choices != null && !choices.isEmpty()
                && choices.get(0).getMessage() != null) {
            return choices.get(0).getMessage().getContent();
        }
        return "";
    }

    public int getInputTokens() {
        return usage != null ? usage.getPrompt_tokens() : 0;
    }

    public int getOutputTokens() {
        return usage != null ? usage.getCompletion_tokens() : 0;
    }
}

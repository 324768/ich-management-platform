package com.hyang.ich.omnitrix.infrastructure.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LlmRequest {

    private String model;
    private List<Map<String, String>> messages;
    private double temperature;
    private int max_tokens;
    private boolean stream;
    private Map<String, String> response_format;

    public static LlmRequest of(String model, List<Map<String, String>> messages,
                                 double temperature, int maxTokens, boolean stream) {
        LlmRequest req = new LlmRequest();
        req.setModel(model);
        req.setMessages(messages);
        req.setTemperature(temperature);
        req.setMax_tokens(maxTokens);
        req.setStream(stream);
        return req;
    }

    /**
     * 启用 JSON Mode：强制 LLM 输出合法 JSON（Ollama / vLLM / OpenAI 均支持）
     */
    public LlmRequest withJsonMode() {
        this.response_format = Collections.singletonMap("type", "json_object");
        return this;
    }
}

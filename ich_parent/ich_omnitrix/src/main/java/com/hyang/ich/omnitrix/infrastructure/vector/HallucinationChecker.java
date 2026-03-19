package com.hyang.ich.omnitrix.infrastructure.vector;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 幻觉检测器 - 检测生成内容是否与检索内容一致
 * 防止模型编造不在知识库中的信息
 */
@Slf4j
@Component
public class HallucinationChecker {

    private final ChatLanguageModel chatModel;

    public HallucinationChecker(ChatLanguageModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 检测回答中是否存在幻觉
     *
     * @param answer LLM生成的回答
     * @param context 检索到的上下文
     * @return 幻觉检测结果
     */
    public HallucinationResult check(String answer, String context) {
        if (answer == null || answer.trim().isEmpty()) {
            return HallucinationResult.builder()
                    .hasHallucination(false)
                    .confidence(1.0)
                    .feedback("空回答")
                    .build();
        }

        if (context == null || context.trim().isEmpty()) {
            return HallucinationResult.builder()
                    .hasHallucination(false)
                    .confidence(0.5)
                    .feedback("无上下文，无法检测")
                    .build();
        }

        String prompt = String.format("""
                你是一个事实核查专家。请仔细检查以下回答是否基于提供的上下文/知识库内容。

                上下文/知识库内容：
                %s

                回答：
                %s

                检测要求：
                1. 仔细对比回答中的每个陈述与上下文内容
                2. 识别任何超出上下文范围的信息
                3. 识别任何与上下文矛盾的信息
                4. 如果回答正确使用了上下文内容，标记为无幻觉

                返回格式（JSON）：
                {
                    "hasHallucination": true/false,
                    "confidence": 0.0-1.0,
                    "feedback": "简要说明检测结果"
                }

                只返回JSON，不要其他内容。
                """, context, answer);

        try {
            String response = chatModel.chat(prompt).trim();
            return parseResult(response);
        } catch (Exception e) {
            log.warn("幻觉检测失败: {}", e.getMessage());
            return HallucinationResult.builder()
                    .hasHallucination(false)
                    .confidence(0.5)
                    .feedback("检测失败")
                    .build();
        }
    }

    /**
     * 解析检测结果
     */
    private HallucinationResult parseResult(String response) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(response, HallucinationResult.class);
        } catch (Exception e) {
            log.warn("解析幻觉检测结果失败: {}", response);
            // 简单解析
            boolean hasHallucination = response.toLowerCase().contains("true")
                    || response.contains("1");
            return HallucinationResult.builder()
                    .hasHallucination(hasHallucination)
                    .confidence(0.5)
                    .feedback(response)
                    .build();
        }
    }

    @lombok.Data
    @lombok.Builder
    public static class HallucinationResult {
        /** 是否存在幻觉 */
        private boolean hasHallucination;
        /** 置信度（0-1） */
        private double confidence;
        /** 反馈说明 */
        private String feedback;
    }
}

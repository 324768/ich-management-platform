package com.hyang.ich.omnitrix.infrastructure.telemetry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmClient;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmResponse;
import com.hyang.ich.omnitrix.infrastructure.prompt.PromptTemplate;
import com.hyang.ich.omnitrix.mapper.AiTraceLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class AiSelfEvaluator {

    private static final Pattern SCORE_PATTERN = Pattern.compile("(\\d+\\.?\\d*)");
    private static final Pattern JSON_PATTERN = Pattern.compile("\\{[^}]*}");

    private final LlmClient llmClient;
    private final AiTraceLogMapper traceLogMapper;
    private final ObjectMapper objectMapper;

    public AiSelfEvaluator(LlmClient llmClient, AiTraceLogMapper traceLogMapper) {
        this.llmClient = llmClient;
        this.traceLogMapper = traceLogMapper;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 异步多维评分：调用 LLM 对 [问题, 回答] 进行准确性/完整性/安全性打分
     */
    @Async("aiAsyncExecutor")
    public void evaluate(String traceId, String userQuery, String aiResponse) {
        try {
            String evalPrompt = String.format(PromptTemplate.SELF_EVALUATOR, userQuery, aiResponse);

            LlmResponse response = llmClient.chatAuxiliaryJson(evalPrompt, new ArrayList<>(), "请打分");

            String scoreText = response.getContent();

            // 尝试解析多维 JSON 评分
            if (parseMultiDimensionScore(traceId, scoreText)) {
                return;
            }

            // 回退: 解析单一数字评分
            BigDecimal score = parseSingleScore(scoreText);
            if (score != null) {
                traceLogMapper.updateSelfScore(traceId, score);
                log.debug("AI 自评分(单维回退): traceId={}, score={}", traceId, score);
            } else {
                log.debug("AI 自评分解析失败: traceId={}, response={}", traceId, scoreText);
            }

        } catch (Exception e) {
            log.error("AI 自评分异常: traceId={}, error={}", traceId, e.getMessage());
        }
    }

    /**
     * 解析多维 JSON 评分 {"accuracy":X,"completeness":X,"safety":X}
     */
    private boolean parseMultiDimensionScore(String traceId, String text) {
        if (text == null || text.isEmpty()) return false;

        try {
            // 从回复中提取 JSON 部分
            Matcher jsonMatcher = JSON_PATTERN.matcher(text);
            if (!jsonMatcher.find()) return false;

            JsonNode node = objectMapper.readTree(jsonMatcher.group());
            BigDecimal accuracy = extractScore(node, "accuracy");
            BigDecimal completeness = extractScore(node, "completeness");
            BigDecimal safety = extractScore(node, "safety");

            if (accuracy != null && completeness != null && safety != null) {
                BigDecimal avg = accuracy.add(completeness).add(safety)
                        .divide(BigDecimal.valueOf(3), 1, RoundingMode.HALF_UP);

                traceLogMapper.updateMultiDimensionScore(traceId, avg, accuracy, completeness, safety);
                log.debug("AI 多维评分: traceId={}, accuracy={}, completeness={}, safety={}, avg={}",
                        traceId, accuracy, completeness, safety, avg);
                return true;
            }
        } catch (Exception e) {
            log.debug("多维评分 JSON 解析失败: {}", e.getMessage());
        }
        return false;
    }

    private BigDecimal extractScore(JsonNode node, String field) {
        if (node.has(field)) {
            double val = node.get(field).asDouble(-1);
            if (val >= 0 && val <= 10) {
                return BigDecimal.valueOf(val);
            }
        }
        return null;
    }

    private BigDecimal parseSingleScore(String text) {
        if (text == null || text.isEmpty()) return null;

        Matcher matcher = SCORE_PATTERN.matcher(text.trim());
        if (matcher.find()) {
            try {
                double score = Double.parseDouble(matcher.group(1));
                if (score >= 0 && score <= 10) {
                    return BigDecimal.valueOf(score);
                }
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}

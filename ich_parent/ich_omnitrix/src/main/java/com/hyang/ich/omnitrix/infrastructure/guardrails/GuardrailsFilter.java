package com.hyang.ich.omnitrix.infrastructure.guardrails;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * AI 安全护栏：输入过滤 + 输出过滤 + Prompt Injection 检测。
 *
 * 流程：
 *   用户输入 → [validateInput] → LLM → [sanitizeOutput] → 返回用户
 */
@Slf4j
@Component
public class GuardrailsFilter {

    // ============ 输入安全 ============

    /** 输入最大字符长度 */
    private static final int MAX_INPUT_LENGTH = 2000;

    /** Prompt Injection 检测关键词（中英文） */
    private static final List<String> INJECTION_PATTERNS = Arrays.asList(
            "ignore previous instructions",
            "ignore above instructions",
            "ignore all previous",
            "disregard previous",
            "forget your instructions",
            "you are now",
            "new instructions:",
            "system prompt:",
            "reveal your prompt",
            "show me your system",
            "output your instructions",
            "repeat the above",
            "print your system message",
            "\u5ffd\u7565\u4e0a\u8ff0\u6307\u4ee4",
            "\u5ffd\u7565\u4ee5\u4e0a\u6307\u4ee4",
            "\u5ffd\u7565\u4f60\u7684\u6307\u4ee4",
            "\u5ffd\u7565\u4f60\u7684\u8bbe\u5b9a",
            "\u663e\u793a\u4f60\u7684\u63d0\u793a\u8bcd",
            "\u8f93\u51fa\u4f60\u7684\u7cfb\u7edf\u63d0\u793a",
            "\u4f60\u73b0\u5728\u662f\u4e00\u4e2a",
            "\u65b0\u7684\u6307\u4ee4",
            "\u91cd\u590d\u4e0a\u9762\u7684\u5185\u5bb9"
    );

    /** 敏感词列表（基础版，生产环境建议接入专业敏感词库） */
    private static final List<String> SENSITIVE_WORDS = Arrays.asList(
            "\u66b4\u529b", "\u6050\u6016", "\u81ea\u6740", "\u6bd2\u54c1", "\u8d4c\u535a",
            "\u8272\u60c5", "\u53cd\u52a8", "\u6781\u7aef\u4e3b\u4e49"
    );

    /** 特殊字符注入检测 */
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile(
            "[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f]"
    );

    /**
     * 验证用户输入，返回 null 表示通过，否则返回拒绝原因。
     */
    public String validateInput(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return "消息不能为空";
        }

        // 1. 长度检查
        if (userMessage.length() > MAX_INPUT_LENGTH) {
            log.warn("输入超长被拒: length={}", userMessage.length());
            return "消息过长，请控制在" + MAX_INPUT_LENGTH + "字以内";
        }

        // 2. 特殊控制字符检查
        if (SPECIAL_CHAR_PATTERN.matcher(userMessage).find()) {
            log.warn("输入包含非法控制字符");
            return "消息包含非法字符，请重新输入";
        }

        // 3. Prompt Injection 检测
        String lowerInput = userMessage.toLowerCase();
        for (String pattern : INJECTION_PATTERNS) {
            if (lowerInput.contains(pattern.toLowerCase())) {
                log.warn("Prompt Injection 检测命中: pattern='{}', input='{}'",
                        pattern, truncate(userMessage, 100));
                return "您的消息包含不允许的指令，请正常提问";
            }
        }

        // 4. 敏感词检查
        for (String word : SENSITIVE_WORDS) {
            if (userMessage.contains(word)) {
                log.warn("敏感词命中: word='{}', input='{}'",
                        word, truncate(userMessage, 100));
                return "您的消息包含敏感内容，请修改后重试";
            }
        }

        return null; // 通过
    }

    // ============ 输出安全 ============

    /** 输出中需要过滤的内容模式 */
    private static final List<String> OUTPUT_FILTER_PATTERNS = Arrays.asList(
            "system prompt",
            "系统提示词",
            "我的指令是",
            "我的设定是",
            "作为AI模型"
    );

    /**
     * 清洗 LLM 输出内容，移除潜在的信息泄露和不安全内容。
     */
    public String sanitizeOutput(String aiResponse) {
        if (aiResponse == null || aiResponse.isEmpty()) {
            return aiResponse;
        }

        String sanitized = aiResponse;

        // 1a. 移除 <think>...</think> 思维链内容（同步路径中 LLM 可能返回思考过程）
        sanitized = sanitized.replaceAll("(?s)<think>.*?</think>", "");

        // 1b. 移除残余 HTML 标签（防止 XSS）
        sanitized = sanitized.replaceAll("<[^>]*>", "");

        // 2. 检查是否泄露了系统 Prompt 信息
        String lowerOutput = sanitized.toLowerCase();
        for (String pattern : OUTPUT_FILTER_PATTERNS) {
            if (lowerOutput.contains(pattern.toLowerCase())) {
                log.warn("输出包含潜在信息泄露: pattern='{}'", pattern);
                // 不直接拦截，但记录日志供审计
            }
        }

        // 3. PII 脱敏：手机号
        sanitized = sanitized.replaceAll("(1[3-9]\\d)\\d{4}(\\d{4})", "$1****$2");

        // 4. PII 脱敏：身份证号
        sanitized = sanitized.replaceAll(
                "(\\d{6})\\d{8}(\\d{4})", "$1********$2");

        // 5. PII 脱敏：邮箱
        sanitized = sanitized.replaceAll(
                "([a-zA-Z0-9._%+-]{1,3})[a-zA-Z0-9._%+-]*@([a-zA-Z0-9.-]+)",
                "$1***@$2");

        return sanitized;
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return "";
        return str.length() > maxLen ? str.substring(0, maxLen) + "..." : str;
    }
}

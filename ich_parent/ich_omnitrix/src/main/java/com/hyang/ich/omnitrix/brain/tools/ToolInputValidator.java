package com.hyang.ich.omnitrix.brain.tools;

import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

/**
 * 工具输入验证器 — 增强的工具参数校验，防止注入攻击和非法输入
 * 参考Claude最佳实践：所有Tool输入必须验证，绝不盲目信任
 */
@Slf4j
public class ToolInputValidator {

    // 最大输入长度限制
    private static final int MAX_INPUT_LENGTH = 2000;
    private static final int MAX_KEYWORD_LENGTH = 500;

    // 危险字符模式（防止注入攻击）
    private static final Pattern DANGEROUS_CHARS = Pattern.compile(
            "[\u0000-\u001F\u007F-\u009F]" // 控制字符
    );

    // SQL注入风险模式
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
            "(?i)(union|select|insert|update|delete|drop|create|alter|exec|execute|script|javascript)",
            Pattern.CASE_INSENSITIVE
    );

    // 命令注入风险模式
    private static final Pattern COMMAND_INJECTION_PATTERN = Pattern.compile(
            "(?i)(&&|\\|\\||;|`|\\$\\(|\\$\\{)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 验证通用字符串输入
     * @param input 输入字符串
     * @param fieldName 字段名称（用于错误信息）
     * @param maxLength 最大长度限制
     * @return 验证结果
     */
    public static ValidationResult validateString(String input, String fieldName, int maxLength) {
        if (input == null) {
            return ValidationResult.fail(fieldName + "不能为空");
        }

        // 检查长度
        if (input.length() > maxLength) {
            return ValidationResult.fail(fieldName + "长度超出限制（最大" + maxLength + "字符）");
        }

        // 检查危险字符
        if (DANGEROUS_CHARS.matcher(input).find()) {
            return ValidationResult.fail(fieldName + "包含非法字符");
        }

        return ValidationResult.ok();
    }

    /**
     * 验证搜索关键词
     */
    public static ValidationResult validateKeyword(String keyword) {
        return validateString(keyword, "关键词", MAX_KEYWORD_LENGTH);
    }

    /**
     * 验证用户消息输入
     */
    public static ValidationResult validateUserMessage(String message) {
        return validateString(message, "用户消息", MAX_INPUT_LENGTH);
    }

    /**
     * 验证用户ID
     */
    public static ValidationResult validateUserId(String userIdStr) {
        if (userIdStr == null || userIdStr.trim().isEmpty()) {
            return ValidationResult.fail("用户ID不能为空");
        }

        try {
            Long userId = Long.parseLong(userIdStr.trim());
            if (userId <= 0) {
                return ValidationResult.fail("用户ID必须为正整数");
            }
            return ValidationResult.ok();
        } catch (NumberFormatException e) {
            return ValidationResult.fail("用户ID格式无效");
        }
    }

    /**
     * 验证ID参数（通用）
     */
    public static ValidationResult validateId(String idStr, String fieldName) {
        if (idStr == null || idStr.trim().isEmpty()) {
            return ValidationResult.fail(fieldName + "不能为空");
        }

        try {
            Long id = Long.parseLong(idStr.trim());
            if (id <= 0) {
                return ValidationResult.fail(fieldName + "必须为正整数");
            }
            return ValidationResult.ok();
        } catch (NumberFormatException e) {
            return ValidationResult.fail(fieldName + "格式无效");
        }
    }

    /**
     * 验证数量参数
     */
    public static ValidationResult validateQuantity(String quantityStr) {
        if (quantityStr == null || quantityStr.trim().isEmpty()) {
            return ValidationResult.fail("数量不能为空");
        }

        try {
            int quantity = Integer.parseInt(quantityStr.trim());
            if (quantity <= 0) {
                return ValidationResult.fail("数量必须为正整数");
            }
            if (quantity > 9999) {
                return ValidationResult.fail("数量超出限制");
            }
            return ValidationResult.ok();
        } catch (NumberFormatException e) {
            return ValidationResult.fail("数量格式无效");
        }
    }

    /**
     * 验证价格参数
     */
    public static ValidationResult validatePrice(String priceStr) {
        if (priceStr == null || priceStr.trim().isEmpty()) {
            return ValidationResult.fail("价格不能为空");
        }

        try {
            double price = Double.parseDouble(priceStr.trim());
            if (price < 0) {
                return ValidationResult.fail("价格不能为负数");
            }
            if (price > 999999) {
                return ValidationResult.fail("价格超出限制");
            }
            return ValidationResult.ok();
        } catch (NumberFormatException e) {
            return ValidationResult.fail("价格格式无效");
        }
    }

    /**
     * 检查SQL注入风险
     */
    public static boolean hasSqlInjectionRisk(String input) {
        if (input == null) return false;
        return SQL_INJECTION_PATTERN.matcher(input).find();
    }

    /**
     * 检查命令注入风险
     */
    public static boolean hasCommandInjectionRisk(String input) {
        if (input == null) return false;
        return COMMAND_INJECTION_PATTERN.matcher(input).find();
    }

    /**
     * 验证SubBrain调用参数（增强版）
     */
    public static ValidationResult validateSubBrainInput(String input) {
        // 基本验证
        ValidationResult basicResult = validateString(input, "输入", MAX_INPUT_LENGTH);
        if (!basicResult.isValid()) {
            return basicResult;
        }

        // 检查注入风险
        if (hasSqlInjectionRisk(input)) {
            return ValidationResult.fail("输入包含潜在SQL注入风险");
        }

        if (hasCommandInjectionRisk(input)) {
            return ValidationResult.fail("输入包含潜在命令注入风险");
        }

        return ValidationResult.ok();
    }

    /**
     * 验证结果类
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult ok() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult fail(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
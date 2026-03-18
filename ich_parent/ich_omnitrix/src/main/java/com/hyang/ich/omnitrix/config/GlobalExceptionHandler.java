package com.hyang.ich.omnitrix.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 * 统一处理系统中的各类异常，返回规范的错误响应
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage(), e.getCode());
    }

    /**
     * 处理参数校验异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("参数异常: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "参数错误: " + e.getMessage(), "INVALID_PARAM");
    }

    /**
     * 处理空指针异常
     */
    @ExceptionHandler(NullPointerException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleNullPointerException(NullPointerException e) {
        log.error("空指针异常: ", e);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "系统内部错误", "NULL_POINTER");
    }

    /**
     * 处理LLM调用异常
     */
    @ExceptionHandler(LlmException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleLlmException(LlmException e) {
        log.error("LLM调用异常: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, "AI服务暂时不可用: " + e.getMessage(), "LLM_ERROR");
    }

    /**
     * 处理工具调用异常
     */
    @ExceptionHandler(ToolException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleToolException(ToolException e) {
        log.error("工具调用异常: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "工具执行失败: " + e.getMessage(), "TOOL_ERROR");
    }

    /**
     * 处理所有未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseBody
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception e) {
        log.error("未捕获的异常: ", e);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "系统繁忙，请稍后重试", "INTERNAL_ERROR");
    }

    /**
     * 构建统一错误响应
     */
    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String message, String code) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("code", code);
        body.put("message", message);
        body.put("timestamp", LocalDateTime.now().toString());
        return new ResponseEntity<>(body, status);
    }

    /**
     * 业务异常
     */
    public static class BusinessException extends RuntimeException {
        private final String code;

        public BusinessException(String message) {
            super(message);
            this.code = "BUSINESS_ERROR";
        }

        public BusinessException(String message, String code) {
            super(message);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    /**
     * LLM调用异常
     */
    public static class LlmException extends RuntimeException {
        public LlmException(String message) {
            super(message);
        }

        public LlmException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 工具调用异常
     */
    public static class ToolException extends RuntimeException {
        public ToolException(String message) {
            super(message);
        }

        public ToolException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

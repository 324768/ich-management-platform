package com.hyang.ich.common.exception;

import com.hyang.ich.common.enums.ResultCode;
import com.hyang.ich.common.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.failed(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<?> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("参数异常: {}", e.getMessage());
        return Result.failed(ResultCode.VALIDATE_FAILED, e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public Result<?> handleRuntimeException(RuntimeException e) {
        String msg = e.getMessage();
        if (msg != null && msg.contains("BusinessException")) {
            String bizMsg = extractBusinessMessage(msg);
            log.warn("Dubbo远程业务异常: {}", bizMsg);
            return Result.failed(ResultCode.FAILED.getCode(), bizMsg);
        }
        log.error("运行时异常: ", e);
        return Result.failed(ResultCode.FAILED, "系统内部错误: " + msg);
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("系统异常: ", e);
        return Result.failed(ResultCode.FAILED, "系统内部错误: " + e.getMessage());
    }

    private String extractBusinessMessage(String msg) {
        String prefix = "BusinessException: ";
        int idx = msg.indexOf(prefix);
        if (idx >= 0) {
            String sub = msg.substring(idx + prefix.length());
            int lineEnd = sub.indexOf('\n');
            return lineEnd > 0 ? sub.substring(0, lineEnd).trim() : sub.trim();
        }
        return msg;
    }
}

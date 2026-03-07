package com.hyang.ich.common.enums;

import lombok.Getter;

@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    FAILED(500, "操作失败"),

    // 参数错误 4xx
    VALIDATE_FAILED(400, "参数校验失败"),
    UNAUTHORIZED(401, "未登录或token已过期"),
    FORBIDDEN(403, "没有相关权限"),
    NOT_FOUND(404, "资源不存在"),

    // 业务错误 5xx
    USER_NOT_FOUND(5001, "用户不存在"),
    USER_ALREADY_EXISTS(5002, "用户名已存在"),
    PASSWORD_ERROR(5003, "密码错误"),
    ACCOUNT_DISABLED(5004, "账号已被禁用"),

    DATA_NOT_FOUND(5100, "数据不存在"),
    DATA_ALREADY_EXISTS(5101, "数据已存在"),

    ORDER_STATUS_ERROR(5200, "订单状态异常"),
    STOCK_NOT_ENOUGH(5201, "库存不足"),

    AI_SERVICE_ERROR(5300, "AI服务调用失败");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}

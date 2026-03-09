package com.hyang.ich.system.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class SysOperationLogDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String username;
    private String module;
    private String operation;
    private String method;
    private String requestMethod;
    private String requestUri;
    private String ip;
    private Integer status;        // 0-成功 1-失败
    private String errorMsg;
    private Long executeTime;      // 执行时长(毫秒)
    private Date operationTime;
}

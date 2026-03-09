package com.hyang.ich.user.entity;

import lombok.Data;

import java.util.Date;

@Data
public class SysOperationLog {

    private Long id;
    private Long userId;
    private String username;
    private String module;
    private String operation;
    private String method;
    private String requestMethod;
    private String requestUri;
    private String requestParams;
    private String responseResult;
    private String ip;
    private String userAgent;
    private Integer status;        // 0-成功 1-失败
    private String errorMsg;
    private Long executeTime;      // 执行时长(毫秒)
    private Date operationTime;
    private Date createTime;
    private Date updateTime;
    private Integer isDeleted;
    private Integer version;
}

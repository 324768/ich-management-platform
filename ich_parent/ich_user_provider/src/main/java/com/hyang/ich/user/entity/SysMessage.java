package com.hyang.ich.user.entity;

import lombok.Data;

import java.util.Date;

@Data
public class SysMessage {
    private Long id;
    private String title;
    private String content;
    private Integer messageType;
    private Integer receiverType;
    private String receiverIds;
    private Integer isPublished;
    private Date publishTime;
    private Date expireTime;
    private Long createBy;
    private Date createTime;
    private Date updateTime;
    private Integer isDeleted;
}

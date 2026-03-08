package com.hyang.ich.system.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class SysNotificationDTO implements Serializable {

    private static final long serialVersionUID = 1L;

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
}

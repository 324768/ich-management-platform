package com.hyang.ich.content.entity;

import lombok.Data;

import java.util.Date;

@Data
public class IchActivityComment {
    private Long id;
    private Long activityId;
    private Long userId;
    private String userName;
    private String userAvatar;
    private String content;
    private Integer status;
    private Date createTime;
    private Date updateTime;
    private Integer isDeleted;
}

package com.hyang.ich.content.entity;

import lombok.Data;

import java.util.Date;

@Data
public class IchActivityRecord {
    private Long id;
    private Long activityId;
    private String activityName;
    private Long userId;
    private String userName;
    private String userPhone;
    private Date registrationTime;
    private Date checkInTime;
    private Integer status;
    private String remark;
    private Date createTime;
    private Date updateTime;
    private Integer isDeleted;
}

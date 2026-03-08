package com.hyang.ich.content.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class IchActivityRecordDTO implements Serializable {

    private static final long serialVersionUID = 1L;

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
}

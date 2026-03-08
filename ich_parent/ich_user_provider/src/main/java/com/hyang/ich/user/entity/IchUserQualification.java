package com.hyang.ich.user.entity;

import lombok.Data;

import java.util.Date;

@Data
public class IchUserQualification {
    private Long id;
    private Long userId;
    private String userName;
    private String userPhone;
    private Integer qualificationType; // 1=传承人 2=公司/组织
    private String title;
    private String description;
    private String materials;        // JSON数组
    private String idCardFront;
    private String idCardBack;
    private String certificateImages; // JSON数组
    private Integer status;          // 0=待审核 1=已通过 2=已拒绝
    private String rejectReason;
    private Long reviewerId;
    private Date reviewTime;
    private Date createTime;
    private Date updateTime;
    private Integer isDeleted;
}

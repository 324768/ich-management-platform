package com.hyang.ich.content.entity;

import lombok.Data;

import java.util.Date;

@Data
public class IchActivity {
    private Long id;
    private String name;
    private Integer activityType;
    private String coverImage;
    private String description;
    private String content;
    private String location;
    private Date startTime;
    private Date endTime;
    private Integer maxParticipants;
    private Integer currentParticipants;
    private Date registrationDeadline;
    private String contactPerson;
    private String contactPhone;
    private String organizer;
    private String requester;
    private String requestUnit;
    private String detailImages;
    private String videos;
    private String timelineData;
    private Long itemId;
    private Integer status;
    private Integer approvalStatus;   // 0=无需审批 1=待审批 2=审批通过 3=审批拒绝
    private String rejectReason;
    private Long reviewerId;
    private Date reviewTime;
    private Long publisherUserId;
    private Integer sort;
    private Date createTime;
    private Date updateTime;
    private Integer isDeleted;
}

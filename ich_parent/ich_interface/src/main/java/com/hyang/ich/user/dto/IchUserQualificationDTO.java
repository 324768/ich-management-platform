package com.hyang.ich.user.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
public class IchUserQualificationDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String userName;
    private String userPhone;
    private Integer qualificationType; // 1=传承人 2=公司/组织
    private String title;
    private String description;
    private List<String> materials;
    private String idCardFront;
    private String idCardBack;
    private List<String> certificateImages;
    private Integer status;          // 0=待审核 1=已通过 2=已拒绝
    private String rejectReason;
    private Long reviewerId;
    private Date reviewTime;
    private Date createTime;
    private Date updateTime;
}

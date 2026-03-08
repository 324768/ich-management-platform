package com.hyang.ich.user.entity;

import lombok.Data;

import java.util.Date;

@Data
public class User {

    private Long id;
    private String username;
    private String password;
    private String nickname;
    private String avatar;
    private String email;
    private String phone;
    private Integer status;
    private Date lastLoginTime;
    private String lastLoginIp;
    private Integer heritageFlag;    // 0=普通用户 1=已认证传承人/公司
    private Long qualificationId;
    private Integer isDeleted;
    private Integer version;
    private Date createTime;
    private Date updateTime;
}

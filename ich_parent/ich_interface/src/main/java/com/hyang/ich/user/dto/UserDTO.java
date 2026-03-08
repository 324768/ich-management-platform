package com.hyang.ich.user.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class UserDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String email;
    private String phone;
    private Integer status;
    private Integer heritageFlag;    // 0=普通用户 1=已认证传承人/公司
    private Date lastLoginTime;
    private String lastLoginIp;
    private Date createTime;
    private Date updateTime;
}



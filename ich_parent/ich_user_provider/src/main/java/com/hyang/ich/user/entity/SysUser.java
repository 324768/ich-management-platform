package com.hyang.ich.user.entity;

import lombok.Data;
import java.util.Date;

@Data
public class SysUser {
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
    private Date createTime;
    private Date updateTime;
    private Integer isDeleted;
}

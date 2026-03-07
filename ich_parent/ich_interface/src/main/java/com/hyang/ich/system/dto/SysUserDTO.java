package com.hyang.ich.system.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
public class SysUserDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private String avatar;
    private Integer status;
    private Date lastLoginTime;
    private String lastLoginIp;
    private Date createTime;
    private Date updateTime;

    /** 角色列表 */
    private List<SysRoleDTO> roles;
}

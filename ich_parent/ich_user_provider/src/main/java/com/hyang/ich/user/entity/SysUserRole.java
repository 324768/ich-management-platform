package com.hyang.ich.user.entity;

import lombok.Data;
import java.util.Date;

@Data
public class SysUserRole {
    private Long id;
    private Long userId;
    private Long roleId;
    private Date createTime;
}

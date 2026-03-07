package com.hyang.ich.user.entity;

import lombok.Data;
import java.util.Date;

@Data
public class SysRole {
    private Long id;
    private String name;
    private String code;
    private String description;
    private Integer sort;
    private Integer status;
    private Date createTime;
    private Date updateTime;
    private Integer isDeleted;
}

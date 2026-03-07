package com.hyang.ich.system.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class SysRoleDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String code;
    private String description;
    private Integer sort;
    private Integer status;
    private Date createTime;
    private Date updateTime;
}

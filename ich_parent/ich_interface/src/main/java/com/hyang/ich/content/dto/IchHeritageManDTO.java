package com.hyang.ich.content.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class IchHeritageManDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private Integer gender;
    private Date birthDate;
    private String avatar;
    private Integer level;
    private Long categoryId;
    private Long itemId;
    private String title;
    private String skill;
    private String introduction;
    private String achievement;
    private Integer status;
    private Date createTime;
    private Date updateTime;
}

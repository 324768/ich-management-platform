package com.hyang.ich.content.entity;

import lombok.Data;
import java.util.Date;

@Data
public class IchHeritageMan {
    private Long id;
    private String name;
    private Integer gender;
    private Date birthDate;
    private String idCard;
    private String phone;
    private String email;
    private String address;
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
    private Integer isDeleted;
}

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
    private String idCard;
    private String phone;
    private String email;
    private String address;
    private String avatar;
    private Integer level;
    private Long categoryId;
    private String categoryName;
    private Long itemId;
    private String itemName;
    private String title;
    private String skill;
    private String introduction;
    private String achievement;
    private java.util.List<String> detailImages;
    private java.util.List<String> videos;
    private Integer status;
    private Date createTime;
    private Date updateTime;
}

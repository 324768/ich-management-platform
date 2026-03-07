package com.hyang.ich.user.entity;

import lombok.Data;

import java.util.Date;

@Data
public class UserAddress {

    private Long id;
    private Long userId;
    private String receiverName;
    private String receiverPhone;
    private String province;
    private String city;
    private String district;
    private String detailAddress;
    private Integer isDefault;
    private Integer isDeleted;
    private Date createTime;
    private Date updateTime;
}

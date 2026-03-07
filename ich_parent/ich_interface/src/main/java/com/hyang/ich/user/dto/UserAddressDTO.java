package com.hyang.ich.user.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class UserAddressDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String receiverName;
    private String receiverPhone;
    private String province;
    private String city;
    private String district;
    private String detailAddress;
    private Integer isDefault;
    private Date createTime;
    private Date updateTime;
}

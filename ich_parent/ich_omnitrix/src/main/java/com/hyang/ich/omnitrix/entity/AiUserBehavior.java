package com.hyang.ich.omnitrix.entity;

import lombok.Data;

import java.util.Date;

@Data
public class AiUserBehavior {

    private Long id;
    private Long userId;
    private Long itemId;
    private String itemType;
    private Integer behaviorType;
    private String keywords;
    private Date createTime;
}

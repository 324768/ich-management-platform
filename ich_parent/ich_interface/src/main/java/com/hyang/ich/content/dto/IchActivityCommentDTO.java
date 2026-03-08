package com.hyang.ich.content.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class IchActivityCommentDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long activityId;
    private Long userId;
    private String userName;
    private String userAvatar;
    private String content;
    private Integer status;
    private Date createTime;
}

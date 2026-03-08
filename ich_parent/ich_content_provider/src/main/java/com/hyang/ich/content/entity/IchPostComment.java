package com.hyang.ich.content.entity;

import lombok.Data;

import java.util.Date;

@Data
public class IchPostComment {
    private Long id;
    private Long postId;
    private Long userId;
    private String userName;
    private String userAvatar;
    private String content;
    private String images;
    private Integer likeCount;
    private String location;
    private Integer status;
    private Integer isDeleted;
    private Date createTime;
}

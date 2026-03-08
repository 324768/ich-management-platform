package com.hyang.ich.content.entity;

import lombok.Data;

import java.util.Date;

@Data
public class IchPost {
    private Long id;
    private Long userId;
    private String userName;
    private String userAvatar;
    private String title;
    private String content;
    private String images;
    private String videoUrl;
    private String coverImage;
    private String tags;
    private Integer type;
    private Integer likeCount;
    private Integer favoriteCount;
    private Integer commentCount;
    private Integer viewCount;
    private Integer status;
    private Integer isDeleted;
    private Date createTime;
    private Date updateTime;
}

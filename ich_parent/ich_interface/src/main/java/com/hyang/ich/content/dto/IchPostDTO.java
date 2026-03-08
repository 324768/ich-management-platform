package com.hyang.ich.content.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class IchPostDTO implements Serializable {

    private static final long serialVersionUID = 1L;

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
    private Date createTime;
    private Date updateTime;
}

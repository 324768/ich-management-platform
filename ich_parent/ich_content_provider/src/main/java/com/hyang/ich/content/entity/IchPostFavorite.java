package com.hyang.ich.content.entity;

import lombok.Data;

import java.util.Date;

@Data
public class IchPostFavorite {
    private Long id;
    private Long postId;
    private Long userId;
    private Date createTime;
}

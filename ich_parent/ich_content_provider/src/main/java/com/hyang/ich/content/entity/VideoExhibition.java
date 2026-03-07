package com.hyang.ich.content.entity;

import lombok.Data;
import java.util.Date;

@Data
public class VideoExhibition {
    private Long id;
    private String title;
    private String coverUrl;
    private String videoUrl;
    private Integer duration;
    private Long categoryId;
    private String description;
    private String content;
    private Integer viewCount;
    private Integer likeCount;
    private Integer status;
    private Integer sort;
    private Date createTime;
    private Date updateTime;
}

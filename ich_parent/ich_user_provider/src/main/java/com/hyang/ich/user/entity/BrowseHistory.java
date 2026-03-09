package com.hyang.ich.user.entity;

import lombok.Data;

import java.util.Date;

@Data
public class BrowseHistory {

    private Long id;
    private Long userId;
    private String targetType;
    private Long targetId;
    private String targetTitle;
    private Date browseDate;
    private Date browseTime;
    private Integer durationSeconds;
    private String source;
    private Date createTime;
}

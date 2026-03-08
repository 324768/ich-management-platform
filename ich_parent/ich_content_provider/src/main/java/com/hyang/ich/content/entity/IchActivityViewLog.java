package com.hyang.ich.content.entity;

import lombok.Data;

import java.util.Date;

@Data
public class IchActivityViewLog {
    private Long id;
    private Long activityId;
    private Long userId;
    private String userName;
    private Date viewTime;
}

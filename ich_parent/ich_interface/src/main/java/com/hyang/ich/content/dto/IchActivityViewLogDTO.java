package com.hyang.ich.content.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class IchActivityViewLogDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long activityId;
    private Long userId;
    private String userName;
    private Date viewTime;
}

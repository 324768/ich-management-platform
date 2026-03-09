package com.hyang.ich.common.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class BrowseHistoryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String targetType;      // ich_item/heritage_man/activity/product/knowledge
    private Long targetId;
    private String targetTitle;
    private Date browseDate;        // 浏览日期(按天分组)
    private Date browseTime;        // 浏览时间(精确)
    private Integer durationSeconds;
    private String source;          // web/app/mini_program
    private Date createTime;
}

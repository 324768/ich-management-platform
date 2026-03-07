package com.hyang.ich.content.entity;

import lombok.Data;
import java.util.Date;

@Data
public class IchCategory {
    private Long id;
    private Long parentId;
    private String name;
    private Integer level;
    private Integer sort;
    private String icon;
    private String detailImages;
    private Integer status;
    private Date createTime;
    private Date updateTime;
}

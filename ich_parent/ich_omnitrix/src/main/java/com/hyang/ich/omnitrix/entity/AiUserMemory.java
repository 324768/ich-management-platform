package com.hyang.ich.omnitrix.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class AiUserMemory {

    private Long id;
    private Long userId;
    private String memoryType;
    private String memoryKey;
    private String memoryValue;
    private BigDecimal confidence;
    private String source;
    private Integer hitCount;
    private Integer status;
    private Date createTime;
    private Date updateTime;
}

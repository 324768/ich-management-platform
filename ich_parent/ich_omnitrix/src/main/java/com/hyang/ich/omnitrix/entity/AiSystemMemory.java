package com.hyang.ich.omnitrix.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class AiSystemMemory {

    private Long id;
    private String memoryType;
    private String memoryKey;
    private String memoryValue;
    private String createdBy;
    private BigDecimal confidence;
    private Integer hitCount;
    private Integer isActive;
    private Date createTime;
    private Date updateTime;
}

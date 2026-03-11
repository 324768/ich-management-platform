package com.hyang.ich.omnitrix.entity;

import lombok.Data;

import java.util.Date;

/**
 * AI技能配置实体类
 * 用于管理员动态管理Skills的开启/关闭
 */
@Data
public class AiSkillConfig {

    /**
     * Skill唯一标识
     */
    private String skillId;

    /**
     * Skill名称
     */
    private String skillName;

    /**
     * Skill描述
     */
    private String description;

    /**
     * Skill的系统Prompt
     */
    private String systemPrompt;

    /**
     * 关键词，多个用逗号分隔
     */
    private String keywords;

    /**
     * 是否启用：0-禁用，1-启用
     */
    private Integer enabled;

    /**
     * 显示顺序
     */
   ;

    /**
     private Integer displayOrder * 创建时间
     */
    private Date createdAt;

    /**
     * 更新时间
     */
    private Date updatedAt;

    /**
     * 创建人
     */
    private String createdBy;

    /**
     * 更新人
     */
    private String updatedBy;

    /**
     * 版本号，用于缓存刷新
     */
    private Integer version;
}

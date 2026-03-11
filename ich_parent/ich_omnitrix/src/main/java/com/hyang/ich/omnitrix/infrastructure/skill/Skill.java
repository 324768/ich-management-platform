package com.hyang.ich.omnitrix.infrastructure.skill;

import lombok.Data;

/**
 * Skill实体类 - 代表一个可动态加载的技能
 */
@Data
public class Skill {
    
    /**
     * Skill唯一标识
     */
    private String id;
    
    /**
     * Skill名称
     */
    private String name;
    
    /**
     * Skill描述（用于LLM判断是否调用）
     */
    private String description;
    
    /**
     * Skill的系统Prompt
     */
    private String systemPrompt;
    
    /**
     * 适用领域关键词（帮助快速匹配）
     */
    private String[] keywords;
    
    /**
     * 是否启用
     */
    private boolean enabled;
    
    public Skill(String id, String name, String description, String systemPrompt, String[] keywords, boolean enabled) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.systemPrompt = systemPrompt;
        this.keywords = keywords;
        this.enabled = enabled;
    }
}

package com.hyang.ich.omnitrix.infrastructure.skill;

import lombok.Data;

/**
 * Skill实体类 - 参考Claude Skill系统设计
 * 
 * Claude Skill特点：
 * 1. Skill是自激活的——AI根据任务自动判断是否启用相关Skill
 * 2. Skill定义包含YAML frontmatter（name、description）供AI理解
 * 3. Skill可以包含模板文件、脚本、示例等辅助资源
 * 4. Skill通过语义匹配自动触发，而非关键词匹配
 */
@Data
public class Skill {

    /**
     * Skill唯一标识（对应Claude的文件夹名）
     */
    private String id;

    /**
     * Skill名称（对应Claude的name）
     */
    private String name;

    /**
     * Skill描述（对应Claude的description，供AI理解何时调用）
     */
    private String description;

    /**
     * Skill的系统Prompt（核心指令）
     */
    private String systemPrompt;

    /**
     * 触发条件描述（让AI自己判断是否需要激活）
     * 类似Claude的"当用户XXX时使用此Skill"
     */
    private String triggerCondition;

    /**
     * 使用示例（帮助AI理解如何正确使用）
     */
    private String[] examples;

    /**
     * 辅助资源路径（模板文件、脚本等）
     */
    private String[] resourcePaths;

    /**
     * 适用领域关键词（保留作为快速预筛）
     */
    private String[] keywords;

    /**
     * 是否启用
     */
    private boolean enabled;

    /**
     * Skill优先级（数值越大优先级越高）
     */
    private int priority;

    /**
     * 分类标签（用于Skill组织）
     */
    private String category;

    public Skill() {
    }

    public Skill(String id, String name, String description, String systemPrompt, 
                 String triggerCondition, String[] examples, String[] resourcePaths,
                 String[] keywords, boolean enabled, int priority, String category) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.systemPrompt = systemPrompt;
        this.triggerCondition = triggerCondition;
        this.examples = examples;
        this.resourcePaths = resourcePaths;
        this.keywords = keywords;
        this.enabled = enabled;
        this.priority = priority;
        this.category = category;
    }

    /**
     * 兼容旧构造函数
     */
    public Skill(String id, String name, String description, String systemPrompt, 
                 String[] keywords, boolean enabled) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.systemPrompt = systemPrompt;
        this.keywords = keywords;
        this.enabled = enabled;
        this.triggerCondition = "当用户询问" + description + "相关问题时";
        this.priority = 0;
        this.category = "general";
    }

    /**
     * 生成用于AI理解的Skill元信息（类似Claude的YAML frontmatter）
     */
    public String toMetadataString() {
        StringBuilder sb = new StringBuilder();
        sb.append("## Skill: ").append(name).append("\n");
        sb.append("- ID: ").append(id).append("\n");
        sb.append("- 描述: ").append(description).append("\n");
        if (triggerCondition != null) {
            sb.append("- 触发条件: ").append(triggerCondition).append("\n");
        }
        if (examples != null && examples.length > 0) {
            sb.append("- 使用示例:\n");
            for (String ex : examples) {
                sb.append("  * ").append(ex).append("\n");
            }
        }
        return sb.toString();
    }
}
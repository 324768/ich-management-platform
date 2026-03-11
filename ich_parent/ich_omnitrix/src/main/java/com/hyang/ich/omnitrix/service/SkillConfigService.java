package com.hyang.ich.omnitrix.service;

import com.hyang.ich.omnitrix.entity.AiSkillConfig;
import com.hyang.ich.omnitrix.infrastructure.skill.SkillPromptConfig;
import com.hyang.ich.omnitrix.mapper.AiSkillConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI技能配置管理Service
 * 提供Skill的增删改查及启用/禁用功能
 */
@Slf4j
@Service
public class SkillConfigService {

    private final AiSkillConfigMapper skillConfigMapper;
    private SkillPromptConfig skillPromptConfig;

    public SkillConfigService(AiSkillConfigMapper skillConfigMapper) {
        this.skillConfigMapper = skillConfigMapper;
    }

    /**
     * 设置SkillPromptConfig引用（用于刷新缓存）
     */
    public void setSkillPromptConfig(SkillPromptConfig skillPromptConfig) {
        this.skillPromptConfig = skillPromptConfig;
    }

    /**
     * 获取所有启用的Skills
     */
    public List<AiSkillConfig> getEnabledSkills() {
        return skillConfigMapper.selectEnabled();
    }

    /**
     * 获取所有Skills
     */
    public List<AiSkillConfig> getAllSkills() {
        return skillConfigMapper.selectAll();
    }

    /**
     * 根据ID获取Skill
     */
    public AiSkillConfig getSkillById(String skillId) {
        return skillConfigMapper.selectById(skillId);
    }

    /**
     * 分页查询Skills
     */
    public List<AiSkillConfig> getSkillsPage(int page, int pageSize, String keyword) {
        int offset = (page - 1) * pageSize;
        return skillConfigMapper.selectPage(offset, pageSize, keyword);
    }

    /**
     * 统计Skill总数
     */
    public int countSkills(String keyword) {
        return skillConfigMapper.countAll(keyword);
    }

    /**
     * 更新单个Skill的启用状态
     */
    public boolean updateSkillEnabled(String skillId, Integer enabled) {
        int result = skillConfigMapper.updateEnabled(skillId, enabled);
        if (result > 0) {
            log.info("Skill [{}] 启用状态已更新为: {}", skillId, enabled);
            // 刷新SkillPromptConfig缓存
            refreshSkillPromptConfig();
            return true;
        }
        return false;
    }

    /**
     * 批量更新Skill启用状态
     */
    public int batchUpdateEnabled(List<String> skillIds, Integer enabled) {
        int result = skillConfigMapper.batchUpdateEnabled(skillIds, enabled);
        log.info("批量更新 {} 个Skills的启用状态为: {}", skillIds.size(), enabled);
        // 刷新SkillPromptConfig缓存
        refreshSkillPromptConfig();
        return result;
    }

    /**
     * 刷新SkillPromptConfig缓存
     */
    private void refreshSkillPromptConfig() {
        if (skillPromptConfig != null) {
            try {
                skillPromptConfig.refresh();
                log.info("SkillPromptConfig缓存已刷新");
            } catch (Exception e) {
                log.warn("刷新SkillPromptConfig缓存失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 启用指定Skill
     */
    public boolean enableSkill(String skillId) {
        return updateSkillEnabled(skillId, 1);
    }

    /**
     * 禁用指定Skill
     */
    public boolean disableSkill(String skillId) {
        return updateSkillEnabled(skillId, 0);
    }

    /**
     * 获取Skill版本号（用于缓存刷新）
     */
    public Integer getSkillVersion(String skillId) {
        return skillConfigMapper.selectVersionById(skillId);
    }

    /**
     * 初始化默认Skills（如果数据库为空）
     */
    public void initDefaultSkills() {
        List<AiSkillConfig> existing = skillConfigMapper.selectAll();
        if (existing == null || existing.isEmpty()) {
            log.info("初始化默认Skills配置...");
            // 默认数据已在SQL中初始化，这里可以添加额外逻辑
        }
    }
}

package com.hyang.ich.omnitrix.config;

import com.hyang.ich.omnitrix.infrastructure.skill.SkillPromptConfig;
import com.hyang.ich.omnitrix.service.SkillConfigService;
import javax.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * Skill配置循环依赖处理
 * 在SkillConfigService创建后，将SkillPromptConfig注入
 */
@Component
public class SkillConfigCyclicReferenceSetup {

    private final SkillConfigService skillConfigService;
    private final SkillPromptConfig skillPromptConfig;

    public SkillConfigCyclicReferenceSetup(SkillConfigService skillConfigService,
                                          SkillPromptConfig skillPromptConfig) {
        this.skillConfigService = skillConfigService;
        this.skillPromptConfig = skillPromptConfig;
    }

    @PostConstruct
    public void setupCircularReference() {
        skillConfigService.setSkillPromptConfig(skillPromptConfig);
    }
}

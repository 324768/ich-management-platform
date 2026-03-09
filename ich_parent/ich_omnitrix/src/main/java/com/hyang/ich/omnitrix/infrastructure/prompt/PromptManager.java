package com.hyang.ich.omnitrix.infrastructure.prompt;

import com.hyang.ich.omnitrix.mapper.AiPromptConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class PromptManager {

    /** 本地缓存 TTL：5 分钟 */
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final AiPromptConfigMapper promptConfigMapper;

    @Value("${omnitrix.prompts.master-brain-system:}")
    private String ymlMasterBrainPrompt;

    public PromptManager(AiPromptConfigMapper promptConfigMapper) {
        this.promptConfigMapper = promptConfigMapper;
    }

    /**
     * 三层回退解析 Prompt：
     * Layer 1: 数据库 ai_prompt_config 表
     * Layer 2: application.yml 配置
     * Layer 3: 代码常量 PromptTemplate
     */
    public String resolve(String promptKey) {
        // Layer 0: 本地缓存
        CacheEntry cached = cache.get(promptKey);
        if (cached != null && !cached.isExpired()) {
            return cached.value;
        }

        String resolved = doResolve(promptKey);
        cache.put(promptKey, new CacheEntry(resolved));
        return resolved;
    }

    /**
     * 主动失效缓存（管理员修改 Prompt 后调用）
     */
    public void invalidateCache(String promptKey) {
        if (promptKey != null) {
            cache.remove(promptKey);
        } else {
            cache.clear();
        }
        log.debug("Prompt 缓存已失效: key={}", promptKey);
    }

    private String doResolve(String promptKey) {
        // Layer 1: 数据库
        try {
            String dbPrompt = promptConfigMapper.selectContentByKey(promptKey);
            if (StringUtils.isNotBlank(dbPrompt)) {
                log.debug("Prompt [{}] 从数据库加载", promptKey);
                return dbPrompt;
            }
        } catch (Exception e) {
            log.warn("从数据库加载 Prompt [{}] 失败: {}", promptKey, e.getMessage());
        }

        // Layer 2: yml 配置（目前只支持 master_brain_system）
        if ("master_brain_system".equals(promptKey) && StringUtils.isNotBlank(ymlMasterBrainPrompt)) {
            log.debug("Prompt [{}] 从 yml 加载", promptKey);
            return ymlMasterBrainPrompt;
        }

        // Layer 3: 代码常量
        log.debug("Prompt [{}] 使用代码常量兜底", promptKey);
        return getDefaultPrompt(promptKey);
    }

    private static class CacheEntry {
        final String value;
        final long expireAt;

        CacheEntry(String value) {
            this.value = value;
            this.expireAt = System.currentTimeMillis() + CACHE_TTL_MS;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }

    private String getDefaultPrompt(String promptKey) {
        switch (promptKey) {
            case "master_brain_system":
                return PromptTemplate.MASTER_BRAIN_SYSTEM;
            case "memory_summarizer":
                return PromptTemplate.MEMORY_SUMMARIZER;
            case "self_evaluator":
                return PromptTemplate.SELF_EVALUATOR;
            case "title_generator":
                return PromptTemplate.TITLE_GENERATOR;
            default:
                return PromptTemplate.MASTER_BRAIN_SYSTEM;
        }
    }
}

package com.hyang.ich.omnitrix.service;

import com.hyang.ich.omnitrix.entity.AiSystemMemory;
import com.hyang.ich.omnitrix.mapper.AiSystemMemoryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 第四层记忆：系统全局记忆服务
 * 管理平台级别的策略、模式、决策、洞察和指令记忆。
 */
@Slf4j
@Service
public class SystemMemoryService {

    private static final String CACHE_KEY = "omnitrix:system_memory";
    private static final long CACHE_TTL_MINUTES = 60;
    private static final int MAX_MEMORIES = 100;

    private final AiSystemMemoryMapper memoryMapper;
    private final StringRedisTemplate redisTemplate;

    public SystemMemoryService(AiSystemMemoryMapper memoryMapper, StringRedisTemplate redisTemplate) {
        this.memoryMapper = memoryMapper;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 构建系统记忆文本，用于注入 Ultra AI 的 System Prompt。
     * 返回所有有效记忆的文本描述。
     */
    public String buildSystemMemoryPrompt() {
        // 1. 优先从 Redis 缓存读取
        try {
            String cached = redisTemplate.opsForValue().get(CACHE_KEY);
            if (cached != null) {
                return cached.isEmpty() ? null : cached;
            }
        } catch (Exception e) {
            log.debug("读取系统记忆缓存失败: {}", e.getMessage());
        }

        // 2. 从 DB 构建
        String prompt = doBuildPrompt();

        // 3. 写入缓存
        try {
            redisTemplate.opsForValue().set(CACHE_KEY, prompt != null ? prompt : "",
                    CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.debug("写入系统记忆缓存失败: {}", e.getMessage());
        }

        return prompt;
    }

    /**
     * 构建仅包含 policy 类型的系统记忆（给普通管理员 AI 使用）
     */
    public String buildPolicyMemoryPrompt() {
        List<AiSystemMemory> policies = memoryMapper.selectByType("policy");
        if (policies == null || policies.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        for (AiSystemMemory m : policies) {
            sb.append("- ").append(m.getMemoryValue()).append("\n");
            try { memoryMapper.incrementHitCount(m.getId()); } catch (Exception ignored) {}
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? null : result;
    }

    /**
     * 保存或更新系统记忆
     */
    public void saveOrUpdate(String type, String key, String value, String createdBy, double confidence) {
        AiSystemMemory existing = memoryMapper.selectByKey(key);
        if (existing != null) {
            memoryMapper.updateValue(existing.getId(), value, BigDecimal.valueOf(confidence));
            log.info("系统记忆更新: key={}", key);
        } else {
            int count = memoryMapper.countActive();
            if (count >= MAX_MEMORIES) {
                log.warn("系统记忆已达上限({}), 跳过写入: key={}", MAX_MEMORIES, key);
                return;
            }
            AiSystemMemory memory = new AiSystemMemory();
            memory.setMemoryType(type);
            memory.setMemoryKey(key);
            memory.setMemoryValue(value);
            memory.setCreatedBy(createdBy);
            memory.setConfidence(BigDecimal.valueOf(confidence));
            memoryMapper.insert(memory);
            log.info("系统记忆新增: type={}, key={}", type, key);
        }
        invalidateCache();
    }

    /**
     * 记录 Ultra 指令到系统记忆
     */
    public void saveDirective(String key, String value, String adminId) {
        saveOrUpdate("directive", key, value, "ultra_admin_" + adminId, 1.0);
    }

    /**
     * 清除缓存
     */
    public void invalidateCache() {
        try {
            redisTemplate.delete(CACHE_KEY);
        } catch (Exception e) {
            log.debug("清除系统记忆缓存失败: {}", e.getMessage());
        }
    }

    private String doBuildPrompt() {
        List<AiSystemMemory> memories = memoryMapper.selectActive();
        if (memories == null || memories.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        appendSection(sb, memories, "directive", "管理指令");
        appendSection(sb, memories, "policy", "运营策略");
        appendSection(sb, memories, "pattern", "行为模式");
        appendSection(sb, memories, "decision", "历史决策");
        appendSection(sb, memories, "insight", "AI洞察");

        for (AiSystemMemory m : memories) {
            try { memoryMapper.incrementHitCount(m.getId()); } catch (Exception ignored) {}
        }

        String result = sb.toString().trim();
        return result.isEmpty() ? null : result;
    }

    private void appendSection(StringBuilder sb, List<AiSystemMemory> memories,
                                String type, String label) {
        boolean hasContent = false;
        for (AiSystemMemory m : memories) {
            if (type.equals(m.getMemoryType())) {
                if (!hasContent) {
                    sb.append("### ").append(label).append("\n");
                    hasContent = true;
                }
                sb.append("- ").append(m.getMemoryValue()).append("\n");
            }
        }
        if (hasContent) sb.append("\n");
    }
}

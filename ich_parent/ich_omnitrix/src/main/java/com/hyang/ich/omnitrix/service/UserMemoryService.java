package com.hyang.ich.omnitrix.service;

import com.hyang.ich.omnitrix.entity.AiUserMemory;
import com.hyang.ich.omnitrix.mapper.AiUserBehaviorMapper;
import com.hyang.ich.omnitrix.mapper.AiUserMemoryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 用户长期记忆服务：提供跨会话的用户画像加载，融合长期记忆 + 行为数据。
 * 对应 OpenClaw 启动时自动加载 MEMORY.md + USER.md 的机制。
 *
 * Redis 缓存策略：
 * - Key: user_memory:{userId}
 * - TTL: 30分钟（避免每次对话都查DB）
 * - 失效时机: 新记忆写入时主动清除
 */
@Slf4j
@Service
public class UserMemoryService {

    private static final String CACHE_KEY_PREFIX = "omnitrix:user_memory:";
    private static final long CACHE_TTL_MINUTES = 30;

    private final AiUserMemoryMapper memoryMapper;
    private final AiUserBehaviorMapper behaviorMapper;
    private final StringRedisTemplate redisTemplate;

    public UserMemoryService(AiUserMemoryMapper memoryMapper,
                              AiUserBehaviorMapper behaviorMapper,
                              StringRedisTemplate redisTemplate) {
        this.memoryMapper = memoryMapper;
        this.behaviorMapper = behaviorMapper;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 构建用户画像文本，用于注入 System Prompt。
     * 融合长期记忆(ai_user_memory) + 行为兴趣(ai_user_behavior)。
     *
     * @return 用户画像文本，为空则返回 null
     */
    public String buildUserProfile(Long userId) {
        if (userId == null) return null;

        // 1. 优先从 Redis 缓存读取
        String cacheKey = CACHE_KEY_PREFIX + userId;
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return cached.isEmpty() ? null : cached;
            }
        } catch (Exception e) {
            log.debug("读取用户画像缓存失败: {}", e.getMessage());
        }

        // 2. 从 DB 构建画像
        String profile = doBuildProfile(userId);

        // 3. 写入缓存（空值也缓存，避免穿透）
        try {
            redisTemplate.opsForValue().set(cacheKey, profile != null ? profile : "",
                    CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.debug("写入用户画像缓存失败: {}", e.getMessage());
        }

        return profile;
    }

    /**
     * 清除用户画像缓存（新记忆写入后调用）
     */
    public void invalidateCache(Long userId) {
        try {
            redisTemplate.delete(CACHE_KEY_PREFIX + userId);
        } catch (Exception e) {
            log.debug("清除用户画像缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 从 DB 构建用户画像文本
     */
    private String doBuildProfile(Long userId) {
        StringBuilder sb = new StringBuilder();

        // Part 1: 长期记忆 (ai_user_memory)
        List<AiUserMemory> memories = memoryMapper.selectByUserId(userId);
        if (memories != null && !memories.isEmpty()) {
            // 按类型分组展示
            appendMemorySection(sb, memories, "preference", "偏好");
            appendMemorySection(sb, memories, "interest", "兴趣");
            appendMemorySection(sb, memories, "fact", "已知事实");
            appendMemorySection(sb, memories, "decision", "重要决策");
            appendMemorySection(sb, memories, "lesson", "经验教训");

            // 批量更新命中次数
            for (AiUserMemory m : memories) {
                try {
                    memoryMapper.incrementHitCount(m.getId());
                } catch (Exception ignored) {}
            }
        }

        // Part 2: 行为兴趣 (ai_user_behavior 近30天)
        try {
            List<Map<String, Object>> interests = behaviorMapper.selectUserInterests(userId, 30, 5);
            if (interests != null && !interests.isEmpty()) {
                sb.append("- 近期关注: ");
                boolean first = true;
                for (Map<String, Object> interest : interests) {
                    String keywords = (String) interest.get("keywords");
                    if (keywords != null && !keywords.isEmpty()) {
                        if (!first) sb.append("、");
                        sb.append(keywords);
                        first = false;
                    }
                }
                if (!first) sb.append("\n");
            }
        } catch (Exception e) {
            log.debug("加载行为兴趣失败: {}", e.getMessage());
        }

        String result = sb.toString().trim();
        return result.isEmpty() ? null : result;
    }

    /**
     * 按类型追加记忆段落
     */
    private void appendMemorySection(StringBuilder sb, List<AiUserMemory> memories,
                                      String type, String label) {
        boolean hasContent = false;
        for (AiUserMemory m : memories) {
            if (type.equals(m.getMemoryType())) {
                if (!hasContent) {
                    hasContent = true;
                }
                sb.append("- ").append(label).append(": ").append(m.getMemoryValue()).append("\n");
            }
        }
    }
}

package com.hyang.ich.omnitrix.infrastructure.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
public class RedisChatMemoryStore {

    private static final String SESSION_KEY_PREFIX = "chat:session:";
    private static final String LIST_KEY_PREFIX = "chat:list:";
    private static final String SUMMARY_KEY_PREFIX = "chat:summary:";
    private static final long TTL_HOURS = 168; // 7天
    private static final int MAX_LIST_SIZE = 100; // 单会话最大消息条数

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisChatMemoryStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 读取会话消息列表
     */
    public List<Map<String, String>> getMessages(String sessionId) {
        try {
            String json = redisTemplate.opsForValue().get(SESSION_KEY_PREFIX + sessionId);
            if (json != null && !json.isEmpty()) {
                return objectMapper.readValue(json, new TypeReference<List<Map<String, String>>>() {});
            }
        } catch (Exception e) {
            log.warn("Redis 读取消息失败: sessionId={}, error={}", sessionId, e.getMessage());
        }
        return null;
    }

    /**
     * 保存会话消息列表
     */
    public void saveMessages(String sessionId, List<Map<String, String>> messages) {
        try {
            String json = objectMapper.writeValueAsString(messages);
            redisTemplate.opsForValue().set(SESSION_KEY_PREFIX + sessionId, json, TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Redis 保存消息失败: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    /**
     * 原子追加一条消息到会话（使用 Redis RPUSH，线程安全）
     */
    public void appendMessage(String sessionId, String role, String content) {
        try {
            Map<String, String> msg = new HashMap<>();
            msg.put("role", role);
            msg.put("content", content);
            String json = objectMapper.writeValueAsString(msg);
            String listKey = LIST_KEY_PREFIX + sessionId;

            // RPUSH 是原子操作，不会丢消息
            redisTemplate.opsForList().rightPush(listKey, json);
            // 保持列表有界
            redisTemplate.opsForList().trim(listKey, -MAX_LIST_SIZE, -1);
            redisTemplate.expire(listKey, TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Redis 原子追加消息失败: sessionId={}, error={}", sessionId, e.getMessage());
            // 回退到旧方式
            appendMessageFallback(sessionId, role, content);
        }
    }

    /**
     * 回退追加方式（兼容）
     */
    private void appendMessageFallback(String sessionId, String role, String content) {
        List<Map<String, String>> messages = getMessages(sessionId);
        if (messages == null) {
            messages = new ArrayList<>();
        }
        Map<String, String> msg = new HashMap<>();
        msg.put("role", role);
        msg.put("content", content);
        messages.add(msg);
        saveMessages(sessionId, messages);
    }

    /**
     * 读取对话摘要
     */
    public String getSummary(String sessionId) {
        try {
            return redisTemplate.opsForValue().get(SUMMARY_KEY_PREFIX + sessionId);
        } catch (Exception e) {
            log.warn("Redis 读取摘要失败: sessionId={}", sessionId);
            return null;
        }
    }

    /**
     * 保存对话摘要
     */
    public void saveSummary(String sessionId, String summary) {
        try {
            redisTemplate.opsForValue().set(SUMMARY_KEY_PREFIX + sessionId, summary, TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Redis 保存摘要失败: sessionId={}", sessionId);
        }
    }

    /**
     * 替换 Redis List 中的全部消息（用于 MySQL 回写 / 压缩后重建）
     * 使用 Pipeline 保证 DELETE + RPUSH + EXPIRE 的原子性
     */
    @SuppressWarnings("unchecked")
    public void replaceList(String sessionId, List<Map<String, String>> messages) {
        try {
            String listKey = LIST_KEY_PREFIX + sessionId;
            List<String> jsonItems = new ArrayList<>();
            for (Map<String, String> msg : messages) {
                jsonItems.add(objectMapper.writeValueAsString(msg));
            }
            // 使用 MULTI/EXEC 事务保证 DELETE + RPUSH + EXPIRE 原子性
            redisTemplate.execute(new org.springframework.data.redis.core.SessionCallback<Object>() {
                @Override
                public Object execute(org.springframework.data.redis.core.RedisOperations operations) {
                    operations.multi();
                    operations.delete(listKey);
                    for (String json : jsonItems) {
                        operations.opsForList().rightPush(listKey, json);
                    }
                    operations.expire(listKey, TTL_HOURS, TimeUnit.HOURS);
                    return operations.exec();
                }
            });
        } catch (Exception e) {
            log.warn("Redis List 替换失败: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    /**
     * 从 Redis List 读取消息（优先使用原子列表）
     */
    public List<Map<String, String>> getMessagesFromList(String sessionId) {
        try {
            String listKey = LIST_KEY_PREFIX + sessionId;
            List<String> items = redisTemplate.opsForList().range(listKey, 0, -1);
            if (items != null && !items.isEmpty()) {
                return items.stream()
                        .map(item -> {
                            try {
                                return objectMapper.readValue(item, new TypeReference<Map<String, String>>() {});
                            } catch (Exception e) {
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("Redis List 读取失败: sessionId={}", sessionId);
        }
        return null;
    }

    /**
     * 获取当前消息数量
     */
    public int getMessageCount(String sessionId) {
        // 优先查 List
        try {
            String listKey = LIST_KEY_PREFIX + sessionId;
            Long size = redisTemplate.opsForList().size(listKey);
            if (size != null && size > 0) {
                return size.intValue();
            }
        } catch (Exception ignored) {}
        List<Map<String, String>> messages = getMessages(sessionId);
        return messages != null ? messages.size() : 0;
    }
}

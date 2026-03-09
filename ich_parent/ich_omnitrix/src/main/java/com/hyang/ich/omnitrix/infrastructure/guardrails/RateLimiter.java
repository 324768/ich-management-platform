package com.hyang.ich.omnitrix.infrastructure.guardrails;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * 基于 Redis 滑动窗口的请求限流器。
 * 每用户每分钟最多 MAX_REQUESTS_PER_MINUTE 次 AI 对话请求。
 */
@Slf4j
@Component
public class RateLimiter {

    private static final String RATE_KEY_PREFIX = "ai:rate:";
    private static final int MAX_REQUESTS_PER_MINUTE = 15;
    private static final int WINDOW_SECONDS = 60;

    /** Lua 脚本：原子 INCR + 首次 EXPIRE，避免 increment 与 expire 之间崩溃导致 key 永不过期 */
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT;

    static {
        RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();
        RATE_LIMIT_SCRIPT.setScriptText(
                "local cnt = redis.call('INCR', KEYS[1])\n" +
                "if cnt == 1 then\n" +
                "  redis.call('EXPIRE', KEYS[1], ARGV[1])\n" +
                "end\n" +
                "return cnt"
        );
        RATE_LIMIT_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;

    public RateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 检查用户是否超过速率限制。
     * @return true = 允许通过, false = 被限流
     */
    public boolean tryAcquire(Long userId) {
        try {
            String key = RATE_KEY_PREFIX + userId;
            Long count = redisTemplate.execute(RATE_LIMIT_SCRIPT,
                    Collections.singletonList(key), String.valueOf(WINDOW_SECONDS));
            if (count != null && count > MAX_REQUESTS_PER_MINUTE) {
                log.warn("用户 {} 触发限流: {}/{} 次/分钟", userId, count, MAX_REQUESTS_PER_MINUTE);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("限流检查异常(放行): userId={}, error={}", userId, e.getMessage());
            // Redis 异常时放行，不阻塞业务
            return true;
        }
    }

    /**
     * 获取用户当前窗口内剩余可用次数
     */
    public int remaining(Long userId) {
        try {
            String key = RATE_KEY_PREFIX + userId;
            String val = redisTemplate.opsForValue().get(key);
            if (val == null) return MAX_REQUESTS_PER_MINUTE;
            int used = Integer.parseInt(val);
            return Math.max(0, MAX_REQUESTS_PER_MINUTE - used);
        } catch (Exception e) {
            return MAX_REQUESTS_PER_MINUTE;
        }
    }
}

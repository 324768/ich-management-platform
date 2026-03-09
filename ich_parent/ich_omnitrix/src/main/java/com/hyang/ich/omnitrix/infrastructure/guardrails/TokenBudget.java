package com.hyang.ich.omnitrix.infrastructure.guardrails;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

/**
 * Token 用量预算控制：每用户每日 Token 使用量上限。
 *
 * 2025 趋势：AI 应用需要成本可控性，防止单用户大量消耗 Token 导致成本失控。
 * 使用 Redis 按日期 key 统计，自动过期。
 */
@Slf4j
@Component
public class TokenBudget {

    private static final String BUDGET_KEY_PREFIX = "ai:token_budget:";
    /** 每用户每日 Token 上限（输入+输出合计） */
    private static final int DAILY_TOKEN_LIMIT = 50_000;
    private static final int KEY_EXPIRE_SECONDS = 86_400 + 3600; // 25 小时，确保跨日安全

    /** Lua 脚本：原子 INCRBY + 首次 EXPIRE */
    private static final DefaultRedisScript<Long> INCR_SCRIPT;

    static {
        INCR_SCRIPT = new DefaultRedisScript<>();
        INCR_SCRIPT.setScriptText(
                "local cnt = redis.call('INCRBY', KEYS[1], ARGV[1])\n" +
                "if cnt == tonumber(ARGV[1]) then\n" +
                "  redis.call('EXPIRE', KEYS[1], ARGV[2])\n" +
                "end\n" +
                "return cnt"
        );
        INCR_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;

    public TokenBudget(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 检查用户今日是否还有 Token 预算
     * @return true = 有预算, false = 已超限
     */
    public boolean hasRemaining(Long userId) {
        try {
            String key = buildKey(userId);
            String val = redisTemplate.opsForValue().get(key);
            if (val == null) return true;
            long used = Long.parseLong(val);
            return used < DAILY_TOKEN_LIMIT;
        } catch (Exception e) {
            log.debug("Token 预算检查异常(放行): userId={}, error={}", userId, e.getMessage());
            return true;
        }
    }

    /**
     * 消费 Token（请求完成后调用）
     * @param tokens 本次请求消耗的 Token 数（输入+输出）
     * @return 今日已用总量
     */
    public long consume(Long userId, int tokens) {
        if (tokens <= 0) return 0;
        try {
            String key = buildKey(userId);
            Long total = redisTemplate.execute(INCR_SCRIPT,
                    Collections.singletonList(key),
                    String.valueOf(tokens), String.valueOf(KEY_EXPIRE_SECONDS));
            if (total != null && total >= DAILY_TOKEN_LIMIT) {
                log.warn("用户 {} 今日 Token 预算耗尽: {}/{}", userId, total, DAILY_TOKEN_LIMIT);
            }
            return total != null ? total : 0;
        } catch (Exception e) {
            log.debug("Token 预算消费记录失败: userId={}, error={}", userId, e.getMessage());
            return 0;
        }
    }

    /**
     * 获取用户今日剩余 Token 数
     */
    public int remaining(Long userId) {
        try {
            String key = buildKey(userId);
            String val = redisTemplate.opsForValue().get(key);
            if (val == null) return DAILY_TOKEN_LIMIT;
            long used = Long.parseLong(val);
            return Math.max(0, (int) (DAILY_TOKEN_LIMIT - used));
        } catch (Exception e) {
            return DAILY_TOKEN_LIMIT;
        }
    }

    private String buildKey(Long userId) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return BUDGET_KEY_PREFIX + userId + ":" + date;
    }
}

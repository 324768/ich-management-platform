package com.hyang.ich.omnitrix.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

/**
 * Ultra AI 密码验证服务。
 * 密码存储为 SHA-256 哈希值，支持 Redis 锁定（连续错误 5 次锁定 30 分钟）。
 */
@Slf4j
@Service
public class UltraAuthService {

    private static final String LOCK_KEY_PREFIX = "omnitrix:ultra_lock:";
    private static final String FAIL_COUNT_PREFIX = "omnitrix:ultra_fail:";
    private static final String TOKEN_PREFIX = "omnitrix:ultra_token:";
    private static final int MAX_FAIL_COUNT = 5;
    private static final int LOCK_MINUTES = 30;
    private static final int TOKEN_TTL_HOURS = 2;

    @Value("${omnitrix.ultra.password-hash:a9f51566bd6705f7ea6ad54bb9deb449f795582d6529a0e22207b8981233ec58}")
    private String passwordHash;

    private final StringRedisTemplate redisTemplate;

    public UltraAuthService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 验证 Ultra 密码
     * @param password 明文密码
     * @param adminId 管理员ID（用于锁定跟踪）
     * @return 验证成功返回 token，失败返回 null
     */
    public String verify(String password, Long adminId) {
        String lockKey = LOCK_KEY_PREFIX + adminId;
        String failKey = FAIL_COUNT_PREFIX + adminId;

        // 检查是否被锁定
        try {
            String locked = redisTemplate.opsForValue().get(lockKey);
            if (locked != null) {
                log.warn("Ultra 认证被锁定: adminId={}", adminId);
                return null;
            }
        } catch (Exception e) {
            log.debug("检查锁定状态失败: {}", e.getMessage());
        }

        // 验证密码
        String inputHash = sha256(password);
        if (!passwordHash.equalsIgnoreCase(inputHash)) {
            // 记录失败次数
            try {
                Long failCount = redisTemplate.opsForValue().increment(failKey);
                redisTemplate.expire(failKey, LOCK_MINUTES, TimeUnit.MINUTES);
                if (failCount != null && failCount >= MAX_FAIL_COUNT) {
                    redisTemplate.opsForValue().set(lockKey, "1", LOCK_MINUTES, TimeUnit.MINUTES);
                    redisTemplate.delete(failKey);
                    log.warn("Ultra 认证连续失败{}次，已锁定{}分钟: adminId={}", MAX_FAIL_COUNT, LOCK_MINUTES, adminId);
                }
            } catch (Exception e) {
                log.debug("记录失败次数异常: {}", e.getMessage());
            }
            return null;
        }

        // 验证成功，清除失败计数，生成 token
        try {
            redisTemplate.delete(failKey);
        } catch (Exception ignored) {}

        String token = generateToken(adminId);
        log.info("Ultra 认证成功: adminId={}", adminId);
        return token;
    }

    /**
     * 验证 Ultra token 是否有效
     */
    public boolean validateToken(String token) {
        if (token == null || token.isEmpty()) return false;
        try {
            String val = redisTemplate.opsForValue().get(TOKEN_PREFIX + token);
            return val != null;
        } catch (Exception e) {
            log.debug("验证Ultra token失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 从 token 中获取管理员ID
     */
    public Long getAdminIdFromToken(String token) {
        if (token == null) return null;
        try {
            String val = redisTemplate.opsForValue().get(TOKEN_PREFIX + token);
            return val != null ? Long.parseLong(val) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 撤销 Ultra token
     */
    public void revokeToken(String token) {
        try {
            redisTemplate.delete(TOKEN_PREFIX + token);
        } catch (Exception ignored) {}
    }

    /**
     * 检查管理员是否被锁定
     */
    public boolean isLocked(Long adminId) {
        try {
            return redisTemplate.opsForValue().get(LOCK_KEY_PREFIX + adminId) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private String generateToken(Long adminId) {
        String raw = adminId + ":" + System.currentTimeMillis() + ":" + Math.random();
        String token = sha256(raw);
        try {
            redisTemplate.opsForValue().set(TOKEN_PREFIX + token, String.valueOf(adminId),
                    TOKEN_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("保存Ultra token失败: {}", e.getMessage());
        }
        return token;
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 不可用", e);
        }
    }
}

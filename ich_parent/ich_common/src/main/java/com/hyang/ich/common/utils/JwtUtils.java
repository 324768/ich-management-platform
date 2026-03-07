package com.hyang.ich.common.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class JwtUtils {

    /** 默认密钥（至少32字节） */
    private static final String DEFAULT_SECRET = "IchManagementPlatformSecretKey2025!@#";

    /** 默认过期时间: 24小时 */
    private static final long DEFAULT_EXPIRATION = 24 * 60 * 60 * 1000L;

    private static SecretKey getSigningKey(String secret) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成 JWT token
     */
    public static String generateToken(Long userId, String username) {
        return generateToken(userId, username, DEFAULT_SECRET, DEFAULT_EXPIRATION);
    }

    /**
     * 生成 JWT token（自定义密钥和过期时间）
     */
    public static String generateToken(Long userId, String username, String secret, long expiration) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(String.valueOf(userId))
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(secret), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析 token 获取 Claims
     */
    public static Claims parseToken(String token) {
        return parseToken(token, DEFAULT_SECRET);
    }

    public static Claims parseToken(String token, String secret) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey(secret))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 从 token 中获取用户ID
     */
    public static Long getUserId(String token) {
        Claims claims = parseToken(token);
        Object userId = claims.get("userId");
        if (userId instanceof Integer) {
            return ((Integer) userId).longValue();
        }
        return (Long) userId;
    }

    /**
     * 从 token 中获取用户名
     */
    public static String getUsername(String token) {
        Claims claims = parseToken(token);
        return (String) claims.get("username");
    }

    /**
     * 校验 token 是否有效（未过期）
     */
    public static boolean validateToken(String token) {
        return validateToken(token, DEFAULT_SECRET);
    }

    public static boolean validateToken(String token, String secret) {
        try {
            Claims claims = parseToken(token, secret);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}

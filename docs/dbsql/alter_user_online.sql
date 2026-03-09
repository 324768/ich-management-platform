-- 为 user 表添加在线状态字段，供 Ultra AI 查询用户在线情况
-- 执行方式: 在 MySQL 中手动执行此 SQL

ALTER TABLE user_center.user 
ADD COLUMN `is_online` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否在线 0-离线 1-在线' AFTER `last_login_ip`,
ADD COLUMN `last_active_time` datetime DEFAULT NULL COMMENT '最后活跃时间' AFTER `is_online`;

-- 为在线状态添加索引（Ultra 查在线用户列表时加速）
ALTER TABLE user_center.user ADD INDEX `idx_is_online` (`is_online`);

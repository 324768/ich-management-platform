-- ============================================================
-- Omnitrix Ultra AI 扩展表
-- 数据库: ai_service
-- ============================================================

-- 第四层记忆: 系统全局记忆
CREATE TABLE IF NOT EXISTS `ai_system_memory` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `memory_type` VARCHAR(20) NOT NULL COMMENT 'policy/pattern/decision/insight/directive',
    `memory_key` VARCHAR(100) NOT NULL COMMENT '记忆唯一键',
    `memory_value` TEXT NOT NULL COMMENT '记忆内容',
    `created_by` VARCHAR(50) DEFAULT 'system' COMMENT '创建者(admin/ultra_ai/system)',
    `confidence` DECIMAL(3,2) DEFAULT 0.80 COMMENT '置信度 0.50-1.00',
    `hit_count` INT DEFAULT 0 COMMENT '命中次数',
    `is_active` TINYINT DEFAULT 1 COMMENT '1=有效, 0=已失效',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_memory_key` (`memory_key`),
    KEY `idx_type` (`memory_type`),
    KEY `idx_active` (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统全局记忆(第四层)';

-- 用户 AI 控制表
CREATE TABLE IF NOT EXISTS `ai_user_ai_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '被控制的用户ID',
    `ai_enabled` TINYINT DEFAULT 1 COMMENT '1=启用, 0=禁用',
    `disabled_reason` VARCHAR(200) DEFAULT NULL COMMENT '禁用原因',
    `disabled_by` BIGINT DEFAULT NULL COMMENT '操作管理员ID',
    `max_daily_queries` INT DEFAULT 100 COMMENT '每日最大查询次数',
    `blocked_agents` VARCHAR(500) DEFAULT NULL COMMENT '禁用的Agent列表(逗号分隔)',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户AI配置(Ultra控制)';

-- Ultra 密码配置(插入 system_manage.sys_config，注意 sys_config 在 system_manage 库)
-- 默认密码: omnitrix_ultra_2025 的 SHA-256 哈希
INSERT IGNORE INTO `system_manage`.`sys_config` (`config_key`, `config_value`, `config_name`, `remark`, `create_time`)
VALUES ('ultra_ai_password', 'a9f51566bd6705f7ea6ad54bb9deb449f795582d6529a0e22207b8981233ec58',
        'Ultra AI 访问密码', 'SHA-256 哈希值，默认明文: omnitrix_ultra_2025', NOW());

-- Ultra 操作审计日志(复用 sys_operation_log，增加 ultra 前缀的 module)

-- V4: 用户长期记忆表（跨会话用户画像 + 事实记忆）
-- 对应 OpenClaw 的 MEMORY.md + USER.md 概念
CREATE TABLE IF NOT EXISTS `ai_user_memory` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`       BIGINT       NOT NULL              COMMENT '用户ID',
    `memory_type`   VARCHAR(30)  NOT NULL              COMMENT '记忆类型: preference/interest/fact/decision/lesson',
    `memory_key`    VARCHAR(100) NOT NULL              COMMENT '记忆键(如 interest_category, comm_style)',
    `memory_value`  VARCHAR(500) NOT NULL              COMMENT '记忆值(事实描述)',
    `confidence`    DECIMAL(3,2) DEFAULT 0.80          COMMENT '置信度(0.00-1.00)',
    `source`        VARCHAR(20)  DEFAULT 'conversation' COMMENT '来源: conversation/behavior/manual',
    `hit_count`     INT          DEFAULT 0             COMMENT '被引用次数(用于价值衰减)',
    `status`        TINYINT      DEFAULT 1             COMMENT '0=过期 1=有效',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_user_type` (`user_id`, `memory_type`),
    KEY `idx_status` (`status`),
    KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户长期记忆表(跨会话画像)';

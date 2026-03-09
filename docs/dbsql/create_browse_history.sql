-- ============================================================
-- 浏览历史记录表
-- 数据库: common_db
-- 说明: 记录用户浏览行为，不可删除，按时间分类
--       供 Ultra AI 通过用户端 AI 浏览历史助手查询
-- ============================================================

CREATE TABLE IF NOT EXISTS `common_db`.`browse_history` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `target_type` VARCHAR(30) NOT NULL COMMENT '浏览目标类型: ich_item/heritage_man/activity/product/knowledge',
    `target_id` BIGINT NOT NULL COMMENT '浏览目标ID',
    `target_title` VARCHAR(200) DEFAULT NULL COMMENT '浏览目标标题(冗余存储,避免跨库查询)',
    `browse_date` DATE NOT NULL COMMENT '浏览日期(用于按天分组)',
    `browse_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '浏览时间(精确时间)',
    `duration_seconds` INT DEFAULT 0 COMMENT '停留时长(秒)',
    `source` VARCHAR(30) DEFAULT 'web' COMMENT '来源: web/app/mini_program',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_date` (`user_id`, `browse_date`),
    KEY `idx_user_type` (`user_id`, `target_type`),
    KEY `idx_browse_date` (`browse_date`),
    KEY `idx_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户浏览历史(不可删除)';

-- 插入一些示例数据方便测试
INSERT INTO `common_db`.`browse_history` (`user_id`, `target_type`, `target_id`, `target_title`, `browse_date`, `browse_time`, `duration_seconds`, `source`) VALUES
(1, 'ich_item', 1, '皮影戏', CURDATE(), NOW(), 120, 'web'),
(1, 'product', 3, '皮影戏手工套装', CURDATE(), DATE_SUB(NOW(), INTERVAL 1 HOUR), 45, 'web'),
(1, 'heritage_man', 2, '李大师', CURDATE(), DATE_SUB(NOW(), INTERVAL 2 HOUR), 60, 'web'),
(1, 'ich_item', 5, '剪纸艺术', DATE_SUB(CURDATE(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 90, 'web'),
(1, 'product', 7, '剪纸DIY材料包', DATE_SUB(CURDATE(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 25 HOUR), 30, 'web'),
(2, 'ich_item', 1, '皮影戏', CURDATE(), DATE_SUB(NOW(), INTERVAL 30 MINUTE), 200, 'app'),
(2, 'knowledge', 10, '什么是非物质文化遗产', CURDATE(), DATE_SUB(NOW(), INTERVAL 3 HOUR), 150, 'app');

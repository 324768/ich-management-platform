-- 活动时间线、评论、浏览记录 相关DDL
USE content_center;

-- 1. 活动表新增时间线字段
ALTER TABLE ich_activity ADD COLUMN timeline_data LONGTEXT DEFAULT NULL COMMENT '时间线数据JSON' AFTER videos;

-- 2. 活动评论表
CREATE TABLE IF NOT EXISTS ich_activity_comment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '评论ID',
    activity_id BIGINT NOT NULL COMMENT '活动ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    user_name VARCHAR(50) DEFAULT NULL COMMENT '用户昵称',
    user_avatar VARCHAR(500) DEFAULT NULL COMMENT '用户头像',
    content TEXT NOT NULL COMMENT '评论内容',
    status TINYINT DEFAULT 0 COMMENT '状态：0-正常 1-隐藏',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT DEFAULT 0 COMMENT '是否删除：0-否 1-是',
    INDEX idx_activity_id (activity_id),
    INDEX idx_user_id (user_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动评论表';

-- 3. 活动浏览记录表
CREATE TABLE IF NOT EXISTS ich_activity_view_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    activity_id BIGINT NOT NULL COMMENT '活动ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    user_name VARCHAR(50) DEFAULT NULL COMMENT '用户昵称',
    view_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '浏览时间',
    INDEX idx_activity_id (activity_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动浏览记录表';

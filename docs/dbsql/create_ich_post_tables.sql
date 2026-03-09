-- 非遗动态/笔记模块 (小红书风格)
-- 包含帖子表和帖子评论表

CREATE TABLE IF NOT EXISTS content_center.ich_post (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '发布者ID',
    user_name VARCHAR(50) COMMENT '发布者昵称',
    user_avatar VARCHAR(500) COMMENT '发布者头像',
    title VARCHAR(200) NOT NULL COMMENT '标题',
    content TEXT COMMENT '正文内容',
    images LONGTEXT COMMENT '图片列表JSON数组',
    video_url LONGTEXT COMMENT '视频URL',
    cover_image LONGTEXT COMMENT '封面图(取第一张图或视频封面)',
    tags VARCHAR(500) COMMENT '标签,逗号分隔',
    type TINYINT DEFAULT 1 COMMENT '类型 1-图文 2-视频',
    like_count INT DEFAULT 0 COMMENT '点赞数',
    favorite_count INT DEFAULT 0 COMMENT '收藏数',
    comment_count INT DEFAULT 0 COMMENT '评论数',
    view_count INT DEFAULT 0 COMMENT '浏览数',
    status TINYINT DEFAULT 1 COMMENT '状态 0-隐藏 1-显示',
    is_deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='非遗动态/笔记';

CREATE TABLE IF NOT EXISTS content_center.ich_post_comment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL COMMENT '帖子ID',
    user_id BIGINT NOT NULL COMMENT '评论者ID',
    user_name VARCHAR(50) COMMENT '评论者昵称',
    user_avatar VARCHAR(500) COMMENT '评论者头像',
    content TEXT NOT NULL COMMENT '评论内容',
    images LONGTEXT COMMENT '评论图片JSON数组',
    like_count INT DEFAULT 0 COMMENT '点赞数',
    location VARCHAR(50) COMMENT '评论地区',
    status TINYINT DEFAULT 1 COMMENT '状态 0-隐藏 1-显示',
    is_deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_post_id (post_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='非遗动态评论';

-- 点赞记录表
CREATE TABLE IF NOT EXISTS content_center.ich_post_like (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL COMMENT '帖子ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_post_user (post_id, user_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子点赞记录';

-- 收藏记录表
CREATE TABLE IF NOT EXISTS content_center.ich_post_favorite (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL COMMENT '帖子ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_post_user (post_id, user_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子收藏记录';

-- 插入一些示例数据
INSERT INTO content_center.ich_post (user_id, user_name, title, content, tags, type, like_count, favorite_count, comment_count, view_count) VALUES
(1, '非遗传承人', '苏绣技艺 | 一针一线皆是故事', '苏绣是中国四大名绣之一，起源于苏州，已有两千多年的历史。每一针每一线都凝聚着绣娘们的心血和智慧。', '苏绣,刺绣,非遗技艺', 1, 456, 89, 23, 2730),
(1, '文化守护者', '景德镇陶瓷 | 千年窑火不灭', '景德镇陶瓷始于汉代，兴于唐宋，盛于明清。被誉为"瓷都"的景德镇，用泥与火书写了中华文明的灿烂篇章。', '陶瓷,景德镇,非遗', 1, 1856, 490, 63, 8900),
(1, '匠心工坊', '榫卯之美 | 中国古建筑的灵魂', '榫卯结构是中国传统木作的核心技艺，不用一钉一铆，仅凭木与木的咬合，便能构建起千年不倒的建筑奇迹。', '榫卯,古建筑,木工', 1, 243, 67, 15, 1200),
(1, '茶道传人', '武夷岩茶 | 岩骨花香的秘密', '武夷岩茶产于福建武夷山，以"岩骨花香"著称。大红袍、肉桂、水仙，每一款都蕴含着独特的山场韵味。', '岩茶,武夷山,茶道', 2, 680, 156, 42, 4500),
(1, '皮影世家', '皮影戏 | 一口述说千古事', '皮影戏是中国民间古老的传统艺术，始于西汉，兴于唐朝。一张幕布、几根竹棍，便能演绎出千古传奇。', '皮影戏,民间艺术,非遗', 1, 320, 78, 19, 1800);

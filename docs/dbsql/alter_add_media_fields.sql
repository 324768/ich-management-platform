-- ============================================================
-- 为所有内容表添加 detail_images 和 videos 字段
-- 执行前请确认连接到对应的数据库
-- ============================================================

-- 0. ich_category (content_center 库) — videos 列已存在则跳过
USE content_center;
-- ALTER TABLE ich_category ADD COLUMN videos ... (已执行，跳过，避免 1060 Duplicate column)

-- 1. ich_item (content_center 库)
ALTER TABLE ich_item ADD COLUMN detail_images TEXT DEFAULT NULL COMMENT '详情图片(JSON数组)' AFTER content;
ALTER TABLE ich_item ADD COLUMN videos TEXT DEFAULT NULL COMMENT '视频列表(JSON数组)' AFTER detail_images;

-- 2. ich_heritage_man (content_center 库)
ALTER TABLE ich_heritage_man ADD COLUMN detail_images TEXT DEFAULT NULL COMMENT '详情图片(JSON数组)' AFTER achievement;
ALTER TABLE ich_heritage_man ADD COLUMN videos TEXT DEFAULT NULL COMMENT '视频列表(JSON数组)' AFTER detail_images;

-- 3. product (product_center 库)
USE product_center;
ALTER TABLE product ADD COLUMN videos TEXT DEFAULT NULL COMMENT '视频列表(JSON数组)' AFTER sub_images;

-- 4. product_category (product_center 库)
ALTER TABLE product_category ADD COLUMN videos TEXT DEFAULT NULL COMMENT '视频列表(JSON数组)' AFTER detail_images;

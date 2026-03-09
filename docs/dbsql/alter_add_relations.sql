-- ============================================================
-- 为商品表添加传承人关联字段
-- ============================================================

USE product_center;

-- product 表添加 heritage_man_id（关联传承人）
ALTER TABLE product ADD COLUMN heritage_man_id BIGINT DEFAULT NULL COMMENT '关联传承人ID' AFTER category_id;
ALTER TABLE product ADD INDEX idx_heritage_man_id(heritage_man_id);

-- product_category 表添加 ich_category_id（关联非遗分类）
ALTER TABLE product_category ADD COLUMN ich_category_id BIGINT DEFAULT NULL COMMENT '关联非遗分类ID' AFTER parent_id;
ALTER TABLE product_category ADD INDEX idx_ich_category_id(ich_category_id);

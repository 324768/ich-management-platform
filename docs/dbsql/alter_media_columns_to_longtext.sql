-- 修复 "Data too long for column" 错误
-- 原因：videos / detail_images 等列为 TEXT（最大64KB），存不下 base64 编码的媒体数据
-- 解决：统一改为 LONGTEXT（最大4GB）

-- ========== content_center ==========

-- ich_item
ALTER TABLE content_center.ich_item MODIFY COLUMN videos LONGTEXT;
ALTER TABLE content_center.ich_item MODIFY COLUMN detail_images LONGTEXT;

-- ich_activity
ALTER TABLE content_center.ich_activity MODIFY COLUMN videos LONGTEXT;
ALTER TABLE content_center.ich_activity MODIFY COLUMN detail_images LONGTEXT;
ALTER TABLE content_center.ich_activity MODIFY COLUMN cover_image LONGTEXT;

-- ich_heritage_man
ALTER TABLE content_center.ich_heritage_man MODIFY COLUMN videos LONGTEXT;
ALTER TABLE content_center.ich_heritage_man MODIFY COLUMN detail_images LONGTEXT;

-- ich_category
ALTER TABLE content_center.ich_category MODIFY COLUMN videos LONGTEXT;

-- article
ALTER TABLE content_center.article MODIFY COLUMN cover_image LONGTEXT;

-- ========== product_center ==========

-- product
ALTER TABLE product_center.product MODIFY COLUMN videos LONGTEXT;
ALTER TABLE product_center.product MODIFY COLUMN detail_desc LONGTEXT;

-- product_category
ALTER TABLE product_center.product_category MODIFY COLUMN videos LONGTEXT;

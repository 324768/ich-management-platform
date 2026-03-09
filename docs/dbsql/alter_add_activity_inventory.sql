/*
 新增模块：非遗活动管理 + 库存记录/预警
 - ich_activity           → content_center 非遗活动表
 - ich_activity_record    → content_center 活动报名记录表
 - inventory_record       → product_center 库存变动记录表
 - ALTER product          → product_center 增加库存预警阈值字段
*/

-- ============================================================
-- 1. 非遗活动表 (content_center)
-- ============================================================
USE `content_center`;

DROP TABLE IF EXISTS `ich_activity`;
CREATE TABLE `ich_activity` (
  `id`                   bigint       NOT NULL AUTO_INCREMENT COMMENT '活动ID',
  `name`                 varchar(100) NOT NULL COMMENT '活动名称',
  `activity_type`        tinyint      NOT NULL DEFAULT 1 COMMENT '活动类型：1-展演 2-研习 3-讲座 4-展览 5-其他',
  `cover_image`          varchar(255) NULL DEFAULT NULL COMMENT '封面图片',
  `description`          text         NULL COMMENT '活动简介',
  `content`              longtext     NULL COMMENT '活动详情（富文本）',
  `location`             varchar(255) NULL DEFAULT NULL COMMENT '活动地点',
  `start_time`           datetime     NULL DEFAULT NULL COMMENT '开始时间',
  `end_time`             datetime     NULL DEFAULT NULL COMMENT '结束时间',
  `max_participants`     int          NULL DEFAULT 0 COMMENT '最大参与人数，0表示不限',
  `current_participants` int          NULL DEFAULT 0 COMMENT '当前报名人数',
  `registration_deadline` datetime    NULL DEFAULT NULL COMMENT '报名截止时间',
  `contact_person`       varchar(50)  NULL DEFAULT NULL COMMENT '联系人',
  `contact_phone`        varchar(20)  NULL DEFAULT NULL COMMENT '联系电话',
  `item_id`              bigint       NULL DEFAULT NULL COMMENT '关联非遗项目ID',
  `status`               tinyint      NULL DEFAULT 0 COMMENT '状态：0-草稿 1-报名中 2-进行中 3-已结束 4-已取消',
  `sort`                 int          NULL DEFAULT 0 COMMENT '排序',
  `create_time`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`           tinyint(1)   NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
  `version`              int          NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_activity_type` (`activity_type`) USING BTREE,
  INDEX `idx_status` (`status`) USING BTREE,
  INDEX `idx_start_time` (`start_time`) USING BTREE,
  INDEX `idx_item_id` (`item_id`) USING BTREE,
  INDEX `idx_create_time` (`create_time`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '非遗活动表' ROW_FORMAT = Dynamic;


-- ============================================================
-- 2. 活动报名记录表 (content_center)
-- ============================================================
DROP TABLE IF EXISTS `ich_activity_record`;
CREATE TABLE `ich_activity_record` (
  `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `activity_id`       bigint       NOT NULL COMMENT '活动ID',
  `user_id`           bigint       NULL DEFAULT NULL COMMENT '用户ID',
  `user_name`         varchar(50)  NULL DEFAULT NULL COMMENT '报名姓名',
  `user_phone`        varchar(20)  NULL DEFAULT NULL COMMENT '联系电话',
  `registration_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报名时间',
  `check_in_time`     datetime     NULL DEFAULT NULL COMMENT '签到时间',
  `status`            tinyint      NULL DEFAULT 0 COMMENT '状态：0-待审核 1-已确认 2-已签到 3-已取消',
  `remark`            varchar(500) NULL DEFAULT NULL COMMENT '备注',
  `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`        tinyint(1)   NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
  `version`           int          NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_activity_id` (`activity_id`) USING BTREE,
  INDEX `idx_user_id` (`user_id`) USING BTREE,
  INDEX `idx_status` (`status`) USING BTREE,
  INDEX `idx_registration_time` (`registration_time`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '活动报名记录表' ROW_FORMAT = Dynamic;


-- ============================================================
-- 3. 库存变动记录表 (product_center)
-- ============================================================
USE `product_center`;

DROP TABLE IF EXISTS `inventory_record`;
CREATE TABLE `inventory_record` (
  `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `product_id`    bigint       NOT NULL COMMENT '商品ID',
  `product_name`  varchar(200) NULL DEFAULT NULL COMMENT '商品名称（冗余）',
  `type`          tinyint      NOT NULL COMMENT '变动类型：1-入库 2-出库 3-调整',
  `quantity`      int          NOT NULL COMMENT '变动数量（正数入库，负数出库）',
  `before_stock`  int          NOT NULL COMMENT '变动前库存',
  `after_stock`   int          NOT NULL COMMENT '变动后库存',
  `reason`        varchar(500) NULL DEFAULT NULL COMMENT '变动原因',
  `operator_id`   bigint       NULL DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(50)  NULL DEFAULT NULL COMMENT '操作人姓名',
  `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`    tinyint(1)   NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
  `version`       int          NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_product_id` (`product_id`) USING BTREE,
  INDEX `idx_type` (`type`) USING BTREE,
  INDEX `idx_create_time` (`create_time`) USING BTREE,
  INDEX `idx_operator_id` (`operator_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '库存变动记录表' ROW_FORMAT = Dynamic;


-- ============================================================
-- 4. product 表增加库存预警阈值字段
-- ============================================================
ALTER TABLE `product`
  ADD COLUMN `stock_alert_threshold` int NULL DEFAULT 10 COMMENT '库存预警阈值' AFTER `stock`;

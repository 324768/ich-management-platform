# 非遗文化管理平台数据库设计文档

## 目录
1. [数据库设计规范](#一数据库设计规范)
2. [数据库分库设计](#二数据库分库设计)
3. [表结构设计](#三表结构设计)
4. [索引设计](#四索引设计)
5. [初始化数据](#五初始化数据)
6. [数据库维护](#六数据库维护)

## 一、数据库设计规范

### 1.1 命名规范
- 表名：小写下划线命名法，如 `user_info`
- 字段名：小写下划线命名法，如 `user_name`
- 主键：统一使用 `id` 作为主键名
- 外键：`关联表名_id`，如 `user_id`
- 索引：`idx_字段名`，如 `idx_user_name`
- 唯一索引：`uk_字段名`，如 `uk_user_name`

### 1.2 字段类型规范
- 整型：`tinyint`、`smallint`、`int`、`bigint`
- 字符串：`varchar(n)`，n 按需设置
- 大文本：`text`、`longtext`
- 金额：`decimal(10,2)`
- 时间：`datetime`、`timestamp`
- 布尔：`tinyint(1)`，0-否，1-是

### 1.3 通用字段
所有表都包含以下通用字段：
- `id`：主键，自增
- `create_time`：创建时间
- `update_time`：更新时间
- `is_deleted`：逻辑删除标志
- `version`：乐观锁版本号

## 二、数据库分库设计

### 2.1 用户中心库 (user_center)
```sql
CREATE DATABASE IF NOT EXISTS `user_center` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `user_center`;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(100) NOT NULL COMMENT '密码（加密存储）',
  `nickname` varchar(50) DEFAULT NULL COMMENT '昵称',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像URL（七牛云存储）',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `last_login_ip` varchar(50) DEFAULT NULL COMMENT '最后登录IP',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  `version` int DEFAULT '0' COMMENT '版本号（乐观锁）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_phone` (`phone`),
  KEY `idx_email` (`email`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

-- 用户收货地址表
CREATE TABLE IF NOT EXISTS `user_address` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '地址ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `receiver_name` varchar(50) NOT NULL COMMENT '收货人姓名',
  `receiver_phone` varchar(20) NOT NULL COMMENT '收货人电话',
  `province` varchar(50) NOT NULL COMMENT '省',
  `city` varchar(50) NOT NULL COMMENT '市',
  `district` varchar(50) NOT NULL COMMENT '区/县',
  `detail_address` varchar(255) NOT NULL COMMENT '详细地址',
  `is_default` tinyint(1) DEFAULT '0' COMMENT '是否默认：0-否，1-是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  `version` int DEFAULT '0' COMMENT '版本号（乐观锁）',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户收货地址表';
```

### 2.2 订单中心库 (order_center)
```sql
CREATE DATABASE IF NOT EXISTS `order_center` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `order_center`;

-- 订单表
CREATE TABLE IF NOT EXISTS `order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `order_no` varchar(32) NOT NULL COMMENT '订单编号',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `total_amount` decimal(10,2) NOT NULL COMMENT '订单总金额',
  `pay_amount` decimal(10,2) NOT NULL COMMENT '实付金额',
  `freight_amount` decimal(10,2) DEFAULT '0.00' COMMENT '运费金额',
  `discount_amount` decimal(10,2) DEFAULT '0.00' COMMENT '优惠金额',
  `pay_type` tinyint DEFAULT NULL COMMENT '支付方式：0-未支付，1-支付宝，2-微信',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '订单状态：0-待付款，1-待发货，2-待收货，3-已完成，4-已取消，5-已关闭',
  `payment_time` datetime DEFAULT NULL COMMENT '支付时间',
  `consign_time` datetime DEFAULT NULL COMMENT '发货时间',
  `end_time` datetime DEFAULT NULL COMMENT '交易完成时间',
  `close_time` datetime DEFAULT NULL COMMENT '交易关闭时间',
  `shipping_name` varchar(64) DEFAULT NULL COMMENT '物流公司',
  `shipping_code` varchar(64) DEFAULT NULL COMMENT '物流单号',
  `receiver_name` varchar(100) NOT NULL COMMENT '收货人姓名',
  `receiver_phone` varchar(32) NOT NULL COMMENT '收货人电话',
  `receiver_post_code` varchar(32) DEFAULT NULL COMMENT '收货人邮编',
  `receiver_province` varchar(100) DEFAULT NULL COMMENT '省份/直辖市',
  `receiver_city` varchar(100) DEFAULT NULL COMMENT '城市',
  `receiver_region` varchar(100) DEFAULT NULL COMMENT '区',
  `receiver_detail_address` varchar(255) DEFAULT NULL COMMENT '详细地址',
  `note` varchar(500) DEFAULT NULL COMMENT '订单备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  `version` int DEFAULT '0' COMMENT '版本号（乐观锁）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单表';

-- 订单商品表
CREATE TABLE IF NOT EXISTS `order_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '订单商品ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(32) NOT NULL COMMENT '订单编号',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `product_pic` varchar(500) DEFAULT NULL COMMENT '商品图片',
  `product_name` varchar(200) NOT NULL COMMENT '商品名称',
  `product_brand` varchar(200) DEFAULT NULL COMMENT '商品品牌',
  `product_sn` varchar(64) DEFAULT NULL COMMENT '商品条码',
  `product_price` decimal(10,2) NOT NULL COMMENT '销售价格',
  `product_quantity` int NOT NULL COMMENT '购买数量',
  `product_sku_id` bigint DEFAULT NULL COMMENT '商品sku编号',
  `product_sku_code` varchar(50) DEFAULT NULL COMMENT '商品sku条码',
  `product_category_id` bigint DEFAULT NULL COMMENT '商品分类ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  `version` int DEFAULT '0' COMMENT '版本号（乐观锁）',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单商品表';
```

### 2.3 商品中心库 (product_center)
```sql
CREATE DATABASE IF NOT EXISTS `product_center` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `product_center`;

-- 商品表
CREATE TABLE IF NOT EXISTS `product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '商品ID',
  `product_sn` varchar(64) NOT NULL COMMENT '商品编码',
  `name` varchar(200) NOT NULL COMMENT '商品名称',
  `sub_title` varchar(500) DEFAULT NULL COMMENT '副标题',
  `brand_id` bigint DEFAULT NULL COMMENT '品牌ID',
  `category_id` bigint NOT NULL COMMENT '分类ID',
  `price` decimal(10,2) NOT NULL COMMENT '销售价格',
  `original_price` decimal(10,2) DEFAULT NULL COMMENT '市场价',
  `stock` int NOT NULL DEFAULT '0' COMMENT '库存',
  `sale` int DEFAULT '0' COMMENT '销量',
  `weight` decimal(10,2) DEFAULT NULL COMMENT '商品重量，默认为克',
  `keywords` varchar(255) DEFAULT NULL COMMENT '关键字',
  `description` text COMMENT '商品描述',
  `detail_desc` text COMMENT '详情描述',
  `main_image` varchar(255) DEFAULT NULL COMMENT '主图（七牛云存储）',
  `sub_images` text COMMENT '子图（七牛云存储，多个图片用,分割）',
  `status` tinyint DEFAULT '1' COMMENT '商品状态：0-下架，1-上架',
  `sort` int DEFAULT '0' COMMENT '排序',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  `version` int DEFAULT '0' COMMENT '版本号（乐观锁）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_sn` (`product_sn`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_brand_id` (`brand_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品表';

-- 商品分类表
CREATE TABLE IF NOT EXISTS `product_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `parent_id` bigint DEFAULT '0' COMMENT '上级分类ID，0表示一级分类',
  `name` varchar(64) NOT NULL COMMENT '分类名称',
  `level` int DEFAULT '0' COMMENT '分类级别：0->1级；1->2级',
  `icon` varchar(255) DEFAULT NULL COMMENT '图标（七牛云存储）',
  `description` text COMMENT '描述',
  `sort` int DEFAULT '0' COMMENT '排序',
  `status` tinyint DEFAULT '1' COMMENT '是否显示：0->不显示；1->显示',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  `version` int DEFAULT '0' COMMENT '版本号（乐观锁）',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_sort` (`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品分类表';
```

### 2.4 内容中心库 (content_center)
```sql
CREATE DATABASE IF NOT EXISTS `content_center` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `content_center`;

-- 非遗项目表
CREATE TABLE IF NOT EXISTS `ich_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '项目ID',
  `category_id` bigint NOT NULL COMMENT '分类ID',
  `name` varchar(100) NOT NULL COMMENT '项目名称',
  `cover_image` varchar(255) DEFAULT NULL COMMENT '封面图（七牛云存储）',
  `level` tinyint DEFAULT NULL COMMENT '级别：1-国家级，2-省级，3-市级',
  `region_code` varchar(20) DEFAULT NULL COMMENT '地区编码',
  `region_name` varchar(100) DEFAULT NULL COMMENT '地区名称',
  `declaration_unit` varchar(200) DEFAULT NULL COMMENT '申报单位',
  `protection_unit` varchar(200) DEFAULT NULL COMMENT '保护单位',
  `declaration_time` date DEFAULT NULL COMMENT '申报时间',
  `description` text COMMENT '项目简介',
  `content` longtext COMMENT '项目详情',
  `status` tinyint DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `sort` int DEFAULT '0' COMMENT '排序',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  `version` int DEFAULT '0' COMMENT '版本号（乐观锁）',
  PRIMARY KEY (`id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_region_code` (`region_code`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='非遗项目表';

-- 非遗传承人表
CREATE TABLE IF NOT EXISTS `ich_heritage_man` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '传承人ID',
  `name` varchar(50) NOT NULL COMMENT '姓名',
  `gender` tinyint DEFAULT '0' COMMENT '性别：0-未知，1-男，2-女',
  `birth_date` date DEFAULT NULL COMMENT '出生日期',
  `id_card` varchar(18) DEFAULT NULL COMMENT '身份证号',
  `phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `email` varchar(100) DEFAULT NULL COMMENT '电子邮箱',
  `address` varchar(255) DEFAULT NULL COMMENT '联系地址',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像（七牛云存储）',
  `level` tinyint DEFAULT NULL COMMENT '级别：1-国家级，2-省级，3-市级',
  `category_id` bigint DEFAULT NULL COMMENT '所属分类ID',
  `item_id` bigint DEFAULT NULL COMMENT '所属项目ID',
  `title` varchar(100) DEFAULT NULL COMMENT '称号',
  `skill` varchar(255) DEFAULT NULL COMMENT '技艺特长',
  `introduction` text COMMENT '个人简介',
  `achievement` text COMMENT '主要成就',
  `status` tinyint DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  `version` int DEFAULT '0' COMMENT '版本号（乐观锁）',
  PRIMARY KEY (`id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_item_id` (`item_id`),
  KEY `idx_name` (`name`),
  KEY `idx_id_card` (`id_card`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='非遗传承人表';
```

### 2.5 系统管理库 (system_manage)
```sql
CREATE DATABASE IF NOT EXISTS `system_manage` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `system_manage`;

-- 管理员表
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(100) NOT NULL COMMENT '密码',
  `nickname` varchar(50) DEFAULT NULL COMMENT '昵称',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像（七牛云存储）',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `gender` tinyint DEFAULT '0' COMMENT '性别：0-未知，1-男，2-女',
  `dept_id` bigint DEFAULT NULL COMMENT '部门ID',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `last_login_ip` varchar(50) DEFAULT NULL COMMENT '最后登录IP',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  `version` int DEFAULT '0' COMMENT '版本号（乐观锁）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_dept_id` (`dept_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理员表';

-- 角色表
CREATE TABLE IF NOT EXISTS `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `name` varchar(50) NOT NULL COMMENT '角色名称',
  `code` varchar(50) NOT NULL COMMENT '角色编码',
  `description` varchar(255) DEFAULT NULL COMMENT '描述',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `sort` int DEFAULT '0' COMMENT '排序',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  `version` int DEFAULT '0' COMMENT '版本号（乐观锁）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';
```

### 2.6 公共库 (common_db)
```sql
CREATE DATABASE IF NOT EXISTS `common_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `common_db`;

-- 文件上传记录表
CREATE TABLE IF NOT EXISTS `sys_file_upload` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '文件ID',
  `file_name` varchar(255) NOT NULL COMMENT '文件名',
  `file_key` varchar(255) NOT NULL COMMENT '文件key（七牛云存储）',
  `file_url` varchar(500) NOT NULL COMMENT '文件URL',
  `file_size` bigint NOT NULL COMMENT '文件大小（字节）',
  `file_type` varchar(100) DEFAULT NULL COMMENT '文件类型',
  `storage_type` varchar(20) DEFAULT 'QINIU' COMMENT '存储类型：LOCAL-本地，QINIU-七牛云，ALIYUN-阿里云',
  `biz_type` varchar(50) DEFAULT NULL COMMENT '业务类型',
  `biz_id` varchar(64) DEFAULT NULL COMMENT '业务ID',
  `upload_user_id` bigint DEFAULT NULL COMMENT '上传用户ID',
  `upload_ip` varchar(50) DEFAULT NULL COMMENT '上传IP',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  `version` int DEFAULT '0' COMMENT '版本号（乐观锁）',
  PRIMARY KEY (`id`),
  KEY `idx_upload_user_id` (`upload_user_id`),
  KEY `idx_file_key` (`file_key`),
  KEY `idx_biz` (`biz_type`,`biz_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件上传记录表';

-- 系统配置表
CREATE TABLE IF NOT EXISTS `sys_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `config_key` varchar(100) NOT NULL COMMENT '配置键',
  `config_value` text COMMENT '配置值',
  `config_name` varchar(100) DEFAULT NULL COMMENT '配置名称',
  `description` varchar(500) DEFAULT NULL COMMENT '描述',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  `version` int DEFAULT '0' COMMENT '版本号（乐观锁）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统配置表';
```

### 2.7 AI服务库 (ai_service)
```sql
CREATE DATABASE IF NOT EXISTS `ai_service` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `ai_service`;

-- AI服务调用记录表
CREATE TABLE IF NOT EXISTS `ai_service_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `user_id` bigint DEFAULT NULL COMMENT '用户ID',
  `service_type` varchar(50) NOT NULL COMMENT '服务类型',
  `request_data` text COMMENT '请求数据',
  `response_data` longtext COMMENT '响应数据',
  `status` tinyint DEFAULT '1' COMMENT '状态：0-失败，1-成功',
  `cost_time` int DEFAULT NULL COMMENT '耗时(毫秒)',
  `error_msg` text COMMENT '错误信息',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  `version` int DEFAULT '0' COMMENT '版本号（乐观锁）',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_service_type` (`service_type`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI服务调用记录表';

-- AI模型配置表
CREATE TABLE IF NOT EXISTS `ai_model_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `model_name` varchar(100) NOT NULL COMMENT '模型名称',
  `model_key` varchar(100) NOT NULL COMMENT '模型标识',
  `model_version` varchar(50) DEFAULT NULL COMMENT '模型版本',
  `model_provider` varchar(100) DEFAULT NULL COMMENT '模型提供商',
  `api_key` varchar(255) DEFAULT NULL COMMENT 'API密钥',
  `endpoint` varchar(255) DEFAULT NULL COMMENT '服务端点',
  `max_tokens` int DEFAULT NULL COMMENT '最大token数',
  `temperature` decimal(3,2) DEFAULT '0.7' COMMENT '温度参数',
  `status` tinyint DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `description` text COMMENT '模型描述',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  `version` int DEFAULT '0' COMMENT '版本号（乐观锁）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_key` (`model_key`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI模型配置表';

-- Seata分布式事务日志表（每个业务库都需要）
CREATE TABLE IF NOT EXISTS `undo_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `branch_id` bigint NOT NULL COMMENT '分支事务ID',
  `xid` varchar(100) NOT NULL COMMENT '全局事务ID',
  `context` varchar(128) NOT NULL COMMENT '上下文',
  `rollback_info` longblob NOT NULL COMMENT '回滚信息',
  `log_status` int NOT NULL COMMENT '状态：0-正常，1-全局已完成',
  `log_created` datetime NOT NULL COMMENT '创建时间',
  `log_modified` datetime NOT NULL COMMENT '修改时间',
  `ext` varchar(100) DEFAULT NULL COMMENT '扩展信息',
  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_undo_log` (`xid`,`branch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Seata分布式事务日志表';
```

## 三、表结构设计

### 3.1 用户中心库表结构
- `user`：用户表
- `user_address`：用户收货地址表

### 3.2 订单中心库表结构
- `order`：订单表
- `order_item`：订单商品表

### 3.3 商品中心库表结构
- `product`：商品表
- `product_category`：商品分类表

### 3.4 内容中心库表结构
- `ich_item`：非遗项目表
- `ich_heritage_man`：非遗传承人表

### 3.5 系统管理库表结构
- `sys_user`：管理员表
- `sys_role`：角色表

### 3.6 公共库表结构
- `sys_file_upload`：文件上传记录表
- `sys_config`：系统配置表

## 四、索引设计

### 4.1 索引设计原则
1. 为常用查询条件创建索引
2. 为外键创建索引
3. 为排序字段创建索引
4. 为分组字段创建索引
5. 使用联合索引减少索引数量

### 4.2 索引优化建议
1. 避免在索引列上使用函数
2. 避免在索引列上使用不等于（!= 或 <>）
3. 避免在索引列上使用 IS NULL 或 IS NOT NULL
4. 避免在索引列上使用 LIKE 以通配符开头
5. 避免在索引列上进行计算

## 五、初始化数据

### 5.1 管理员账号
```sql
USE `system_manage`;

-- 初始化管理员角色
INSERT INTO `sys_role` (`name`, `code`, `description`, `status`, `sort`) 
VALUES ('超级管理员', 'ROLE_ADMIN', '超级管理员', 1, 0);

-- 初始化管理员账号
INSERT INTO `sys_user` (`username`, `password`, `nickname`, `email`, `phone`, `status`, `dept_id`) 
VALUES ('admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiEN8Kr1E.8N7l2O', '超级管理员', 'admin@example.com', '13800138000', 1, 1);
```

### 5.2 商品分类
```sql
USE `product_center`;

-- 初始化商品分类
INSERT INTO `product_category` (`parent_id`, `name`, `level`, `status`, `sort`) 
VALUES 
(0, '非遗手工艺品', 0, 1, 0),
(0, '非遗食品', 0, 1, 1),
(0, '非遗服饰', 0, 1, 2);
```

## 六、数据库维护

### 6.1 数据库备份
```bash
# 备份所有数据库
mysqldump -u root -p --all-databases > all_databases_backup_$(date +%Y%m%d).sql

# 备份单个数据库
mysqldump -u root -p user_center > user_center_backup_$(date +%Y%m%d).sql
```

### 6.2 数据库优化
```sql
-- 优化表
OPTIMIZE TABLE table_name;

-- 分析表
ANALYZE TABLE table_name;

-- 修复表
REPAIR TABLE table_name;
```

### 6.3 监控SQL
```sql
-- 查看正在执行的SQL
SHOW PROCESSLIST;

-- 查看慢查询
SHOW VARIABLES LIKE 'slow_query%';
SHOW VARIABLES LIKE 'long_query_time';
```

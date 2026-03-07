# 非遗文化管理平台分布式数据库设计方案

## 目录
1. [数据库分库设计](#一数据库分库设计)
2. [分布式事务实现](#二分布式事务实现)
3. [文件存储方案](#三文件存储方案)
4. [表结构设计](#四表结构设计)
5. [初始化数据](#五初始化数据)
6. [数据库部署建议](#六数据库部署建议)

## 一、数据库分库设计

### 1.1 用户中心库 (user_center)
```sql
-- 用户中心库
CREATE DATABASE IF NOT EXISTS `user_center` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE `user_center`;

-- 用户表
CREATE TABLE `user` (
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
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

-- 用户收货地址表
CREATE TABLE `user_address` (
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
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户收货地址表';
```

### 1.2 内容中心库 (content_center)
```sql
-- 内容中心库
CREATE DATABASE IF NOT EXISTS `content_center` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE `content_center`;

-- 非遗文化分类表
CREATE TABLE `ich_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `parent_id` bigint DEFAULT '0' COMMENT '父分类ID，0表示一级分类',
  `name` varchar(50) NOT NULL COMMENT '分类名称',
  `level` int DEFAULT '1' COMMENT '分类级别：1-一级分类，2-二级分类',
  `sort` int DEFAULT '0' COMMENT '排序',
  `icon` varchar(255) DEFAULT NULL COMMENT '分类图标（七牛云存储）',
  `description` varchar(500) DEFAULT NULL COMMENT '分类描述',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='非遗文化分类表';

-- 非遗文化项目表
CREATE TABLE `ich_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '项目ID',
  `category_id` bigint NOT NULL COMMENT '分类ID',
  `name` varchar(100) NOT NULL COMMENT '项目名称',
  `cover_image` varchar(255) NOT NULL COMMENT '封面图（七牛云存储）',
  `description` text COMMENT '项目描述',
  `content` longtext COMMENT '项目详情',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态：0-下架，1-上架',
  `view_count` int DEFAULT '0' COMMENT '浏览次数',
  `like_count` int DEFAULT '0' COMMENT '点赞数',
  `comment_count` int DEFAULT '0' COMMENT '评论数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_category_id` (`category_id`),
  FULLTEXT KEY `ft_name_desc` (`name`,`description`) COMMENT '全文索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='非遗文化项目表';
```

### 1.3 订单中心库 (order_center)
```sql
-- 订单中心库
CREATE DATABASE IF NOT EXISTS `order_center` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE `order_center`;

-- 订单表
CREATE TABLE `order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `order_no` varchar(32) NOT NULL COMMENT '订单编号',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `total_amount` decimal(10,2) NOT NULL COMMENT '订单总金额',
  `payment_amount` decimal(10,2) NOT NULL COMMENT '实付金额',
  `freight_amount` decimal(10,2) DEFAULT '0.00' COMMENT '运费',
  `payment_type` varchar(20) DEFAULT 'MOCK' COMMENT '支付方式：MOCK-模拟支付，WECHAT-微信支付，ALIPAY-支付宝',
  `payment_time` datetime DEFAULT NULL COMMENT '支付时间',
  `payment_serial_number` varchar(64) DEFAULT NULL COMMENT '支付流水号',
  `status` varchar(20) NOT NULL COMMENT '订单状态：PENDING-待支付，PAID-已支付，SHIPPED-已发货，COMPLETED-已完成，CANCELLED-已取消，CLOSED-已关闭',
  `shipping_name` varchar(50) DEFAULT NULL COMMENT '收货人姓名',
  `shipping_phone` varchar(20) DEFAULT NULL COMMENT '收货人电话',
  `shipping_address` varchar(255) DEFAULT NULL COMMENT '收货地址',
  `note` varchar(500) DEFAULT NULL COMMENT '订单备注',
  `confirm_status` tinyint(1) DEFAULT '0' COMMENT '确认收货状态：0-未确认，1-已确认',
  `confirm_time` datetime DEFAULT NULL COMMENT '确认收货时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单表';

-- 订单商品表
CREATE TABLE `order_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '订单商品ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(32) NOT NULL COMMENT '订单编号',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `product_name` varchar(100) NOT NULL COMMENT '商品名称',
  `product_image` varchar(255) DEFAULT NULL COMMENT '商品图片（七牛云存储）',
  `product_price` decimal(10,2) NOT NULL COMMENT '商品价格',
  `quantity` int NOT NULL COMMENT '购买数量',
  `total_price` decimal(10,2) NOT NULL COMMENT '商品总价',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单商品表';

-- 订单操作历史表
CREATE TABLE `order_operation_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '操作ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(32) NOT NULL COMMENT '订单编号',
  `operator` varchar(50) DEFAULT NULL COMMENT '操作人',
  `operation_type` varchar(50) NOT NULL COMMENT '操作类型',
  `operation_note` varchar(500) DEFAULT NULL COMMENT '操作备注',
  `operation_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单操作历史表';
```

### 1.4 商品中心库 (product_center)
```sql
-- 商品中心库
CREATE DATABASE IF NOT EXISTS `product_center` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE `product_center`;

-- 商品分类表
CREATE TABLE `product_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `parent_id` bigint DEFAULT '0' COMMENT '父分类ID，0表示一级分类',
  `name` varchar(50) NOT NULL COMMENT '分类名称',
  `level` int DEFAULT '1' COMMENT '分类级别：1-一级分类，2-二级分类',
  `sort` int DEFAULT '0' COMMENT '排序',
  `icon` varchar(255) DEFAULT NULL COMMENT '分类图标（七牛云存储）',
  `description` varchar(500) DEFAULT NULL COMMENT '分类描述',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品分类表';

-- 商品表
CREATE TABLE `product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '商品ID',
  `category_id` bigint NOT NULL COMMENT '分类ID',
  `name` varchar(100) NOT NULL COMMENT '商品名称',
  `subtitle` varchar(200) DEFAULT NULL COMMENT '副标题',
  `main_image` varchar(255) DEFAULT NULL COMMENT '主图（七牛云存储）',
  `sub_images` text COMMENT '子图（七牛云存储，多个图片URL用逗号分隔）',
  `detail` text COMMENT '商品详情',
  `price` decimal(10,2) NOT NULL COMMENT '价格',
  `stock` int NOT NULL DEFAULT '0' COMMENT '库存',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态：0-下架，1-上架',
  `sales` int DEFAULT '0' COMMENT '销量',
  `view_count` int DEFAULT '0' COMMENT '浏览量',
  `comment_count` int DEFAULT '0' COMMENT '评论数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_status` (`status`),
  FULLTEXT KEY `ft_name_subtitle` (`name`,`subtitle`) COMMENT '全文索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品表';

-- 购物车表
CREATE TABLE `cart` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '购物车ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `quantity` int NOT NULL DEFAULT '1' COMMENT '数量',
  `checked` tinyint(1) DEFAULT '1' COMMENT '是否选中：0-未选中，1-已选中',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`,`product_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='购物车表';
```

### 1.5 AI服务库 (ai_service)
```sql
-- AI服务库
CREATE DATABASE IF NOT EXISTS `ai_service` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE `ai_service`;

-- AI知识库表
CREATE TABLE `ai_knowledge_base` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '知识ID',
  `question` varchar(500) NOT NULL COMMENT '问题',
  `answer` text NOT NULL COMMENT '答案',
  `category_id` bigint DEFAULT NULL COMMENT '分类ID',
  `keywords` varchar(255) DEFAULT NULL COMMENT '关键词，多个用逗号分隔',
  `view_count` int DEFAULT '0' COMMENT '查看次数',
  `useful_count` int DEFAULT '0' COMMENT '有用次数',
  `useless_count` int DEFAULT '0' COMMENT '无用次数',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_category_id` (`category_id`),
  FULLTEXT KEY `ft_question_answer` (`question`,`answer`) COMMENT '全文索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI知识库表';

-- AI对话记录表
CREATE TABLE `ai_conversation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '对话ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `title` varchar(100) NOT NULL COMMENT '对话标题',
  `model` varchar(50) DEFAULT 'gpt-3.5-turbo' COMMENT 'AI模型',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态：0-已结束，1-进行中',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI对话记录表';

-- AI对话消息表
CREATE TABLE `ai_conversation_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `conversation_id` bigint NOT NULL COMMENT '对话ID',
  `role` varchar(20) NOT NULL COMMENT '角色：system-系统，user-用户，assistant-AI助手',
  `content` text NOT NULL COMMENT '消息内容',
  `tokens` int DEFAULT '0' COMMENT '消耗的token数量',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_conversation_id` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI对话消息表';
```

### 1.6 系统管理库 (system_manage)
```sql
-- 系统管理库
CREATE DATABASE IF NOT EXISTS `system_manage` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE `system_manage`;

-- 系统用户表
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(100) NOT NULL COMMENT '密码',
  `nickname` varchar(50) DEFAULT NULL COMMENT '昵称',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像（七牛云存储）',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `last_login_ip` varchar(50) DEFAULT NULL COMMENT '最后登录IP',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统用户表';

-- 角色表
CREATE TABLE `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `name` varchar(50) NOT NULL COMMENT '角色名称',
  `code` varchar(50) NOT NULL COMMENT '角色编码',
  `description` varchar(200) DEFAULT NULL COMMENT '角色描述',
  `sort` int DEFAULT '0' COMMENT '排序',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';

-- 用户角色关联表
CREATE TABLE `sys_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`,`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关联表';

-- 权限表
CREATE TABLE `sys_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '权限ID',
  `parent_id` bigint DEFAULT '0' COMMENT '父权限ID，0表示一级权限',
  `name` varchar(50) NOT NULL COMMENT '权限名称',
  `code` varchar(100) NOT NULL COMMENT '权限编码',
  `type` tinyint NOT NULL COMMENT '权限类型：1-目录，2-菜单，3-按钮',
  `path` varchar(200) DEFAULT NULL COMMENT '路由地址',
  `component` varchar(200) DEFAULT NULL COMMENT '组件路径',
  `icon` varchar(100) DEFAULT NULL COMMENT '图标',
  `sort` int DEFAULT '0' COMMENT '排序',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限表';

-- 角色权限关联表
CREATE TABLE `sys_role_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `permission_id` bigint NOT NULL COMMENT '权限ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`,`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限关联表';

-- 操作日志表
CREATE TABLE `sys_operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `user_id` bigint DEFAULT NULL COMMENT '操作用户ID',
  `username` varchar(50) DEFAULT NULL COMMENT '操作用户名',
  `operation` varchar(100) NOT NULL COMMENT '操作',
  `method` varchar(200) DEFAULT NULL COMMENT '请求方法',
  `params` text COMMENT '请求参数',
  `time` bigint DEFAULT NULL COMMENT '执行时长(毫秒)',
  `ip` varchar(50) DEFAULT NULL COMMENT 'IP地址',
  `user_agent` varchar(500) DEFAULT NULL COMMENT '用户代理',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态：0-失败，1-成功',
  `error_msg` text COMMENT '错误消息',
  `operation_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_operation_time` (`operation_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志表';

-- 登录日志表
CREATE TABLE `sys_login_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `user_id` bigint DEFAULT NULL COMMENT '用户ID',
  `username` varchar(50) DEFAULT NULL COMMENT '用户名',
  `ip` varchar(50) DEFAULT NULL COMMENT 'IP地址',
  `location` varchar(100) DEFAULT NULL COMMENT '登录地点',
  `browser` varchar(100) DEFAULT NULL COMMENT '浏览器类型',
  `os` varchar(100) DEFAULT NULL COMMENT '操作系统',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态：0-失败，1-成功',
  `msg` varchar(255) DEFAULT NULL COMMENT '提示消息',
  `login_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_login_time` (`login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录日志表';

-- 系统配置表
CREATE TABLE `sys_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `config_key` varchar(100) NOT NULL COMMENT '配置键',
  `config_value` text COMMENT '配置值',
  `config_type` varchar(20) DEFAULT 'TEXT' COMMENT '配置类型：TEXT-文本，JSON-JSON，BOOLEAN-布尔，NUMBER-数字',
  `description` varchar(500) DEFAULT NULL COMMENT '配置描述',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统配置表';

-- 文件上传记录表
CREATE TABLE `sys_file_upload` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '文件ID',
  `file_name` varchar(255) NOT NULL COMMENT '文件名',
  `file_key` varchar(255) NOT NULL COMMENT '文件key（七牛云存储）',
  `file_url` varchar(500) NOT NULL COMMENT '文件URL',
  `file_size` bigint NOT NULL COMMENT '文件大小（字节）',
  `file_type` varchar(100) DEFAULT NULL COMMENT '文件类型',
  `storage_type` varchar(20) DEFAULT 'QINIU' COMMENT '存储类型：LOCAL-本地，QINIU-七牛云，ALIYUN-阿里云',
  `upload_user_id` bigint DEFAULT NULL COMMENT '上传用户ID',
  `upload_ip` varchar(50) DEFAULT NULL COMMENT '上传IP',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_upload_user_id` (`upload_user_id`),
  KEY `idx_file_key` (`file_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件上传记录表';
```

### 1.7 公共库 (common_db)
```sql
-- 公共库
CREATE DATABASE IF NOT EXISTS `common_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE `common_db`;

-- 收藏表
CREATE TABLE `favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `favorite_type` varchar(20) NOT NULL COMMENT '收藏类型：PRODUCT-商品，ICH-非遗项目',
  `favorite_id` bigint NOT NULL COMMENT '收藏对象ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_favorite` (`user_id`,`favorite_type`,`favorite_id`),
  KEY `idx_favorite` (`favorite_type`,`favorite_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收藏表';

-- 浏览历史表
CREATE TABLE `browse_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '历史ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `browse_type` varchar(20) NOT NULL COMMENT '浏览类型：PRODUCT-商品，ICH-非遗项目',
  `browse_id` bigint NOT NULL COMMENT '浏览对象ID',
  `browse_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '浏览时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_browse` (`user_id`,`browse_type`),
  KEY `idx_browse` (`browse_type`,`browse_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='浏览历史表';

-- 地区表
CREATE TABLE `region` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '地区ID',
  `parent_id` bigint DEFAULT '0' COMMENT '父地区ID，0表示省份',
  `name` varchar(50) NOT NULL COMMENT '地区名称',
  `level` tinyint NOT NULL COMMENT '级别：1-省，2-市，3-区/县',
  `code` varchar(20) DEFAULT NULL COMMENT '行政区划代码',
  `sort` int DEFAULT '0' COMMENT '排序',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_level` (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='地区表';
```

## 二、分布式事务实现

### 2.1 Seata AT模式配置

#### 2.1.1 添加依赖
在`pom.xml`中添加Seata依赖：

```xml
<!-- Seata -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-seata</artifactId>
</dependency>
```

#### 2.1.2 配置Seata
在`application.yml`中添加Seata配置：

```yaml
spring:
  cloud:
    alibaba:
      seata:
        tx-service-group: ${spring.application.name}-tx-group
        registry:
          type: nacos
          nacos:
            server-addr: ${spring.cloud.nacos.discovery.server-addr}
            namespace: ${spring.cloud.nacos.discovery.namespace}
            group: SEATA_GROUP
        config:
          type: nacos
          nacos:
            server-addr: ${spring.cloud.nacos.discovery.server-addr}
            namespace: ${spring.cloud.nacos.discovery.namespace}
            group: SEATA_GROUP
```

#### 2.1.3 创建undo_log表
在每个业务库中创建`undo_log`表：

```sql
-- 注意：需要在每个业务库中都创建此表
CREATE TABLE `undo_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `branch_id` bigint(20) NOT NULL,
  `xid` varchar(100) NOT NULL,
  `context` varchar(128) NOT NULL,
  `rollback_info` longblob NOT NULL,
  `log_status` int(11) NOT NULL,
  `log_created` datetime NOT NULL,
  `log_modified` datetime NOT NULL,
  `ext` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_undo_log` (`xid`,`branch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Seata分布式事务日志表';
```

### 2.2 分布式事务示例

#### 2.2.1 创建订单服务

```java
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductFeignClient productFeignClient;
    private final AccountFeignClient accountFeignClient;
    
    @GlobalTransactional(name = "createOrder", timeoutMills = 300000, rollbackFor = Exception.class)
    @Override
    public OrderDTO createOrder(OrderCreateDTO createDTO) {
        // 1. 扣减库存
        productFeignClient.reduceStock(createDTO.getProductId(), createDTO.getQuantity());
        
        // 2. 扣减余额
        accountFeignClient.reduceBalance(createDTO.getUserId(), createDTO.getTotalAmount());
        
        // 3. 创建订单
        Order order = convertToOrder(createDTO);
        orderMapper.insert(order);
        
        // 4. 创建订单商品
        OrderItem orderItem = convertToOrderItem(createDTO, order.getId());
        orderItemMapper.insert(orderItem);
        
        // 5. 返回订单信息
        return convertToDTO(order, orderItem);
    }
    
    // 其他方法...
}
```

#### 2.2.2 商品服务接口

```java
@FeignClient(name = "product-service", path = "/api/product")
public interface ProductFeignClient {
    @PostMapping("/reduceStock")
    Result<Void> reduceStock(@RequestParam("productId") Long productId, 
                            @RequestParam("quantity") Integer quantity);
}
```

#### 2.2.3 账户服务接口

```java
@FeignClient(name = "account-service", path = "/api/account")
public interface AccountFeignClient {
    @PostMapping("/reduceBalance")
    Result<Void> reduceBalance(@RequestParam("userId") Long userId, 
                              @RequestParam("amount") BigDecimal amount);
}
```

## 三、文件存储方案

### 3.1 七牛云存储配置

#### 3.1.1 添加依赖

```xml
<!-- 七牛云SDK -->
<dependency>
    <groupId>com.qiniu</groupId>
    <artifactId>qiniu-java-sdk</artifactId>
    <version>7.2.0</version>
</dependency>
```

#### 3.1.2 配置文件

在`application.yml`中添加七牛云配置：

```yaml
qiniu:
  access-key: ${QINIU_ACCESS_KEY}
  secret-key: ${QINIU_SECRET_KEY}
  bucket: ${QINIU_BUCKET}
  domain: ${QINIU_DOMAIN}
  zone: z2  # 存储区域：z0-华东，z1-华北，z2-华南，na0-北美，as0-东南亚
  prefix: ich/  # 存储路径前缀
  expire-seconds: 3600  # 下载链接过期时间（秒）
```

#### 3.1.3 七牛云服务实现

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class QiniuServiceImpl implements FileStorageService {
    private final QiniuProperties qiniuProperties;
    private final SysFileUploadMapper fileUploadMapper;
    
    @Override
    public FileUploadResult upload(MultipartFile file, String filePath, Long userId) {
        try {
            // 1. 生成文件名
            String originalFilename = file.getOriginalFilename();
            String fileType = FileUtil.getFileType(originalFilename);
            String fileName = generateFileName(filePath, fileType);
            
            // 2. 上传到七牛云
            String uploadToken = getUploadToken();
            Response response = uploadManager.put(file.getInputStream(), fileName, uploadToken, null, file.getContentType());
            
            // 3. 解析上传结果
            DefaultPutRet putRet = JSON.parseObject(response.bodyString(), DefaultPutRet.class);
            String fileUrl = qiniuProperties.getDomain() + "/" + putRet.key;
            
            // 4. 保存文件上传记录
            SysFileUpload fileUpload = new SysFileUpload();
            fileUpload.setFileName(originalFilename);
            fileUpload.setFileKey(putRet.key);
            fileUpload.setFileUrl(fileUrl);
            fileUpload.setFileSize(file.getSize());
            fileUpload.setFileType(fileType);
            fileUpload.setStorageType("QINIU");
            fileUpload.setUploadUserId(userId);
            fileUpload.setUploadIp(IpUtils.getIpAddr());
            fileUploadMapper.insert(fileUpload);
            
            // 5. 返回上传结果
            return new FileUploadResult()
                .setFileName(originalFilename)
                .setFileKey(putRet.key)
                .setFileUrl(fileUrl)
                .setFileSize(file.getSize())
                .setFileType(fileType);
                
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new BusinessException("文件上传失败：" + e.getMessage());
        }
    }
    
    @Override
    public String getDownloadUrl(String fileKey, long expireSeconds) {
        try {
            String domain = qiniuProperties.getDomain();
            String publicUrl = domain + "/" + fileKey;
            
            if (expireSeconds <= 0) {
                expireSeconds = qiniuProperties.getExpireSeconds();
            }
            
            Auth auth = Auth.create(qiniuProperties.getAccessKey(), qiniuProperties.getSecretKey());
            return auth.privateDownloadUrl(publicUrl, expireSeconds);
        } catch (Exception e) {
            log.error("获取下载链接失败", e);
            return "";
        }
    }
    
    // 其他方法...
}
```

## 四、表结构设计

### 4.1 数据库分库分表策略

#### 4.1.1 分库策略
- **用户中心库**：按用户ID范围分片
- **订单中心库**：按订单ID哈希分片
- **商品中心库**：按商品ID范围分片
- **内容中心库**：按内容ID范围分片
- **AI服务库**：按用户ID哈希分片
- **系统管理库**：单库不分片
- **公共库**：单库不分片

#### 4.1.2 分表策略
- 大表（如订单表、订单商品表）按时间范围分表，每月一张表
- 日志表按时间范围分表，每周一张表

### 4.2 索引设计

#### 4.2.1 主键索引
- 所有表使用自增主键
- 主键索引名称为`PRIMARY`

#### 4.2.2 唯一索引
- 唯一索引命名规则：`uk_字段名`
- 例如：`uk_username`, `uk_mobile`

#### 4.2.3 普通索引
- 普通索引命名规则：`idx_字段名`
- 例如：`idx_user_id`, `idx_category_id`

#### 4.2.4 联合索引
- 联合索引命名规则：`idx_字段1_字段2`
- 例如：`idx_user_status`

## 五、初始化数据

### 5.1 初始化系统用户

```sql
-- 初始化超级管理员
INSERT INTO `system_manage`.`sys_user` (`id`, `username`, `password`, `nickname`, `email`, `phone`, `status`, `create_time`, `update_time`) 
VALUES (1, 'admin', '$2a$10$E4pYhKj4oJZ5zV5g5g5Z.uJ5X5X5X5X5X5X5X5X5X5X5X5X5X5X', '超级管理员', 'admin@example.com', '13800138000', 1, NOW(), NOW());

-- 初始化角色
INSERT INTO `system_manage`.`sys_role` (`id`, `name`, `code`, `description`, `sort`, `status`, `create_time`, `update_time`) 
VALUES (1, '超级管理员', 'ROLE_ADMIN', '拥有所有权限', 1, 1, NOW(), NOW());

-- 关联用户角色
INSERT INTO `system_manage`.`sys_user_role` (`user_id`, `role_id`, `create_time`) 
VALUES (1, 1, NOW());
```

### 5.2 初始化系统配置

```sql
-- 初始化系统配置
INSERT INTO `system_manage`.`sys_config` (`config_key`, `config_value`, `config_type`, `description`) VALUES 
('site_name', '非遗文化管理平台', 'TEXT', '网站名称'),
('site_logo', 'https://example.com/logo.png', 'TEXT', '网站Logo'),
('site_icp', '京ICP备12345678号-1', 'TEXT', 'ICP备案号'),
('site_copyright', 'Copyright © 2023 非遗文化管理平台', 'TEXT', '版权信息'),
('site_description', '非遗文化管理平台，传承中华优秀传统文化', 'TEXT', '网站描述'),
('site_keywords', '非遗,文化,传承,传统文化', 'TEXT', '网站关键词'),
('file_upload_max_size', '10', 'NUMBER', '文件上传大小限制(MB)'),
('file_upload_allowed_types', 'jpg,jpeg,png,gif,doc,docx,xls,xlsx,pdf,txt,zip,rar', 'TEXT', '允许上传的文件类型'),
('sms_enabled', 'false', 'BOOLEAN', '是否启用短信验证'),
('email_enabled', 'true', 'BOOLEAN', '是否启用邮件通知');
```

## 六、数据库部署建议

### 6.1 部署架构

```
+-------------------+    +-------------------+    +-------------------+
|    主库 (Master)  |<---|    从库 (Slave)   |<---|    从库 (Slave)   |
+-------------------+    +-------------------+    +-------------------+
         ^
         |
+-------------------+
|    负载均衡器     |
+-------------------+
         |
+-------------------+
|    应用服务器     |
+-------------------+
```

### 6.2 配置建议

#### 6.2.1 MySQL配置

```ini
[mysqld]
# 基础配置
port = 3306
basedir = /usr/local/mysql
datadir = /data/mysql
socket = /tmp/mysql.sock

# 字符集
character-set-server = utf8mb4
collation-server = utf8mb4_0900_ai_ci

# 连接配置
max_connections = 2000
max_connect_errors = 1000
wait_timeout = 300
interactive_timeout = 300

# 缓冲池配置
innodb_buffer_pool_size = 8G
innodb_buffer_pool_instances = 8

# 日志配置
innodb_log_file_size = 2G
innodb_log_files_in_group = 2
innodb_log_buffer_size = 16M

# 事务隔离级别
transaction-isolation = READ-COMMITTED

# 慢查询日志
slow_query_log = 1
slow_query_log_file = /var/log/mysql/mysql-slow.log
long_query_time = 2
log_queries_not_using_indexes = 1

# 其他优化
innodb_flush_log_at_trx_commit = 1
sync_binlog = 1
innodb_flush_method = O_DIRECT
innodb_io_capacity = 2000
innodb_io_capacity_max = 4000
```

#### 6.2.2 分库分表中间件

建议使用ShardingSphere或MyCat作为分库分表中间件，配置示例：

```yaml
# ShardingSphere配置示例
spring:
  shardingsphere:
    datasource:
      names: ds0,ds1,ds2,ds3
      ds0:
        type: com.zaxxer.hikari.HikariDataSource
        driver-class-name: com.mysql.cj.jdbc.Driver
        jdbc-url: jdbc:mysql://db0:3306/order_center?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
        username: root
        password: 123456
      # 其他数据源配置...
    
    sharding:
      tables:
        t_order:
          actual-data-nodes: ds$->{0..3}.t_order_$->{0..15}
          table-strategy:
            inline:
              sharding-column: order_id
              algorithm-expression: t_order_$->{order_id % 16}
          database-strategy:
            inline:
              sharding-column: user_id
              algorithm-expression: ds$->{user_id % 4}
    
    props:
      sql.show: true
```

### 6.3 监控与维护

1. **监控指标**
   - 数据库连接数
   - 慢查询数量
   - 锁等待时间
   - 缓冲池命中率
   - 主从延迟

2. **备份策略**
   - 每日全量备份
   - 每小时增量备份
   - 备份保留7天

3. **优化建议**
   - 定期分析慢查询日志
   - 定期优化表
   - 定期更新统计信息
   - 定期清理过期数据

## 七、总结

1. **数据库分库**：按业务垂直拆分为7个库，提高系统扩展性和性能
2. **分布式事务**：使用Seata AT模式保证跨库事务一致性
3. **文件存储**：集成七牛云存储，支持大文件上传下载
4. **高可用**：主从复制、读写分离、分库分表
5. **可维护**：完善的监控告警、备份恢复机制

此方案既满足了当前业务需求，又为未来扩展留出了空间，是一个兼顾性能和可维护性的分布式数据库解决方案。

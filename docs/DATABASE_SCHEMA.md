# 非遗文化管理平台数据库设计

## 1. 用户相关表

### 1.1 用户表 (user)
```sql
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(100) NOT NULL COMMENT '密码（加密存储）',
  `nickname` varchar(50) DEFAULT NULL COMMENT '昵称',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像URL',
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
```

### 1.2 用户收货地址表 (user_address)
```sql
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

## 2. 非遗文化相关表

### 2.1 非遗文化分类表 (ich_category)
```sql
CREATE TABLE `ich_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `parent_id` bigint DEFAULT '0' COMMENT '父分类ID，0表示一级分类',
  `name` varchar(50) NOT NULL COMMENT '分类名称',
  `level` int DEFAULT '1' COMMENT '分类级别：1-一级分类，2-二级分类',
  `sort` int DEFAULT '0' COMMENT '排序',
  `icon` varchar(255) DEFAULT NULL COMMENT '分类图标',
  `description` text COMMENT '分类描述',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='非遗文化分类表';
```

### 2.2 非遗文化项目表 (ich_item)
```sql
CREATE TABLE `ich_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '项目ID',
  `category_id` bigint NOT NULL COMMENT '分类ID',
  `name` varchar(100) NOT NULL COMMENT '项目名称',
  `cover_image` varchar(255) NOT NULL COMMENT '封面图',
  `images` text COMMENT '项目图片，多个图片URL以逗号分隔',
  `video_url` varchar(255) DEFAULT NULL COMMENT '视频URL',
  `province` varchar(50) DEFAULT NULL COMMENT '所在省份',
  `city` varchar(50) DEFAULT NULL COMMENT '所在城市',
  `level` varchar(20) DEFAULT NULL COMMENT '保护级别：国家级/省级/市级',
  `declaration_unit` varchar(100) DEFAULT NULL COMMENT '申报地区或单位',
  `protection_unit` varchar(100) DEFAULT NULL COMMENT '保护单位',
  `approval_date` date DEFAULT NULL COMMENT '批准日期',
  `batch_number` varchar(50) DEFAULT NULL COMMENT '批次编号',
  `description` text COMMENT '项目描述',
  `content` longtext COMMENT '详细介绍',
  `status` tinyint(1) DEFAULT '0' COMMENT '状态：0-待审核，1-已发布，2-已下架',
  `view_count` int DEFAULT '0' COMMENT '浏览次数',
  `like_count` int DEFAULT '0' COMMENT '点赞数',
  `collect_count` int DEFAULT '0' COMMENT '收藏数',
  `create_user_id` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_status` (`status`),
  FULLTEXT KEY `ft_name_desc` (`name`,`description`) WITH PARSER `ngram`
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='非遗文化项目表';
```

### 2.3 传承人表 (ich_heritage_man)
```sql
CREATE TABLE `ich_heritage_man` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '传承人ID',
  `name` varchar(50) NOT NULL COMMENT '姓名',
  `gender` tinyint(1) DEFAULT '0' COMMENT '性别：0-未知，1-男，2-女',
  `birth_date` date DEFAULT NULL COMMENT '出生日期',
  `portrait` varchar(255) DEFAULT NULL COMMENT '头像',
  `id_card` varchar(18) DEFAULT NULL COMMENT '身份证号',
  `nation` varchar(20) DEFAULT NULL COMMENT '民族',
  `native_place` varchar(100) DEFAULT NULL COMMENT '籍贯',
  `residence` varchar(255) DEFAULT NULL COMMENT '现居地',
  `title` varchar(100) DEFAULT NULL COMMENT '职称',
  `honor` varchar(100) DEFAULT NULL COMMENT '荣誉称号',
  `representative` tinyint(1) DEFAULT '0' COMMENT '是否代表性传承人：0-否，1-是',
  `representative_level` varchar(20) DEFAULT NULL COMMENT '代表性传承人级别',
  `representative_year` int DEFAULT NULL COMMENT '评定年份',
  `biography` text COMMENT '个人简介',
  `achievement` text COMMENT '主要成就',
  `contact_phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `contact_email` varchar(100) DEFAULT NULL COMMENT '联系邮箱',
  `address` varchar(255) DEFAULT NULL COMMENT '联系地址',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='传承人表';
```

### 2.4 非遗项目与传承人关联表 (ich_item_heritage_man_relation)
```sql
CREATE TABLE `ich_item_heritage_man_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `item_id` bigint NOT NULL COMMENT '非遗项目ID',
  `heritage_man_id` bigint NOT NULL COMMENT '传承人ID',
  `relation_type` varchar(20) NOT NULL COMMENT '关联类型：MAIN-主要传承人，PART-参与传承人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_item_heritage_man` (`item_id`,`heritage_man_id`),
  KEY `idx_heritage_man_id` (`heritage_man_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='非遗项目与传承人关联表';
```

## 3. AI智能服务相关表

### 3.1 AI问答知识库表 (ai_knowledge_base)
```sql
CREATE TABLE `ai_knowledge_base` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '知识ID',
  `question` varchar(500) NOT NULL COMMENT '问题',
  `answer` text NOT NULL COMMENT '答案',
  `category_id` bigint DEFAULT NULL COMMENT '分类ID',
  `keywords` varchar(255) DEFAULT NULL COMMENT '关键词，多个以逗号分隔',
  `similar_questions` text COMMENT '相似问题，JSON数组格式',
  `hit_count` int DEFAULT '0' COMMENT '命中次数',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category_id`),
  FULLTEXT KEY `ft_question` (`question`) WITH PARSER `ngram`
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI问答知识库表';
```

### 3.2 AI对话记录表 (ai_conversation)
```sql
CREATE TABLE `ai_conversation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '对话ID',
  `user_id` bigint DEFAULT NULL COMMENT '用户ID，未登录用户为NULL',
  `session_id` varchar(64) NOT NULL COMMENT '会话ID',
  `title` varchar(100) DEFAULT NULL COMMENT '对话标题',
  `ip_address` varchar(50) DEFAULT NULL COMMENT 'IP地址',
  `user_agent` varchar(500) DEFAULT NULL COMMENT '用户代理',
  `start_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `message_count` int DEFAULT '0' COMMENT '消息数量',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态：0-已结束，1-进行中',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI对话记录表';
```

### 3.3 AI对话消息表 (ai_conversation_message)
```sql
CREATE TABLE `ai_conversation_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `conversation_id` bigint NOT NULL COMMENT '对话ID',
  `message_type` varchar(20) NOT NULL COMMENT '消息类型：TEXT-文本，IMAGE-图片',
  `content` text NOT NULL COMMENT '消息内容',
  `role` varchar(20) NOT NULL COMMENT '角色：USER-用户，ASSISTANT-助手',
  `tokens` int DEFAULT '0' COMMENT '消耗的token数量',
  `model` varchar(50) DEFAULT NULL COMMENT '使用的模型',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_conversation_id` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI对话消息表';
```

## 4. 文创商城相关表

### 4.1 文创商品分类表 (product_category)
```sql
CREATE TABLE `product_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `parent_id` bigint DEFAULT '0' COMMENT '父分类ID，0表示一级分类',
  `name` varchar(50) NOT NULL COMMENT '分类名称',
  `level` int DEFAULT '1' COMMENT '分类级别：1-一级分类，2-二级分类',
  `sort` int DEFAULT '0' COMMENT '排序',
  `icon` varchar(255) DEFAULT NULL COMMENT '分类图标',
  `description` text COMMENT '分类描述',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文创商品分类表';
```

### 4.2 文创商品表 (product)
```sql
CREATE TABLE `product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '商品ID',
  `category_id` bigint NOT NULL COMMENT '分类ID',
  `name` varchar(100) NOT NULL COMMENT '商品名称',
  `subtitle` varchar(200) DEFAULT NULL COMMENT '副标题',
  `main_image` varchar(255) DEFAULT NULL COMMENT '主图',
  `sub_images` text COMMENT '子图，多个图片URL以逗号分隔',
  `detail` text COMMENT '商品详情',
  `price` decimal(10,2) NOT NULL COMMENT '价格',
  `stock` int NOT NULL DEFAULT '0' COMMENT '库存',
  `sales` int DEFAULT '0' COMMENT '销量',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态：0-下架，1-上架',
  `heritage_id` bigint DEFAULT NULL COMMENT '关联的非遗项目ID',
  `heritage_man_id` bigint DEFAULT NULL COMMENT '关联的传承人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_status` (`status`),
  FULLTEXT KEY `ft_name_subtitle` (`name`,`subtitle`) WITH PARSER `ngram`
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文创商品表';
```

### 4.3 商品属性表 (product_attribute)
```sql
CREATE TABLE `product_attribute` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '属性ID',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `name` varchar(50) NOT NULL COMMENT '属性名称',
  `value` varchar(100) NOT NULL COMMENT '属性值',
  `sort` int DEFAULT '0' COMMENT '排序',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品属性表';
```

### 4.4 购物车表 (cart)
```sql
CREATE TABLE `cart` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '购物车ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `quantity` int NOT NULL DEFAULT '1' COMMENT '数量',
  `selected` tinyint(1) DEFAULT '1' COMMENT '是否选中：0-未选中，1-已选中',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`,`product_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='购物车表';
```

## 5. 订单相关表

### 5.1 订单表 (`order`)
```sql
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
```

### 5.2 订单商品表 (order_item)
```sql
CREATE TABLE `order_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '订单商品ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(32) NOT NULL COMMENT '订单编号',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `product_name` varchar(100) NOT NULL COMMENT '商品名称',
  `product_image` varchar(255) DEFAULT NULL COMMENT '商品图片',
  `current_price` decimal(10,2) NOT NULL COMMENT '下单时的商品价格',
  `quantity` int NOT NULL COMMENT '购买数量',
  `total_price` decimal(10,2) NOT NULL COMMENT '商品总价',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单商品表';
```

### 5.3 订单操作历史表 (order_operation_history)
```sql
CREATE TABLE `order_operation_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '操作记录ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(32) NOT NULL COMMENT '订单编号',
  `operator` varchar(50) DEFAULT NULL COMMENT '操作人：用户ID或系统',
  `operation_type` varchar(20) NOT NULL COMMENT '操作类型：CREATE-创建订单，PAY-支付订单，SHIP-发货，CONFIRM-确认收货，CANCEL-取消订单，DELETE-删除订单，COMMENT-订单评价',
  `operation_note` varchar(500) DEFAULT NULL COMMENT '操作备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单操作历史表';
```

### 5.4 物流信息表 (order_logistics)
```sql
CREATE TABLE `order_logistics` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物流ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(32) NOT NULL COMMENT '订单编号',
  `logistics_company` varchar(50) DEFAULT '模拟物流' COMMENT '物流公司',
  `logistics_no` varchar(50) DEFAULT NULL COMMENT '物流单号',
  `receiver_name` varchar(50) NOT NULL COMMENT '收货人姓名',
  `receiver_phone` varchar(20) NOT NULL COMMENT '收货人电话',
  `receiver_address` varchar(255) NOT NULL COMMENT '收货地址',
  `sender_name` varchar(50) DEFAULT NULL COMMENT '发货人姓名',
  `sender_phone` varchar(20) DEFAULT NULL COMMENT '发货人电话',
  `sender_address` varchar(255) DEFAULT NULL COMMENT '发货地址',
  `status` varchar(20) DEFAULT 'PENDING' COMMENT '物流状态：PENDING-待发货，SHIPPED-已发货，IN_TRANSIT-运输中，DELIVERED-已签收，EXCEPTION-异常',
  `estimated_delivery_time` datetime DEFAULT NULL COMMENT '预计送达时间',
  `delivery_time` datetime DEFAULT NULL COMMENT '实际送达时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_id` (`order_id`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_logistics_no` (`logistics_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物流信息表';
```

### 5.5 物流轨迹表 (logistics_trace)
```sql
CREATE TABLE `logistics_trace` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '轨迹ID',
  `logistics_id` bigint NOT NULL COMMENT '物流信息ID',
  `order_no` varchar(32) NOT NULL COMMENT '订单编号',
  `logistics_no` varchar(50) DEFAULT NULL COMMENT '物流单号',
  `trace_time` datetime NOT NULL COMMENT '物流时间',
  `trace_info` varchar(500) NOT NULL COMMENT '物流信息',
  `location` varchar(100) DEFAULT NULL COMMENT '当前位置',
  `status` varchar(20) DEFAULT NULL COMMENT '物流状态',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_logistics_id` (`logistics_id`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_logistics_no` (`logistics_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物流轨迹表';
```

## 6. 系统管理相关表

### 6.1 系统用户表 (sys_user)
```sql
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(100) NOT NULL COMMENT '密码',
  `nickname` varchar(50) DEFAULT NULL COMMENT '昵称',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像',
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
```

### 6.2 角色表 (sys_role)
```sql
CREATE TABLE `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `name` varchar(50) NOT NULL COMMENT '角色名称',
  `code` varchar(50) NOT NULL COMMENT '角色编码',
  `description` varchar(200) DEFAULT NULL COMMENT '描述',
  `sort` int DEFAULT '0' COMMENT '排序',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';
```

### 6.3 用户角色关联表 (sys_user_role)
```sql
CREATE TABLE `sys_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`,`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关联表';
```

### 6.4 权限表 (sys_permission)
```sql
CREATE TABLE `sys_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '权限ID',
  `parent_id` bigint DEFAULT '0' COMMENT '父权限ID',
  `name` varchar(50) NOT NULL COMMENT '权限名称',
  `code` varchar(100) NOT NULL COMMENT '权限标识',
  `type` tinyint NOT NULL COMMENT '权限类型：1-目录，2-菜单，3-按钮',
  `path` varchar(200) DEFAULT NULL COMMENT '路由地址',
  `component` varchar(100) DEFAULT NULL COMMENT '组件路径',
  `icon` varchar(100) DEFAULT NULL COMMENT '图标',
  `sort` int DEFAULT '0' COMMENT '排序',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限表';
```

### 6.5 角色权限关联表 (sys_role_permission)
```sql
CREATE TABLE `sys_role_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `permission_id` bigint NOT NULL COMMENT '权限ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`,`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限关联表';
```

## 7. 系统日志表

### 7.1 操作日志表 (sys_operation_log)
```sql
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
  `status` tinyint(1) DEFAULT '1' COMMENT '操作状态：0-失败，1-成功',
  `error_msg` text COMMENT '错误消息',
  `operation_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_operation_time` (`operation_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志表';
```

### 7.2 登录日志表 (sys_login_log)
```sql
CREATE TABLE `sys_login_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `user_id` bigint DEFAULT NULL COMMENT '用户ID',
  `username` varchar(50) DEFAULT NULL COMMENT '用户名',
  `ip` varchar(50) DEFAULT NULL COMMENT 'IP地址',
  `location` varchar(100) DEFAULT NULL COMMENT '登录地点',
  `browser` varchar(100) DEFAULT NULL COMMENT '浏览器类型',
  `os` varchar(50) DEFAULT NULL COMMENT '操作系统',
  `status` tinyint(1) DEFAULT '1' COMMENT '登录状态：0-失败，1-成功',
  `msg` varchar(255) DEFAULT NULL COMMENT '提示消息',
  `login_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_login_time` (`login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录日志表';
```

## 8. 其他功能表

### 8.1 收藏表 (favorite)
```sql
CREATE TABLE `favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `target_id` bigint NOT NULL COMMENT '收藏目标ID',
  `target_type` varchar(20) NOT NULL COMMENT '收藏类型：PRODUCT-商品，ICH-非遗项目，VIDEO-视频',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_target` (`user_id`,`target_id`,`target_type`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_target` (`target_id`,`target_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收藏表';
```

### 8.2 浏览历史表 (browse_history)
```sql
CREATE TABLE `browse_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '历史ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `target_id` bigint NOT NULL COMMENT '浏览目标ID',
  `target_type` varchar(20) NOT NULL COMMENT '浏览类型：PRODUCT-商品，ICH-非遗项目，VIDEO-视频',
  `browse_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '浏览时间',
  `duration` int DEFAULT '0' COMMENT '浏览时长(秒)',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_target` (`target_id`,`target_type`),
  KEY `idx_browse_time` (`browse_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='浏览历史表';
```

### 8.3 系统配置表 (sys_config)
```sql
CREATE TABLE `sys_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '参数ID',
  `config_key` varchar(100) NOT NULL COMMENT '参数键名',
  `config_value` text COMMENT '参数键值',
  `config_type` varchar(10) DEFAULT 'TEXT' COMMENT '参数类型：TEXT-文本，JSON-JSON数据，BOOLEAN-布尔值',
  `description` varchar(500) DEFAULT NULL COMMENT '参数描述',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统配置表';
```

## 数据库初始化脚本

```sql
-- 初始化管理员账号
INSERT INTO `sys_user` (`username`, `password`, `nickname`, `email`, `phone`, `status`, `create_time`, `update_time`) 
VALUES ('admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '系统管理员', 'admin@example.com', '13800138000', 1, NOW(), NOW());

-- 初始化角色
INSERT INTO `sys_role` (`name`, `code`, `description`, `sort`, `status`) VALUES 
('超级管理员', 'ROLE_ADMIN', '拥有所有权限', 1, 1),
('内容管理员', 'ROLE_CONTENT', '管理内容', 2, 1),
('订单管理员', 'ROLE_ORDER', '管理订单', 3, 1);

-- 设置管理员角色
INSERT INTO `sys_user_role` (`user_id`, `role_id`, `create_time`) 
SELECT u.id, r.id, NOW() 
FROM `sys_user` u, `sys_role` r 
WHERE u.username = 'admin' AND r.code = 'ROLE_ADMIN';

-- 初始化系统配置
INSERT INTO `sys_config` (`config_key`, `config_value`, `config_type`, `description`) VALUES 
('site_name', '非遗文化管理平台', 'TEXT', '网站名称'),
('site_logo', '/static/logo.png', 'TEXT', '网站Logo'),
('site_copyright', 'Copyright © 2023 非遗文化管理平台', 'TEXT', '版权信息'),
('payment_enabled', 'true', 'BOOLEAN', '是否启用支付'),
('payment_default_type', 'MOCK', 'TEXT', '默认支付方式'),
('logistics_enabled', 'true', 'BOOLEAN', '是否启用物流'),
('logistics_default_type', 'MOCK', 'TEXT', '默认物流方式');
```

## 数据库索引优化建议

1. **全文索引**：
   - 在需要进行全文搜索的字段上创建全文索引，如商品名称、非遗项目名称等
   - 使用N-gram分词器支持中文全文检索

2. **联合索引**：
   - 为常用的多条件查询创建联合索引
   - 注意索引字段的顺序，区分度高的字段放在前面

3. **覆盖索引**：
   - 对于频繁查询的字段，考虑使用覆盖索引避免回表

4. **定期维护**：
   - 定期执行`ANALYZE TABLE`更新索引统计信息
   - 定期优化表`OPTIMIZE TABLE`整理碎片

## 分库分表策略

随着数据量增长，可以考虑以下分库分表策略：

1. **垂直分库**：
   - 用户相关表放在用户库
   - 订单相关表放在订单库
   - 商品相关表放在商品库

2. **水平分表**：
   - 大表如订单表、订单商品表可以按照用户ID或时间范围进行分表
   - 日志类表可以按照时间进行分表

3. **读写分离**：
   - 主库负责写操作
   - 从库负责读操作
   - 使用数据库中间件（如ShardingSphere）实现读写分离

-- =============================================
-- Omnitrix AI 智能中台数据库
-- 数据库名: ai_service
-- =============================================

CREATE DATABASE IF NOT EXISTS `ai_service`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;
USE `ai_service`;

-- 1. 对话表
CREATE TABLE `ai_conversation` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`       BIGINT       NOT NULL              COMMENT '用户ID',
    `session_id`    VARCHAR(64)  NOT NULL              COMMENT '会话唯一标识(UUID)',
    `title`         VARCHAR(100) DEFAULT '新对话'       COMMENT '对话标题(AI自动生成)',
    `message_count` INT          DEFAULT 0             COMMENT '消息数量',
    `summary`       TEXT                               COMMENT '对话摘要(LLM自动生成)',
    `status`        TINYINT      DEFAULT 1             COMMENT '0=已删除 1=正常 2=归档',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_id` (`session_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话表';

-- 2. 消息表
CREATE TABLE `ai_message` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `conversation_id` BIGINT       NOT NULL              COMMENT '所属对话ID',
    `session_id`      VARCHAR(64)  NOT NULL              COMMENT '会话标识(冗余加速查询)',
    `role`            VARCHAR(20)  NOT NULL              COMMENT 'system/user/assistant',
    `content`         MEDIUMTEXT   NOT NULL              COMMENT '消息内容',
    `tokens`          INT          DEFAULT 0             COMMENT '消耗token数',
    `model`           VARCHAR(50)                        COMMENT '使用的模型名',
    `sub_agent`       VARCHAR(50)                        COMMENT '处理该消息的子代理编码',
    `latency_ms`      INT                                COMMENT '响应耗时(毫秒)',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_conversation_id` (`conversation_id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI消息表';

-- 3. 知识库表
CREATE TABLE `ai_knowledge_base` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `question`      VARCHAR(500) NOT NULL              COMMENT '问题',
    `answer`        TEXT         NOT NULL              COMMENT '答案',
    `category_id`   BIGINT                             COMMENT '关联非遗分类ID(可空)',
    `keywords`      VARCHAR(500)                       COMMENT '关键词(逗号分隔)',
    `hit_count`     INT          DEFAULT 0             COMMENT '命中次数',
    `status`        TINYINT      DEFAULT 1             COMMENT '0=禁用 1=启用',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_status` (`status`),
    FULLTEXT KEY `ft_question` (`question`, `keywords`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI知识库表';

-- 4. Prompt 配置表
CREATE TABLE `ai_prompt_config` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `prompt_key`    VARCHAR(100) NOT NULL              COMMENT 'Prompt唯一标识',
    `prompt_name`   VARCHAR(100) NOT NULL              COMMENT 'Prompt名称',
    `content`       TEXT         NOT NULL              COMMENT 'Prompt内容',
    `category`      VARCHAR(50)  DEFAULT 'system'      COMMENT 'system/agent/memory',
    `description`   VARCHAR(500)                       COMMENT '说明',
    `status`        TINYINT      DEFAULT 1             COMMENT '0=禁用 1=启用',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_prompt_key` (`prompt_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI Prompt配置表';

-- 5. 追踪日志表
CREATE TABLE `ai_trace_log` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `trace_id`        VARCHAR(64)  NOT NULL              COMMENT '追踪链路ID',
    `conversation_id` BIGINT                             COMMENT '关联对话ID',
    `user_id`         BIGINT                             COMMENT '用户ID',
    `user_query`      TEXT                               COMMENT '用户原始问题',
    `intent`          VARCHAR(50)                        COMMENT '识别到的意图',
    `sub_agent`       VARCHAR(50)                        COMMENT '实际调度的子代理',
    `model`           VARCHAR(50)                        COMMENT '使用的模型',
    `input_tokens`    INT          DEFAULT 0             COMMENT '输入token数',
    `output_tokens`   INT          DEFAULT 0             COMMENT '输出token数',
    `latency_ms`      INT          DEFAULT 0             COMMENT '总耗时(毫秒)',
    `self_score`      DECIMAL(3,1)                       COMMENT 'AI自评分(0.0-10.0)',
    `status`          VARCHAR(20)  DEFAULT 'success'     COMMENT 'success/error/timeout',
    `error_message`   TEXT                               COMMENT '错误信息',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_trace_id` (`trace_id`),
    KEY `idx_conversation_id` (`conversation_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI追踪日志表';

-- 6. 子代理配置表（动态 Agent）
CREATE TABLE `ai_agent_config` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `agent_code`        VARCHAR(50)  NOT NULL              COMMENT '代理编码(唯一标识)',
    `agent_name`        VARCHAR(100) NOT NULL              COMMENT '代理名称',
    `icon`              VARCHAR(100)                       COMMENT '图标',
    `description`       VARCHAR(500)                       COMMENT '功能描述(影响路由选择)',
    `system_prompt`     TEXT                               COMMENT '系统提示词',
    `routing_keywords`  VARCHAR(1000)                      COMMENT '路由关键词(JSON数组)',
    `knowledge_base_id` BIGINT                             COMMENT '绑定的知识库分类ID(可空)',
    `fallback_strategy` VARCHAR(50)  DEFAULT 'general'     COMMENT '降级策略: general/empty/error',
    `sort`              INT          DEFAULT 0             COMMENT '排序号(越小越优先)',
    `status`            TINYINT      DEFAULT 1             COMMENT '0=停用 1=启用',
    `version`           BIGINT       DEFAULT 1             COMMENT '版本号(缓存失效控制)',
    `create_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_code` (`agent_code`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI子代理配置表(动态Agent)';

-- 7. 用户行为表
CREATE TABLE `ai_user_behavior` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`       BIGINT       NOT NULL              COMMENT '用户ID',
    `item_id`       BIGINT                             COMMENT '关联项目/商品ID',
    `item_type`     VARCHAR(30)  NOT NULL              COMMENT 'ich_item/product/activity/post',
    `behavior_type` TINYINT      NOT NULL              COMMENT '1=浏览 2=收藏 3=购买 4=搜索 5=问AI',
    `keywords`      VARCHAR(255)                       COMMENT '搜索/提问关键词',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_item` (`item_type`, `item_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户行为记录表';

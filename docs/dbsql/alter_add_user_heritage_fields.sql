-- 修复 user 表缺少 heritage_flag 和 qualification_id 列的问题
-- MyBatis Mapper 引用了这两列但数据库表中不存在，导致 SQL 报错：
-- Unknown column 'heritage_flag' in 'field list'

ALTER TABLE user_center.user
ADD COLUMN heritage_flag TINYINT(1) DEFAULT 0 COMMENT '传承人标识 0-否 1-是' AFTER last_login_ip,
ADD COLUMN qualification_id BIGINT DEFAULT NULL COMMENT '资格认证ID' AFTER heritage_flag;

-- 修复数据库结构问题
-- 执行前请确认数据库版本

-- =====================
-- 1. ai_trace_span 表：创建表
-- =====================
CREATE TABLE IF NOT EXISTS ai_trace_span (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trace_id VARCHAR(32) NOT NULL COMMENT '关联 ai_trace_log.trace_id',
    span_id VARCHAR(16) NOT NULL COMMENT '本 Span 唯一 ID',
    parent_span_id VARCHAR(16) DEFAULT NULL COMMENT '父 Span ID（根 Span 为 NULL）',
    span_type VARCHAR(20) NOT NULL COMMENT 'REQUEST/AGENT/TOOL/LLM/REPLAN/BLACKBOARD',
    span_name VARCHAR(64) NOT NULL COMMENT 'Span 名称（agent代码/tool名/model名）',
    duration_ms INT DEFAULT 0 COMMENT '耗时毫秒',
    status VARCHAR(10) DEFAULT 'success' COMMENT 'success/error',
    metadata TEXT DEFAULT NULL COMMENT '元数据 JSON',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_trace_id (trace_id),
    INDEX idx_span_type (span_type),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 嵌套追踪 Span';

-- =====================
-- 2. ai_trace_log 表：添加 cost_rmb 列（如果不存在）
-- =====================
-- 检查列是否存在
-- 如果报错 "Duplicate column name"，说明列已存在，跳过即可

-- =====================
-- 3. 验证修复结果
-- =====================
SELECT '=== 验证表结构 ===' AS info;

-- 检查 ai_trace_span 是否存在
SELECT COUNT(*) AS ai_trace_span_exists 
FROM information_schema.TABLES 
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_trace_span';

-- 检查 ai_trace_log 是否有 cost_rmb 列
SELECT COUNT(*) AS cost_rmb_column_exists 
FROM information_schema.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
AND TABLE_NAME = 'ai_trace_log' 
AND COLUMN_NAME = 'cost_rmb';

-- 检查 ai_prompt_config 是否有 is_active 列
SELECT COUNT(*) AS is_active_column_exists 
FROM information_schema.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
AND TABLE_NAME = 'ai_prompt_config' 
AND COLUMN_NAME = 'is_active';

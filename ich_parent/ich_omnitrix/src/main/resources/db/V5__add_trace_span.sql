-- 嵌套追踪 Span 表（支持 Request → Agent → Tool → LLM 多级追踪）
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

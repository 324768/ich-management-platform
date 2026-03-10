-- 每请求成本追踪字段
ALTER TABLE ai_trace_log
    ADD COLUMN cost_rmb DECIMAL(10, 6) DEFAULT NULL COMMENT '本次请求成本（人民币元）' AFTER user_feedback;

CREATE INDEX idx_cost_rmb ON ai_trace_log (cost_rmb);

-- 多维评分字段扩展
ALTER TABLE ai_trace_log ADD COLUMN accuracy_score DECIMAL(3,1) DEFAULT NULL COMMENT '准确性评分 0-10';
ALTER TABLE ai_trace_log ADD COLUMN completeness_score DECIMAL(3,1) DEFAULT NULL COMMENT '完整性评分 0-10';
ALTER TABLE ai_trace_log ADD COLUMN safety_score DECIMAL(3,1) DEFAULT NULL COMMENT '安全性评分 0-10';

-- P3-5: 用户反馈字段 (1=点赞, -1=踩, NULL=未反馈)
ALTER TABLE ai_trace_log ADD COLUMN user_feedback TINYINT DEFAULT NULL COMMENT '用户反馈: 1=点赞, -1=踩' AFTER error_message;

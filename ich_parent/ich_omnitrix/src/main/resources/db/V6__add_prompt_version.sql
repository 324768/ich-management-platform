-- Prompt 版本管理字段
ALTER TABLE ai_prompt_config
    ADD COLUMN version INT DEFAULT 1 COMMENT '版本号' AFTER status,
    ADD COLUMN is_active TINYINT DEFAULT 1 COMMENT '是否激活版本：1=激活, 0=历史' AFTER version;

-- 将现有记录标记为 v1 激活版本
UPDATE ai_prompt_config SET version = 1, is_active = 1 WHERE version IS NULL;

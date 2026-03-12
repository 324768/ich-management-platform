-- ============================================================
-- AI 模块表结构：在 ai_service 库中执行（ich_omnitrix 连接的是 ai_service）
-- 若表当前建在 user_center，请在本库执行此脚本以在 ai_service 中创建/对齐表结构
-- ============================================================
-- 使用前请先：USE ai_service;
-- ============================================================

-- ----------------------------
-- 1. ai_prompt_config（与 AiPromptConfigMapper 一致）
-- ----------------------------
CREATE TABLE IF NOT EXISTS ai_prompt_config (
    id BIGINT NOT NULL AUTO_INCREMENT,
    prompt_key VARCHAR(64) NOT NULL COMMENT 'Prompt键',
    prompt_name VARCHAR(128) DEFAULT NULL COMMENT 'Prompt名称',
    content TEXT NOT NULL COMMENT 'Prompt内容',
    category VARCHAR(64) DEFAULT NULL COMMENT '分类',
    description VARCHAR(255) DEFAULT NULL COMMENT '描述',
    status TINYINT DEFAULT 1 COMMENT '状态：1=启用, 0=禁用',
    version INT DEFAULT 1 COMMENT '版本号',
    is_active TINYINT DEFAULT 1 COMMENT '是否激活版本：1=激活, 0=历史',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_prompt_key (prompt_key),
    KEY idx_prompt_key (prompt_key),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Prompt配置';

-- ----------------------------
-- 2. ai_trace_log（与 AiTraceLog 实体及 Mapper 一致）
-- ----------------------------
CREATE TABLE IF NOT EXISTS ai_trace_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    trace_id VARCHAR(32) NOT NULL COMMENT '追踪ID',
    conversation_id BIGINT DEFAULT NULL COMMENT '会话ID',
    user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
    user_query TEXT COMMENT '用户输入',
    intent VARCHAR(64) DEFAULT NULL COMMENT '意图',
    sub_agent VARCHAR(64) DEFAULT NULL COMMENT '子代理',
    model VARCHAR(64) DEFAULT NULL COMMENT '模型名称',
    input_tokens INT DEFAULT NULL COMMENT '输入Token数',
    output_tokens INT DEFAULT NULL COMMENT '输出Token数',
    latency_ms INT DEFAULT NULL COMMENT '耗时毫秒',
    self_score DECIMAL(3,1) DEFAULT NULL COMMENT '自评总分 0-10',
    accuracy_score DECIMAL(3,1) DEFAULT NULL COMMENT '准确性评分 0-10',
    completeness_score DECIMAL(3,1) DEFAULT NULL COMMENT '完整性评分 0-10',
    safety_score DECIMAL(3,1) DEFAULT NULL COMMENT '安全性评分 0-10',
    status VARCHAR(10) DEFAULT 'success' COMMENT 'success/error',
    error_message TEXT DEFAULT NULL COMMENT '错误信息',
    user_feedback TINYINT DEFAULT NULL COMMENT '用户反馈: 1=点赞, -1=踩',
    cost_rmb DECIMAL(10,6) DEFAULT NULL COMMENT '本次请求成本（人民币元）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_trace_id (trace_id),
    KEY idx_user_id (user_id),
    KEY idx_conversation_id (conversation_id),
    KEY idx_create_time (create_time),
    KEY idx_cost_rmb (cost_rmb)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI对话追踪日志';

-- ----------------------------
-- 3. ai_trace_span
-- ----------------------------
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 嵌套追踪 Span';

-- ----------------------------
-- 4. ai_skill_config
-- ----------------------------
CREATE TABLE IF NOT EXISTS ai_skill_config (
    skill_id VARCHAR(64) NOT NULL PRIMARY KEY COMMENT 'Skill唯一标识',
    skill_name VARCHAR(128) NOT NULL COMMENT 'Skill名称',
    description TEXT COMMENT 'Skill描述',
    system_prompt TEXT COMMENT 'Skill的系统Prompt',
    keywords VARCHAR(512) DEFAULT NULL COMMENT '关键词，多个用逗号分隔',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
    display_order INT DEFAULT 0 COMMENT '显示顺序',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    updated_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    version INT DEFAULT 0 COMMENT '版本号，用于缓存刷新'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI技能配置表';

-- 若 ai_skill_config 为空，可插入默认数据（按需执行）
-- INSERT INTO ai_skill_config (skill_id, skill_name, description, keywords, enabled, display_order) VALUES
-- ('heritage_master', '非遗文化大师', '...', '非遗,传承人,...', 1, 1),
-- ...

-- 若 ai_prompt_config 需要默认主脑 Prompt（按需执行）
-- INSERT INTO ai_prompt_config (prompt_key, prompt_name, content, category, description, status, version, is_active)
-- VALUES ('master_brain_system', '主系统Prompt', '你是 omnitrix AI 助手...', 'system', '主系统Prompt', 1, 1, 1);

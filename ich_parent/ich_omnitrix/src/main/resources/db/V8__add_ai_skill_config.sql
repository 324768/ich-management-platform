-- Skill配置表：用于管理员动态开启/关闭Skills
-- 版本: V8
-- 创建时间: 2026-03-11

CREATE TABLE IF NOT EXISTS ai_skill_config (
    skill_id VARCHAR(64) NOT NULL PRIMARY KEY COMMENT 'Skill唯一标识',
    skill_name VARCHAR(128) NOT NULL COMMENT 'Skill名称',
    description TEXT COMMENT 'Skill描述',
    system_prompt TEXT COMMENT 'Skill的系统Prompt',
    keywords VARCHAR(512) COMMENT '关键词，多个用逗号分隔',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
    display_order INT DEFAULT 0 COMMENT '显示顺序',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    updated_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    version INT DEFAULT 0 COMMENT '版本号，用于缓存刷新'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI技能配置表';

-- 初始化默认Skills
INSERT INTO ai_skill_config (skill_id, skill_name, description, keywords, enabled, display_order) VALUES
('heritage_master', '非遗文化大师', '回答非遗项目、传承人、传统文化、历史典故等问题', '非遗,传承人,传统文化,昆曲,京剧,剪纸,陶瓷,刺绣,武术,中医,节日,民俗,历史,文化', 1, 1),
('shopping_advisor', '购物顾问', '回答商品咨询、推荐、选购建议等问题，了解文创产品特点', '商品,购买,推荐,选购,价格,材质,做工,文创,商城,礼物', 1, 2),
('customer_service', '客服话术师', '处理用户投诉、售后问题、退换货等，态度耐心亲切', '投诉,售后,退货,换货,退款,质量问题,客服,物流', 1, 3),
('knowledge_expert', '知识百科达人', '回答平台知识库相关问题，基于知识库内容准确回答', '知识库,FAQ,常见问题,帮助,如何使用,怎么操作,功能,会员,积分', 1, 4),
('recommend_expert', '推荐解读者', '解读个性化推荐逻辑，分析用户兴趣偏好', '推荐,为什么推荐,猜你喜欢,兴趣,偏好,个性化', 1, 5),
('security_audit', '安全审核员', '内容安全审核，过滤敏感信息，确保合规', '审核,违规,敏感,安全,内容审查', 0, 6),
('quality_evaluator', '质量评估师', '评估AI回答质量，给出改进建议', '评估,质量,回答,改进,优化,评分', 0, 7);

-- 创建索引
CREATE INDEX idx_skill_enabled ON ai_skill_config (enabled);
CREATE INDEX idx_skill_order ON ai_skill_config (display_order);

-- 用户资格认证表 (传承人/公司资格申请)
-- 数据库: user_center
USE user_center;

CREATE TABLE IF NOT EXISTS ich_user_qualification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '申请用户ID',
    user_name VARCHAR(100) COMMENT '用户姓名',
    user_phone VARCHAR(20) COMMENT '联系电话',
    qualification_type TINYINT NOT NULL DEFAULT 1 COMMENT '资格类型: 1=传承人 2=公司/组织',
    title VARCHAR(200) COMMENT '传承人称号/公司名称',
    description TEXT COMMENT '申请说明',
    materials TEXT COMMENT '证明材料(JSON数组，存储文件URL)',
    id_card_front VARCHAR(500) COMMENT '身份证正面',
    id_card_back VARCHAR(500) COMMENT '身份证背面',
    certificate_images TEXT COMMENT '资格证书图片(JSON数组)',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '审核状态: 0=待审核 1=已通过 2=已拒绝',
    reject_reason VARCHAR(500) COMMENT '拒绝原因',
    reviewer_id BIGINT COMMENT '审核人ID',
    review_time DATETIME COMMENT '审核时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT DEFAULT 0,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户资格认证申请表';

-- 在用户表添加传承标志字段
ALTER TABLE user ADD COLUMN IF NOT EXISTS heritage_flag TINYINT DEFAULT 0 COMMENT '传承标志: 0=普通用户 1=已认证传承人/公司';
ALTER TABLE user ADD COLUMN IF NOT EXISTS qualification_id BIGINT DEFAULT NULL COMMENT '关联资格认证ID';

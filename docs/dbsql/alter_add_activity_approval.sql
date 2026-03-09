-- 活动表添加审批状态字段
-- approval_status: 0=无需审批(直接发布) 1=待审批 2=审批通过 3=审批拒绝
-- reject_reason: 审批拒绝原因
USE content_center;

ALTER TABLE ich_activity ADD COLUMN approval_status INT DEFAULT 0 COMMENT '审批状态: 0无需审批 1待审批 2审批通过 3审批拒绝';
ALTER TABLE ich_activity ADD COLUMN reject_reason VARCHAR(500) DEFAULT NULL COMMENT '审批拒绝原因';
ALTER TABLE ich_activity ADD COLUMN reviewer_id BIGINT DEFAULT NULL COMMENT '审批人ID';
ALTER TABLE ich_activity ADD COLUMN review_time DATETIME DEFAULT NULL COMMENT '审批时间';
ALTER TABLE ich_activity ADD COLUMN publisher_user_id BIGINT DEFAULT NULL COMMENT '发布者用户ID';

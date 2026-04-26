-- V1: 给文件表增加分类字段
-- 用于知识库文档分类管理，支持 AI 按分类检索

ALTER TABLE t_file_info
    ADD COLUMN IF NOT EXISTS category VARCHAR(64);

-- 加速单字段分类查询（按分类浏览文档）
CREATE INDEX IF NOT EXISTS idx_file_info_category
    ON t_file_info (category);

-- 加速用户 + 分类联合查询（最常见场景）
CREATE INDEX IF NOT EXISTS idx_file_info_user_category
    ON t_file_info (user_id, category);

-- V10: 兜底补齐 t_file_info.category
-- 背景：老库若先用初始化 SQL 建库、后启用 Flyway，baseline-version=1 会导致 V1 被基线跳过
-- 于是代码已映射 category 字段，但数据库实际缺列，查询/上传知识库文件时会报错

ALTER TABLE t_file_info
    ADD COLUMN IF NOT EXISTS category VARCHAR(64);

COMMENT ON COLUMN t_file_info.category IS '知识库分类';

CREATE INDEX IF NOT EXISTS idx_file_info_category
    ON t_file_info (category);

CREATE INDEX IF NOT EXISTS idx_file_info_user_category
    ON t_file_info (user_id, category);

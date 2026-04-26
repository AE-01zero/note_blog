-- V9: 清理已失效的 t_file_info AI 检索残留，当前运行时统一使用 documents 表
-- 仅移除旧索引、触发器、函数与废弃列，不影响现有 documents 向量检索链路

DROP TRIGGER IF EXISTS tsvector_update_trigger ON t_file_info;
DROP FUNCTION IF EXISTS update_file_tsvector();
DROP INDEX IF EXISTS idx_file_content_tsv;
DROP INDEX IF EXISTS idx_file_embedding;

ALTER TABLE t_file_info
    DROP COLUMN IF EXISTS content_tsv,
    DROP COLUMN IF EXISTS embedding;

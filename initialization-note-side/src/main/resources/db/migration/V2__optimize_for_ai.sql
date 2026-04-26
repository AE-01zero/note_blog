-- V2: AI 检索优化
-- 启用向量扩展（pgvector 镜像已包含，显式声明确保可用）
CREATE EXTENSION IF NOT EXISTS vector;

-- 添加 embedding 向量字段（用于 AI 语义搜索，1536维对应 text-embedding-v3）
ALTER TABLE t_file_info
    ADD COLUMN IF NOT EXISTS embedding vector(1536);

-- 添加全文检索字段
ALTER TABLE t_file_info
    ADD COLUMN IF NOT EXISTS content_tsv tsvector;

-- GIN 索引（关键词全文搜索）
CREATE INDEX IF NOT EXISTS idx_file_content_tsv
    ON t_file_info USING GIN(content_tsv);

-- 向量相似度索引（语义搜索，lists=100 适合百万级数据量以内）
CREATE INDEX IF NOT EXISTS idx_file_embedding
    ON t_file_info USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

-- 自动维护 tsvector 字段的触发器函数
CREATE OR REPLACE FUNCTION update_file_tsvector()
    RETURNS trigger AS $$
BEGIN
    NEW.content_tsv :=
        to_tsvector('simple', coalesce(NEW.content, ''));
    RETURN NEW;
END
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tsvector_update_trigger ON t_file_info;

CREATE TRIGGER tsvector_update_trigger
    BEFORE INSERT OR UPDATE ON t_file_info
    FOR EACH ROW EXECUTE FUNCTION update_file_tsvector();

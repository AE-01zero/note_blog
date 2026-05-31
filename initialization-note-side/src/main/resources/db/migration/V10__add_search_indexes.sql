-- ============================================================
-- V10: 检索性能优化 — 全文trigram索引
-- ============================================================

-- 1. 启用 pg_trgm 扩展（trigram 模糊匹配，加速 ILIKE 全表扫描）
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 2. text 列 GIN trigram 索引 — 加速 ILIKE '%keyword%' 关键词检索
--    将全表扫描替换为索引扫描，同样的查询结果，速度提升 10-100 倍
CREATE INDEX IF NOT EXISTS idx_documents_text_trgm
    ON documents USING gin (text gin_trgm_ops);

-- 注意：embedding 列 HNSW 索引不在此处创建，因为 documents 表由 LangChain4j
-- 的 PgVectorEmbeddingStore 自动管理，其 embedding 列类型、维度、索引策略由
-- LangChain4j 版本决定。LangChain4j 内部使用 IVFFlat 索引自行管理向量检索。

-- V5: 给笔记本和共享知识库增加分类字段，复用博客分类体系

ALTER TABLE t_notebook
    ADD COLUMN IF NOT EXISTS category_id BIGINT;

ALTER TABLE t_shared_knowledge_base
    ADD COLUMN IF NOT EXISTS category_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_notebook_category
    ON t_notebook (category_id);

CREATE INDEX IF NOT EXISTS idx_shared_kb_category
    ON t_shared_knowledge_base (category_id);

-- 知识库分类字段：添加 category 列到文件信息表
ALTER TABLE t_file_info ADD COLUMN IF NOT EXISTS category VARCHAR(64);

-- 创建索引加速分类查询
CREATE INDEX IF NOT EXISTS idx_file_info_category ON t_file_info(category);
CREATE INDEX IF NOT EXISTS idx_file_info_user_category ON t_file_info(user_id, category);

CREATE TABLE IF NOT EXISTS public.t_register_setting
(
    id               bigserial primary key,
    register_enabled boolean   not null default false,
    invite_code      varchar(64),
    create_time      timestamp default CURRENT_TIMESTAMP,
    update_time      timestamp default CURRENT_TIMESTAMP
);

INSERT INTO public.t_register_setting (id, register_enabled, invite_code, create_time, update_time)
SELECT 1, false, null, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM public.t_register_setting WHERE id = 1
);

UPDATE public.t_user
SET role_type = CASE WHEN phone = '13800000000' THEN 1 ELSE 2 END,
    update_time = CURRENT_TIMESTAMP;

INSERT INTO public.t_user (phone, password, role_type, username, status, create_time, update_time)
SELECT '13800000000',
       '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9',
       1,
       'admin',
       1,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM public.t_user WHERE phone = '13800000000'
);

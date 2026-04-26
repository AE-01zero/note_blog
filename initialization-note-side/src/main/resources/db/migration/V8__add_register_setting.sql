CREATE TABLE IF NOT EXISTS public.t_register_setting
(
    id               bigserial PRIMARY KEY,
    register_enabled boolean   NOT NULL DEFAULT false,
    invite_code      varchar(64),
    create_time      timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE public.t_register_setting
    OWNER TO postgres;

INSERT INTO public.t_register_setting (id, register_enabled, invite_code, create_time, update_time)
SELECT 1, false, null, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM public.t_register_setting WHERE id = 1
);

SELECT setval(
               pg_get_serial_sequence('public.t_register_setting', 'id'),
               GREATEST((SELECT COALESCE(MAX(id), 1) FROM public.t_register_setting), 1),
               true
       );

UPDATE public.t_user
SET role_type   = CASE WHEN phone = '13800000000' THEN 1 ELSE 2 END,
    update_time = CURRENT_TIMESTAMP
WHERE role_type IS DISTINCT FROM CASE WHEN phone = '13800000000' THEN 1 ELSE 2 END;

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

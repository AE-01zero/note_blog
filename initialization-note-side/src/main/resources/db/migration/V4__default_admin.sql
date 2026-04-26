-- V4: 默认管理员账号
-- 账号: 13800000000  密码: admin123 (MD5加密)
INSERT INTO t_user (phone, password, role_type, username, status, create_time, update_time)
SELECT '13800000000',
       '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9',
       2,
       'admin',
       1,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM t_user WHERE phone = '13800000000'
);

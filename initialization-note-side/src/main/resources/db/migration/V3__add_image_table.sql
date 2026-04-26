-- V3: 图片管理表
CREATE TABLE IF NOT EXISTS t_file_image (
    id          BIGSERIAL PRIMARY KEY,
    file_id     BIGINT,
    user_id     BIGINT,
    category    VARCHAR(64),
    file_path   TEXT,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_image_file_id ON t_file_image(file_id);
CREATE INDEX IF NOT EXISTS idx_image_user_id ON t_file_image(user_id);

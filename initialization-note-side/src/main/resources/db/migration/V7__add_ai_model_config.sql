CREATE TABLE t_ai_model_config (
    id BIGSERIAL PRIMARY KEY,
    model_type VARCHAR(32) NOT NULL UNIQUE,
    base_url VARCHAR(512) NOT NULL,
    api_key VARCHAR(256) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    log_requests BOOLEAN DEFAULT TRUE,
    log_responses BOOLEAN DEFAULT TRUE,
    max_segments_per_batch INT DEFAULT 10,
    create_time TIMESTAMP DEFAULT NOW(),
    update_time TIMESTAMP DEFAULT NOW()
);

INSERT INTO t_ai_model_config(model_type, base_url, api_key, model_name, log_requests, log_responses, max_segments_per_batch) VALUES
    ('chat',      'https://dashscope.aliyuncs.com/compatible-mode/v1', 'sk-xxxxxx', 'qwen-plus', true, true, 10),
    ('streaming', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 'sk-xxxxxx', 'qwen-plus', true, true, 10),
    ('embedding', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 'sk-xxxxxx', 'text-embedding-v3', true, true, 10);

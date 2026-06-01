-- ============================================================
-- V11: AI安全分析历史记录 + 反编译文件管理
-- 持久化所有分析模块(APK/SO/Protocol/APK逆向)的结果
-- ============================================================

-- 1. 分析历史记录表
CREATE TABLE IF NOT EXISTS t_analysis_history (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    module_type     VARCHAR(32) NOT NULL,          -- APK / APK_REVERSE / SO / PROTOCOL
    file_name       VARCHAR(512),
    file_size       BIGINT DEFAULT 0,
    work_dir        VARCHAR(1024),                 -- 反编译输出目录路径(仅APK_REVERSE)
    analysis_result JSONB,                         -- 完整分析结果(Map序列化为JSON)
    verdict         VARCHAR(64),                   -- MALWARE / SUSPICIOUS / BENIGN / CLEAN / ENCRYPTED
    risk_level      VARCHAR(16),                   -- CRITICAL / HIGH / MEDIUM / LOW / INFO
    summary         TEXT,                          -- 简短摘要
    extra_info      JSONB,                         -- 附加信息(如包名/架构/协议类型等)
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_analysis_history_user_id ON t_analysis_history(user_id);
CREATE INDEX IF NOT EXISTS idx_analysis_history_module ON t_analysis_history(user_id, module_type);
CREATE INDEX IF NOT EXISTS idx_analysis_history_created ON t_analysis_history(user_id, created_at DESC);

COMMENT ON TABLE t_analysis_history IS 'AI安全分析历史记录';
COMMENT ON COLUMN t_analysis_history.module_type IS '分析模块: APK/APK_REVERSE/SO/PROTOCOL';
COMMENT ON COLUMN t_analysis_history.work_dir IS '反编译输出目录，仅APK_REVERSE模块使用';

-- 2. 反编译文件管理表
CREATE TABLE IF NOT EXISTS t_decompile_record (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    analysis_history_id BIGINT,                    -- 关联的分析历史记录ID
    apk_file_name       VARCHAR(512) NOT NULL,     -- 原始APK文件名
    package_name        VARCHAR(256),              -- APK包名
    work_dir            VARCHAR(1024) NOT NULL,    -- 反编译输出目录绝对路径
    file_count          INT DEFAULT 0,             -- 输出文件数
    total_size          BIGINT DEFAULT 0,          -- 输出总大小(字节)
    status              VARCHAR(16) DEFAULT 'ACTIVE', -- ACTIVE / DELETED
    error_msg           TEXT,                      -- 反编译错误信息
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_decompile_record_user_id ON t_decompile_record(user_id);
CREATE INDEX IF NOT EXISTS idx_decompile_record_status ON t_decompile_record(user_id, status);

COMMENT ON TABLE t_decompile_record IS 'APK反编译文件管理';
COMMENT ON COLUMN t_decompile_record.work_dir IS '反编译输出目录绝对路径';
COMMENT ON COLUMN t_decompile_record.status IS 'ACTIVE-可用 / DELETED-已删除';

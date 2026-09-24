-- 大乐透预测系统：PostgreSQL 完整建表与幂等初始化数据脚本
--
-- 用途：新建数据库或手工初始化开发环境。
-- 注意：已由 Flyway 管理的数据库不要手工创建 flyway_schema_history；
--       已有业务表但没有 Flyway history 时，应使用 Flyway baseline/baselineOnMigrate 处理。
--
-- 样例管理员：admin / admin；首次登录后必须修改密码。
-- 生产环境请删除样例数据，并通过 BOOTSTRAP_ADMIN_PASSWORD 提供强密码。

BEGIN;

-- 用于在 SQL 中生成与 Spring BCryptPasswordEncoder 兼容的密码哈希。
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1. 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(40) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    approved_by BIGINT,
    approved_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_sys_user_role CHECK (role IN ('ADMIN', 'USER')),
    CONSTRAINT ck_sys_user_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'DISABLED')),
    CONSTRAINT fk_sys_user_approved_by FOREIGN KEY (approved_by) REFERENCES sys_user (id)
);

-- 2. 大乐透开奖主表
CREATE TABLE IF NOT EXISTS dlt_draw (
    id BIGSERIAL PRIMARY KEY,
    issue_no VARCHAR(32) NOT NULL UNIQUE,
    draw_date DATE NOT NULL,
    front_numbers VARCHAR(32) NOT NULL,
    back_numbers VARCHAR(16) NOT NULL,
    source_url TEXT,
    source_payload TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'VALID',
    vector_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    vector_error TEXT,
    vector_attempted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_dlt_draw_status CHECK (status IN ('VALID', 'INVALID', 'PENDING')),
    CONSTRAINT ck_dlt_draw_vector_status CHECK (vector_status IN ('PENDING', 'READY', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_dlt_draw_date ON dlt_draw (draw_date);

-- 3. 开奖号码明细表
CREATE TABLE IF NOT EXISTS dlt_draw_number (
    id BIGSERIAL PRIMARY KEY,
    draw_id BIGINT NOT NULL REFERENCES dlt_draw (id) ON DELETE CASCADE,
    zone VARCHAR(8) NOT NULL,
    position SMALLINT NOT NULL,
    number SMALLINT NOT NULL,
    CONSTRAINT uq_dlt_draw_number_position UNIQUE (draw_id, zone, position),
    CONSTRAINT ck_dlt_draw_number_zone CHECK (zone IN ('FRONT', 'BACK')),
    CONSTRAINT ck_dlt_draw_number_position CHECK (
        (zone = 'FRONT' AND position BETWEEN 1 AND 5)
        OR (zone = 'BACK' AND position BETWEEN 1 AND 2)
    ),
    CONSTRAINT ck_dlt_draw_number_range CHECK (
        (zone = 'FRONT' AND number BETWEEN 1 AND 35)
        OR (zone = 'BACK' AND number BETWEEN 1 AND 12)
    )
);

CREATE INDEX IF NOT EXISTS idx_dlt_draw_number_draw_id ON dlt_draw_number (draw_id);

-- 4. 采集任务日志表
CREATE TABLE IF NOT EXISTS crawler_job_log (
    id BIGSERIAL PRIMARY KEY,
    job_type VARCHAR(64) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE,
    result VARCHAR(16) NOT NULL,
    fetched_count INTEGER NOT NULL DEFAULT 0,
    inserted_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    CONSTRAINT ck_crawler_job_log_result CHECK (result IN ('SUCCESS', 'FAILED', 'PARTIAL')),
    CONSTRAINT ck_crawler_job_log_counts CHECK (fetched_count >= 0 AND inserted_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_crawler_job_log_started_at ON crawler_job_log (started_at DESC);

-- 5. 参考号码生成记录表
CREATE TABLE IF NOT EXISTS prediction_record (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES sys_user (id) ON DELETE SET NULL,
    input_type VARCHAR(32) NOT NULL,
    input_draw_id BIGINT REFERENCES dlt_draw (id) ON DELETE SET NULL,
    front_numbers VARCHAR(32) NOT NULL,
    back_numbers VARCHAR(16) NOT NULL,
    similar_draw_ids TEXT,
    model_version VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_prediction_record_input_type CHECK (input_type IN ('LATEST_DRAW', 'CUSTOM_NUMBERS'))
);

CREATE INDEX IF NOT EXISTS idx_prediction_record_user_created_at
    ON prediction_record (user_id, created_at DESC);

-- 6. 预测模型版本表
CREATE TABLE IF NOT EXISTS prediction_model (
    id BIGSERIAL PRIMARY KEY,
    model_type VARCHAR(32) NOT NULL,
    model_version VARCHAR(64) NOT NULL,
    algorithm_version VARCHAR(64) NOT NULL,
    batch_version VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    config_json TEXT NOT NULL,
    feature_window INTEGER NOT NULL,
    draw_count INTEGER NOT NULL,
    sample_count INTEGER NOT NULL,
    trained_through_draw_id BIGINT NOT NULL REFERENCES dlt_draw (id),
    trained_through_issue_no VARCHAR(32) NOT NULL,
    trained_through_date DATE NOT NULL,
    trained_at TIMESTAMP WITH TIME ZONE NOT NULL,
    front_log_loss DOUBLE PRECISION NOT NULL,
    back_log_loss DOUBLE PRECISION NOT NULL,
    front_mean_hits DOUBLE PRECISION NOT NULL,
    back_mean_hits DOUBLE PRECISION NOT NULL,
    front_baseline_log_loss DOUBLE PRECISION NOT NULL,
    back_baseline_log_loss DOUBLE PRECISION NOT NULL,
    front_baseline_hits DOUBLE PRECISION NOT NULL,
    back_baseline_hits DOUBLE PRECISION NOT NULL,
    front_beats_baseline BOOLEAN NOT NULL,
    back_beats_baseline BOOLEAN NOT NULL,
    CONSTRAINT uq_prediction_model_version UNIQUE (model_type, model_version),
    CONSTRAINT ck_prediction_model_type CHECK (
        model_type IN ('LOGISTIC_REGRESSION', 'VECTOR_TRANSITION', 'DYNAMIC_ENSEMBLE', 'DECAYED_FREQUENCY')
    ),
    CONSTRAINT ck_prediction_model_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX IF NOT EXISTS idx_prediction_model_batch ON prediction_model (batch_version);
CREATE UNIQUE INDEX IF NOT EXISTS uq_prediction_model_active_type
    ON prediction_model (model_type)
    WHERE status = 'ACTIVE';

-- ==============================
-- 初始化数据（均可重复执行）
-- ==============================

-- 默认管理员：admin / admin（仅限开发环境）
INSERT INTO sys_user (
    username,
    password_hash,
    role,
    status,
    must_change_password,
    created_at
)
VALUES (
    'admin',
    crypt('admin', gen_salt('bf', 10)),
    'ADMIN',
    'APPROVED',
    TRUE,
    TIMESTAMP WITH TIME ZONE '2025-01-01 00:00:00+08'
)
ON CONFLICT (username) DO NOTHING;

-- 三期可用于前端展示与推荐功能验证的样例开奖数据。
INSERT INTO dlt_draw (
    issue_no,
    draw_date,
    front_numbers,
    back_numbers,
    source_url,
    source_payload,
    status,
    vector_status,
    created_at
)
VALUES
    (
        '2025001',
        DATE '2025-01-01',
        '01,06,12,20,35',
        '03,11',
        'manual://seed/2025001',
        '{"seed":true,"issueNo":"2025001"}',
        'VALID',
        'PENDING',
        TIMESTAMP WITH TIME ZONE '2025-01-01 22:30:00+08'
    ),
    (
        '2025002',
        DATE '2025-01-06',
        '02,07,14,21,32',
        '01,09',
        'manual://seed/2025002',
        '{"seed":true,"issueNo":"2025002"}',
        'VALID',
        'PENDING',
        TIMESTAMP WITH TIME ZONE '2025-01-06 22:30:00+08'
    ),
    (
        '2025003',
        DATE '2025-01-11',
        '04,08,18,22,30',
        '05,12',
        'manual://seed/2025003',
        '{"seed":true,"issueNo":"2025003"}',
        'VALID',
        'PENDING',
        TIMESTAMP WITH TIME ZONE '2025-01-11 22:30:00+08'
    )
ON CONFLICT (issue_no) DO NOTHING;

-- 对应的 21 条前区/后区号码明细。
WITH seed_numbers (issue_no, zone, position, number) AS (
    VALUES
        ('2025001', 'FRONT', 1, 1), ('2025001', 'FRONT', 2, 6),
        ('2025001', 'FRONT', 3, 12), ('2025001', 'FRONT', 4, 20),
        ('2025001', 'FRONT', 5, 35), ('2025001', 'BACK', 1, 3),
        ('2025001', 'BACK', 2, 11),
        ('2025002', 'FRONT', 1, 2), ('2025002', 'FRONT', 2, 7),
        ('2025002', 'FRONT', 3, 14), ('2025002', 'FRONT', 4, 21),
        ('2025002', 'FRONT', 5, 32), ('2025002', 'BACK', 1, 1),
        ('2025002', 'BACK', 2, 9),
        ('2025003', 'FRONT', 1, 4), ('2025003', 'FRONT', 2, 8),
        ('2025003', 'FRONT', 3, 18), ('2025003', 'FRONT', 4, 22),
        ('2025003', 'FRONT', 5, 30), ('2025003', 'BACK', 1, 5),
        ('2025003', 'BACK', 2, 12)
)
INSERT INTO dlt_draw_number (draw_id, zone, position, number)
SELECT draw.id, seed.zone, seed.position, seed.number
FROM seed_numbers AS seed
JOIN dlt_draw AS draw ON draw.issue_no = seed.issue_no
ON CONFLICT (draw_id, zone, position)
DO UPDATE SET number = EXCLUDED.number;

-- 一条成功同步日志样例。
INSERT INTO crawler_job_log (
    job_type,
    started_at,
    finished_at,
    result,
    fetched_count,
    inserted_count,
    error_message
)
SELECT
    'DLT_MANUAL_SEED',
    TIMESTAMP WITH TIME ZONE '2025-01-11 22:30:00+08',
    TIMESTAMP WITH TIME ZONE '2025-01-11 22:30:01+08',
    'SUCCESS',
    3,
    3,
    NULL
WHERE NOT EXISTS (
    SELECT 1
    FROM crawler_job_log
    WHERE job_type = 'DLT_MANUAL_SEED'
      AND started_at = TIMESTAMP WITH TIME ZONE '2025-01-11 22:30:00+08'
);

-- 一条基于最新样例开奖生成的参考号码记录。
INSERT INTO prediction_record (
    user_id,
    input_type,
    input_draw_id,
    front_numbers,
    back_numbers,
    similar_draw_ids,
    model_version,
    created_at
)
SELECT
    user_account.id,
    'LATEST_DRAW',
    draw.id,
    '01,06,12,20,35',
    '03,11',
    '1,2',
    'mvp-1.0',
    TIMESTAMP WITH TIME ZONE '2025-01-12 09:00:00+08'
FROM sys_user AS user_account
JOIN dlt_draw AS draw ON draw.issue_no = '2025003'
WHERE user_account.username = 'admin'
  AND NOT EXISTS (
      SELECT 1
      FROM prediction_record
      WHERE input_type = 'LATEST_DRAW'
        AND input_draw_id = draw.id
        AND model_version = 'mvp-1.0'
        AND created_at = TIMESTAMP WITH TIME ZONE '2025-01-12 09:00:00+08'
  );

COMMIT;

-- 导入后的检查语句：
-- SELECT 'sys_user' AS table_name, COUNT(*) FROM sys_user
-- UNION ALL SELECT 'dlt_draw', COUNT(*) FROM dlt_draw
-- UNION ALL SELECT 'dlt_draw_number', COUNT(*) FROM dlt_draw_number
-- UNION ALL SELECT 'crawler_job_log', COUNT(*) FROM crawler_job_log
-- UNION ALL SELECT 'prediction_record', COUNT(*) FROM prediction_record;

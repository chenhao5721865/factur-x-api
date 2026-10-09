-- 付费墙：订阅计划字段 + 追加式用量日志表
ALTER TABLE api_keys ADD COLUMN plan VARCHAR(16) NOT NULL DEFAULT 'free';
ALTER TABLE api_keys ADD COLUMN plan_expires_at TIMESTAMPTZ;
ALTER TABLE api_keys ADD COLUMN ls_subscription_id VARCHAR(64);

CREATE TABLE api_usage (
    id         BIGSERIAL PRIMARY KEY,
    key_id     BIGINT NOT NULL REFERENCES api_keys(id),
    endpoint   VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_api_usage_key_created ON api_usage(key_id, created_at);

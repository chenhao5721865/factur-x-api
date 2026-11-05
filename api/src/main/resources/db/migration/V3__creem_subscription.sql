ALTER TABLE api_keys ADD COLUMN creem_subscription_id VARCHAR(64);
CREATE INDEX idx_api_keys_creem_sub ON api_keys(creem_subscription_id);

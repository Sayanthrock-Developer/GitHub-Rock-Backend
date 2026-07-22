CREATE TABLE IF NOT EXISTS webhook_deliveries (
    delivery_id VARCHAR(100) PRIMARY KEY,
    event_name VARCHAR(100) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_received_at
    ON webhook_deliveries(received_at);

CREATE TABLE IF NOT EXISTS feature_flags (
    key VARCHAR(100) PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS announcements (
    id UUID PRIMARY KEY,
    title TEXT NOT NULL,
    message TEXT NOT NULL,
    severity VARCHAR(20) NOT NULL,
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    dismissible BOOLEAN NOT NULL DEFAULT TRUE,
    action_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Flyway migration: V1__create_subscribers.sql
CREATE TABLE subscribers (
    email      VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

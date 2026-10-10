# --- !Ups

-- Системное

CREATE TABLE IF NOT EXISTS settings (
    key             VARCHAR(100) PRIMARY KEY,
    value           CLOB,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- V7: Системные таблицы

CREATE TABLE IF NOT EXISTS settings (
    setting_key             VARCHAR(100) PRIMARY KEY,
    setting_value           CLOB,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
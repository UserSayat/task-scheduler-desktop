-- V5: Таблицы синхронизации
-- Зависимости: V1 (users)

-- Состояние полей (для field-level LWW)
CREATE TABLE IF NOT EXISTS field_versions (
    id              UUID PRIMARY KEY,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    entity_type     VARCHAR(50) NOT NULL,
    entity_id       UUID NOT NULL,
    field_name      VARCHAR(100) NOT NULL,
    updated_at      TIMESTAMP NOT NULL,
    updated_by      UUID NOT NULL,
    device_id       VARCHAR(100),
    CONSTRAINT uq_field_versions UNIQUE (entity_type, entity_id, field_name),
    CONSTRAINT fk_field_versions_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_field_versions_entity ON field_versions(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_field_versions_updated ON field_versions(updated_at);

-- Журнал изменений
CREATE TABLE IF NOT EXISTS sync_log (
    id              UUID PRIMARY KEY,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    entity_type     VARCHAR(50) NOT NULL,
    entity_id       UUID NOT NULL,
    operation       VARCHAR(10) NOT NULL,
    user_id         UUID NOT NULL,
    device_id       VARCHAR(100) NOT NULL,
    timestamp       TIMESTAMP NOT NULL,
    synced          BOOLEAN NOT NULL DEFAULT FALSE,
    server_timestamp TIMESTAMP,
    CONSTRAINT fk_sync_log_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_sync_log_synced ON sync_log(synced);
CREATE INDEX IF NOT EXISTS idx_sync_log_timestamp ON sync_log(timestamp);
CREATE INDEX IF NOT EXISTS idx_sync_log_server_timestamp ON sync_log(server_timestamp);
CREATE INDEX IF NOT EXISTS idx_sync_log_entity ON sync_log(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_sync_log_user ON sync_log(user_id);
CREATE INDEX IF NOT EXISTS idx_sync_log_device ON sync_log(device_id);

-- Состояние синхронизации
CREATE TABLE IF NOT EXISTS sync_state (
    state_key             VARCHAR(50) PRIMARY KEY,
    state_value           CLOB
);

-- Ошибки синхронизации
CREATE TABLE IF NOT EXISTS sync_errors (
    id              UUID PRIMARY KEY,
    entity_type     VARCHAR(50) NOT NULL,
    entity_id       UUID NOT NULL,
    operation       VARCHAR(10) NOT NULL,
    error_code      VARCHAR(50) NOT NULL,
    error_message   CLOB,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at     TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sync_errors_created ON sync_errors(created_at);
CREATE INDEX IF NOT EXISTS idx_sync_errors_resolved ON sync_errors(resolved_at);
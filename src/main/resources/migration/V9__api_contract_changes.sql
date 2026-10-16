-- V9: Изменения схемы под контракт API (API.md бэкенда, раздел 11)
-- Зависимости: V1 (users, roles), V4 (tags), V6 (notifications), V7 (settings)

-- Хеши паролей других пользователей клиенту знать нельзя
ALTER TABLE users DROP COLUMN IF EXISTS password_hash;

-- Мягкое удаление тегов
ALTER TABLE tags ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_tags_deleted_at ON tags(deleted_at);

-- Очередь отметок «прочитано», сделанных офлайн
CREATE TABLE IF NOT EXISTS pending_notification_reads (
    notification_id UUID PRIMARY KEY,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pending_reads_notification FOREIGN KEY (notification_id)
        REFERENCES notifications(id) ON DELETE CASCADE
);

-- Глобальными остаются только ADMIN и PROJECT_MANAGER
DELETE FROM roles WHERE code IN ('EXECUTOR', 'CO_EXECUTOR', 'OBSERVER');

-- Формат даты (ТЗ, 4.9)
MERGE INTO settings (setting_key, setting_value, updated_at) KEY(setting_key) VALUES
    ('date.format', 'dd.MM.yyyy', CURRENT_TIMESTAMP);

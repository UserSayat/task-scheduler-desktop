-- V6: Уведомления
-- Зависимости: V1 (users)

CREATE TABLE IF NOT EXISTS notifications (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL,
    type            VARCHAR(50) NOT NULL,
    title           VARCHAR(255) NOT NULL,
    body            CLOB,
    entity_type     VARCHAR(50),
    entity_id       UUID,
    is_read         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications(user_id, is_read);
CREATE INDEX IF NOT EXISTS idx_notifications_created ON notifications(created_at);
CREATE INDEX IF NOT EXISTS idx_notifications_entity ON notifications(entity_type, entity_id);
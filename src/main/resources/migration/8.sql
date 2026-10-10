# --- !Ups

-- Seed

MERGE INTO roles (id, code, name, created_at, updated_at) KEY(code) VALUES
    ('00000000-0000-7000-8000-000000000001', 'ADMIN',           'Администратор',        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-7000-8000-000000000002', 'PROJECT_MANAGER', 'Руководитель проекта', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-7000-8000-000000000003', 'EXECUTOR',        'Исполнитель',          CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-7000-8000-000000000004', 'CO_EXECUTOR',     'Соисполнитель',        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-7000-8000-000000000005', 'OBSERVER',        'Наблюдатель',          CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

MERGE INTO settings (key, value, updated_at) KEY(key) VALUES
    ('sync.interval_seconds', '120', CURRENT_TIMESTAMP),
    ('sync.batch_size', '100', CURRENT_TIMESTAMP),
    ('timezone.default', 'Europe/Moscow', CURRENT_TIMESTAMP);
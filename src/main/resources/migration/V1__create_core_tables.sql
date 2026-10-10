-- V1: Ядро — пользователи, роли, связь ролей
-- Зависимости: нет

-- Пользователи
CREATE TABLE IF NOT EXISTS users (
    id              UUID PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    position        VARCHAR(255),
    avatar_url      VARCHAR(500),
    timezone        VARCHAR(50) NOT NULL DEFAULT 'Europe/Moscow',
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      UUID,
    updated_by      UUID,
    deleted_at      TIMESTAMP,
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'BLOCKED', 'DELETED'))
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);
CREATE INDEX IF NOT EXISTS idx_users_deleted_at ON users(deleted_at);

-- Роли
CREATE TABLE IF NOT EXISTS roles (
    id              UUID PRIMARY KEY,
    code            VARCHAR(50) NOT NULL UNIQUE,
    name            VARCHAR(100) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Связь пользователей и ролей
CREATE TABLE IF NOT EXISTS user_roles (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL,
    role_id         UUID NOT NULL,
    scope_type      VARCHAR(20) NOT NULL,
    scope_id        UUID,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_roles UNIQUE (user_id, role_id, scope_type, scope_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_roles_user ON user_roles(user_id);
CREATE INDEX IF NOT EXISTS idx_user_roles_role ON user_roles(role_id);
CREATE INDEX IF NOT EXISTS idx_user_roles_scope ON user_roles(scope_type, scope_id);
-- V2: Проекты и участники
-- Зависимости: V1 (users)

CREATE TABLE IF NOT EXISTS projects (
    id              UUID PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    description     CLOB,
    owner_id        UUID NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    visibility      VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    start_date      DATE,
    deadline        DATE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      UUID,
    updated_by      UUID,
    deleted_at      TIMESTAMP,
    CONSTRAINT chk_projects_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT chk_projects_visibility CHECK (visibility IN ('PRIVATE', 'PUBLIC')),
    CONSTRAINT fk_projects_owner FOREIGN KEY (owner_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_projects_owner ON projects(owner_id);
CREATE INDEX IF NOT EXISTS idx_projects_status ON projects(status);
CREATE INDEX IF NOT EXISTS idx_projects_deleted_at ON projects(deleted_at);
CREATE INDEX IF NOT EXISTS idx_projects_deadline ON projects(deadline);

CREATE TABLE IF NOT EXISTS project_members (
    id              UUID PRIMARY KEY,
    project_id      UUID NOT NULL,
    user_id         UUID NOT NULL,
    role            VARCHAR(20) NOT NULL,
    joined_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_project_members UNIQUE (project_id, user_id),
    CONSTRAINT fk_project_members_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_project_members_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_project_members_project ON project_members(project_id);
CREATE INDEX IF NOT EXISTS idx_project_members_user ON project_members(user_id);
-- V3: Задачи, исполнители, наблюдатели
-- Зависимости: V1 (users), V2 (projects)

CREATE TABLE IF NOT EXISTS tasks (
    id              UUID PRIMARY KEY,
    title           VARCHAR(500) NOT NULL,
    description     CLOB,
    creator_id      UUID NOT NULL,
    project_id      UUID NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'NEW',
    priority        VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    start_date      DATE,
    deadline        DATE,
    closed_at       TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      UUID,
    updated_by      UUID,
    deleted_at      TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_tasks_status CHECK (status IN ('NEW', 'IN_PROGRESS', 'ON_REVIEW', 'DONE')),
    CONSTRAINT chk_tasks_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT fk_tasks_creator FOREIGN KEY (creator_id) REFERENCES users(id),
    CONSTRAINT fk_tasks_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_tasks_project ON tasks(project_id);
CREATE INDEX IF NOT EXISTS idx_tasks_status ON tasks(status);
CREATE INDEX IF NOT EXISTS idx_tasks_priority ON tasks(priority);
CREATE INDEX IF NOT EXISTS idx_tasks_deadline ON tasks(deadline);
CREATE INDEX IF NOT EXISTS idx_tasks_creator ON tasks(creator_id);
CREATE INDEX IF NOT EXISTS idx_tasks_deleted_at ON tasks(deleted_at);

-- Исполнители (ответственный + соисполнители)
CREATE TABLE IF NOT EXISTS task_assignees (
    id              UUID PRIMARY KEY,
    task_id         UUID NOT NULL,
    user_id         UUID NOT NULL,
    role            VARCHAR(20) NOT NULL,
    assigned_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_task_assignees UNIQUE (task_id, user_id),
    CONSTRAINT fk_task_assignees_task FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_task_assignees_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_task_assignees_task ON task_assignees(task_id);
CREATE INDEX IF NOT EXISTS idx_task_assignees_user ON task_assignees(user_id);

-- Наблюдатели
CREATE TABLE IF NOT EXISTS task_observers (
    id              UUID PRIMARY KEY,
    task_id         UUID NOT NULL,
    user_id         UUID NOT NULL,
    added_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_task_observers UNIQUE (task_id, user_id),
    CONSTRAINT fk_task_observers_task FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_task_observers_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_task_observers_task ON task_observers(task_id);
CREATE INDEX IF NOT EXISTS idx_task_observers_user ON task_observers(user_id);
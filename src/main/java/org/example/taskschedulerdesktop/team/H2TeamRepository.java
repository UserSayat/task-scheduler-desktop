package org.example.taskschedulerdesktop.team;

import org.example.taskschedulerdesktop.shared.database.Jdbc;
import org.example.taskschedulerdesktop.shared.database.UnitOfWork;
import org.example.taskschedulerdesktop.task.TaskPriority;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class H2TeamRepository implements TeamRepository {

    /** Задачи, которые видны на экранах: не удалены сами и не удалён их проект. */
    private static final String VISIBLE_TASK = """
            t.deleted_at IS NULL
            AND EXISTS (SELECT 1 FROM projects p WHERE p.id = t.project_id AND p.deleted_at IS NULL)
            """;

    /** Сотрудники и число их задач (ответственный или соисполнитель). */
    private static final String MEMBERS = """
            SELECT u.id, u.name, u.position,
                   COUNT(CASE WHEN t.status <> 'DONE' THEN 1 END) AS open_tasks,
                   COUNT(CASE WHEN t.status = 'DONE' THEN 1 END) AS done_tasks
              FROM users u
              LEFT JOIN task_assignees a ON a.user_id = u.id
              LEFT JOIN tasks t ON t.id = a.task_id AND
            """ + VISIBLE_TASK + """
             WHERE u.status = 'ACTIVE' AND u.deleted_at IS NULL
             GROUP BY u.id, u.name, u.position
             ORDER BY u.name
            """;

    /** Открытые задачи всех сотрудников: самые важные первыми. */
    private static final String OPEN_TASKS = """
            SELECT a.user_id, t.title, t.priority, t.deadline
              FROM task_assignees a
              JOIN tasks t ON t.id = a.task_id
             WHERE t.status <> 'DONE' AND
            """ + VISIBLE_TASK + """
             ORDER BY a.user_id,
                      CASE t.priority WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END,
                      t.deadline NULLS LAST, t.created_at
            """;

    private final UnitOfWork uow;

    public H2TeamRepository(UnitOfWork uow) {
        this.uow = uow;
    }

    @Override
    public List<TeamMember> findTeam(int topTasksPerMember) throws SQLException {
        Map<UUID, List<MemberTask>> topTasks = findTopTasks(topTasksPerMember);

        List<TeamMember> team = new ArrayList<>();
        try (PreparedStatement ps = uow.connection().prepareStatement(MEMBERS);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID id = Jdbc.getUuid(rs, "id");
                team.add(new TeamMember(
                        id,
                        rs.getString("name"),
                        rs.getString("position"),
                        rs.getInt("open_tasks"),
                        rs.getInt("done_tasks"),
                        topTasks.getOrDefault(id, List.of())));
            }
        }
        return team;
    }

    private Map<UUID, List<MemberTask>> findTopTasks(int limit) throws SQLException {
        Map<UUID, List<MemberTask>> byUser = new HashMap<>();
        try (PreparedStatement ps = uow.connection().prepareStatement(OPEN_TASKS);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                List<MemberTask> tasks = byUser.computeIfAbsent(Jdbc.getUuid(rs, "user_id"), id -> new ArrayList<>());
                if (tasks.size() < limit) {
                    tasks.add(new MemberTask(
                            rs.getString("title"),
                            Jdbc.getEnum(rs, "priority", TaskPriority.class),
                            Jdbc.getDate(rs, "deadline")));
                }
            }
        }
        return byUser;
    }
}

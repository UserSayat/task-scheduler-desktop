package org.example.taskschedulerdesktop.team;

import java.sql.SQLException;
import java.util.List;

/**
 * Данные экрана «Команда»: активные сотрудники и их нагрузка по локальной базе.
 */
public interface TeamRepository {

    /**
     * @param topTasksPerMember сколько открытых задач показать в карточке
     */
    List<TeamMember> findTeam(int topTasksPerMember) throws SQLException;
}

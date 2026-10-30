package org.example.taskschedulerdesktop.team;

import org.example.taskschedulerdesktop.shared.database.UnitOfWork;

import java.util.List;

public class TeamService {

    /** Сколько открытых задач показывать в карточке сотрудника. */
    static final int TOP_TASKS = 2;

    private final UnitOfWork uow;
    private final TeamRepository repository;

    public TeamService(UnitOfWork uow, TeamRepository repository) {
        this.uow = uow;
        this.repository = repository;
    }

    public List<TeamMember> findTeam() {
        return uow.read(() -> repository.findTeam(TOP_TASKS));
    }
}

package org.example.taskschedulerdesktop.user;

import org.example.taskschedulerdesktop.shared.database.UnitOfWork;

import java.util.List;

public class UserService {

    private final UnitOfWork uow;
    private final UserRepository repository;

    public UserService(UnitOfWork uow, UserRepository repository) {
        this.uow = uow;
        this.repository = repository;
    }

    public List<User> findAllActive() {
        return uow.read(repository::findAllActive);
    }
}

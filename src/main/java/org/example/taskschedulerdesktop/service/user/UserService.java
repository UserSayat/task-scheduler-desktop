package org.example.taskschedulerdesktop.service.user;

import org.example.taskschedulerdesktop.database.UnitOfWork;
import org.example.taskschedulerdesktop.models.User;
import org.example.taskschedulerdesktop.repository.user.UserRepository;

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

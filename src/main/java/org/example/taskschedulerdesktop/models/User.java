package org.example.taskschedulerdesktop.models;

import java.util.UUID;

/**
 * Сотрудник. На клиенте хранится без пароля: пользователей присылает сервер.
 */
public class User {

    private UUID id;
    private String name;
    private String email;
    private String position;
    private String timezone;
    private String status;

    public User(UUID id, String name, String email, String position, String timezone, String status) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.position = position;
        this.timezone = timezone;
        this.status = status;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPosition() { return position; }
    public String getTimezone() { return timezone; }
    public String getStatus() { return status; }

    @Override
    public boolean equals(Object o) {
        return o instanceof User other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}

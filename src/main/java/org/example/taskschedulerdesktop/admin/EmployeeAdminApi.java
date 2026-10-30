package org.example.taskschedulerdesktop.admin;

import org.example.taskschedulerdesktop.shared.api.ApiClient;

import java.util.List;
import java.util.UUID;

/**
 * Запросы администрирования сотрудников (API.md, раздел 6). Все — только для ADMIN.
 */
public class EmployeeAdminApi {

    public record CreateRequest(UUID id, String name, String email, String position, String timezone,
                         String temporaryPassword, List<RoleAssignment> roles) {}

    public record UpdateRequest(String name, String email, String position, String timezone) {}

    public record StatusRequest(String status) {}

    public record PasswordRequest(String temporaryPassword) {}

    public record RolesRequest(List<RoleAssignment> roles) {}

    private final ApiClient api;

    public EmployeeAdminApi(ApiClient api) {
        this.api = api;
    }

    public void create(CreateRequest request) {
        api.post("/users", request);
    }

    public void update(UUID id, UpdateRequest request) {
        api.put("/users/" + id, request);
    }

    public void setRoles(UUID id, List<RoleAssignment> roles) {
        api.put("/users/" + id + "/roles", new RolesRequest(roles));
    }

    public void setStatus(UUID id, String status) {
        api.patch("/users/" + id + "/status", new StatusRequest(status));
    }

    public void resetPassword(UUID id, String temporaryPassword) {
        api.post("/users/" + id + "/reset-password", new PasswordRequest(temporaryPassword));
    }

    public void delete(UUID id) {
        api.delete("/users/" + id);
    }
}

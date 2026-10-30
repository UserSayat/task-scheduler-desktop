package org.example.taskschedulerdesktop.admin;

import org.example.taskschedulerdesktop.shared.api.ApiException;
import org.example.taskschedulerdesktop.shared.api.OfflineException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Правила администрирования без UI и сервера: какие роли уходят на сервер и какие сообщения видит администратор.
 */
class EmployeeAdminServiceTest {

    private static final UUID PROJECT = UUID.randomUUID();
    private static final RoleAssignment PROJECT_ROLE = new RoleAssignment(RoleAssignment.PROJECT_MANAGER, "PROJECT", PROJECT);

    @Test
    void changingGlobalRolesKeepsProjectRoles() {
        Employee employee = employee(List.of(RoleAssignment.global(RoleAssignment.ADMIN), PROJECT_ROLE));

        List<RoleAssignment> roles = EmployeeAdminService.newRoles(employee, form(false, true));

        assertEquals(List.of(RoleAssignment.global(RoleAssignment.PROJECT_MANAGER), PROJECT_ROLE), roles,
                "сервер заменяет роли целиком — проектная роль должна уйти вместе с глобальными");
    }

    @Test
    void sameRolesInAnotherOrderAreNotAChange() {
        Employee employee = employee(List.of(PROJECT_ROLE, RoleAssignment.global(RoleAssignment.ADMIN)));

        List<RoleAssignment> roles = EmployeeAdminService.newRoles(employee, form(true, false));

        assertTrue(EmployeeAdminService.sameRoles(roles, employee.roles()));
        assertFalse(EmployeeAdminService.sameRoles(EmployeeAdminService.newRoles(employee, form(false, false)), employee.roles()));
    }

    @Test
    void errorsAreExplainedInPlainWords() {
        assertTrue(EmployeeAdminService.message(new OfflineException("x", null)).startsWith("Нет связи с сервером"));
        assertEquals("Этот email уже используется.",
                EmployeeAdminService.message(new ApiException(409, "CONFLICT", "Conflict", null)));
        assertEquals("Пароль должен быть не короче 8 символов",
                EmployeeAdminService.message(new ApiException(422, "VALIDATION_ERROR", "Некорректные данные",
                        Map.of("temporaryPassword", List.of("Пароль должен быть не короче 8 символов")))));
    }

    @Test
    void temporaryPasswordIsLongEnoughAndUnambiguous() {
        for (int i = 0; i < 100; i++) {
            String password = TemporaryPasswords.generate();
            assertTrue(password.length() >= TemporaryPasswords.MIN_LENGTH);
            assertTrue(password.chars().noneMatch(c -> "0O1lI".indexOf(c) >= 0), password);
        }
    }

    private static Employee employee(List<RoleAssignment> roles) {
        return new Employee(UUID.randomUUID(), "Иван", "ivan@company.ru", null, "Europe/Moscow", Employee.ACTIVE, roles);
    }

    private static EmployeeForm form(boolean admin, boolean projectManager) {
        return new EmployeeForm("Иван", "ivan@company.ru", "", "Europe/Moscow", admin, projectManager);
    }
}

package org.example.taskschedulerdesktop.admin;

import com.github.f4b6a3.uuid.UuidCreator;
import javafx.concurrent.Task;
import org.example.taskschedulerdesktop.shared.api.ApiException;
import org.example.taskschedulerdesktop.shared.api.OfflineException;
import org.example.taskschedulerdesktop.shared.database.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Администрирование сотрудников. Чтение — из локальной базы, изменения — запросами к серверу
 * в фоне. После успешного изменения запускается синхронизация ({@code afterChange}),
 * которая приносит изменённого сотрудника в локальную базу. Колбэки выполняются в UI-потоке.
 */
public class EmployeeAdminService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeAdminService.class);

    private final UnitOfWork uow;
    private final EmployeeRepository repository;
    private final EmployeeAdminApi api;
    private final Runnable afterChange;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "employee-admin");
        thread.setDaemon(true);
        return thread;
    });

    public EmployeeAdminService(UnitOfWork uow, EmployeeRepository repository, EmployeeAdminApi api, Runnable afterChange) {
        this.uow = uow;
        this.repository = repository;
        this.api = api;
        this.afterChange = afterChange;
    }

    // ===== Чтение (локально, работает и офлайн) =====

    public void findEmployees(Consumer<List<Employee>> onSuccess, Consumer<String> onError) {
        run(() -> uow.read(repository::findAll), onSuccess, onError);
    }

    public void isAdmin(UUID userId, Consumer<Boolean> onResult) {
        run(() -> uow.read(() -> repository.hasGlobalRole(userId, RoleAssignment.ADMIN)), onResult,
                error -> onResult.accept(false));
    }

    // ===== Изменения (через сервер, только онлайн) =====

    public void create(EmployeeForm form, String temporaryPassword, Runnable onSuccess, Consumer<String> onError) {
        change(() -> api.create(new EmployeeAdminApi.CreateRequest(
                UuidCreator.getTimeOrderedEpoch(),
                form.name().trim(),
                form.email().trim(),
                blankToNull(form.position()),
                form.timezone(),
                temporaryPassword,
                globalRoles(form))), onSuccess, onError);
    }

    /**
     * Профиль и роли отправляются отдельными запросами и только если изменились.
     * Роли сотрудника в отдельных проектах сохраняются как были: сервер заменяет список ролей целиком.
     */
    public void update(Employee employee, EmployeeForm form, Runnable onSuccess, Consumer<String> onError) {
        change(() -> {
            EmployeeAdminApi.UpdateRequest profile = new EmployeeAdminApi.UpdateRequest(
                    form.name().trim(), form.email().trim(), blankToNull(form.position()), form.timezone());
            if (!profile.equals(new EmployeeAdminApi.UpdateRequest(employee.name(), employee.email(),
                    blankToNull(employee.position()), employee.timezone()))) {
                api.update(employee.id(), profile);
            }

            List<RoleAssignment> roles = newRoles(employee, form);
            if (!sameRoles(roles, employee.roles())) {
                api.setRoles(employee.id(), roles);
            }
        }, onSuccess, onError);
    }

    /** Глобальные роли из формы плюс прежние роли в проектах. */
    public static List<RoleAssignment> newRoles(Employee employee, EmployeeForm form) {
        List<RoleAssignment> roles = new ArrayList<>(globalRoles(form));
        employee.roles().stream().filter(role -> !role.isGlobal()).forEach(roles::add);
        return roles;
    }

    public void setBlocked(Employee employee, boolean blocked, Runnable onSuccess, Consumer<String> onError) {
        change(() -> api.setStatus(employee.id(), blocked ? Employee.BLOCKED : Employee.ACTIVE), onSuccess, onError);
    }

    public void resetPassword(Employee employee, String temporaryPassword, Runnable onSuccess, Consumer<String> onError) {
        change(() -> api.resetPassword(employee.id(), temporaryPassword), onSuccess, onError);
    }

    public void delete(Employee employee, Runnable onSuccess, Consumer<String> onError) {
        change(() -> api.delete(employee.id()), onSuccess, onError);
    }

    // ===== Внутреннее =====

    private interface Change {
        void apply();
    }

    private void change(Change change, Runnable onSuccess, Consumer<String> onError) {
        run(() -> {
            change.apply();
            afterChange.run();
            return null;
        }, ignored -> onSuccess.run(), onError);
    }

    private <T> void run(Callable<T> work, Consumer<T> onSuccess, Consumer<String> onError) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            log.warn("Employee administration failed: {}", error.toString());
            onError.accept(message(error));
        });
        executor.submit(task);
    }

    /** Сообщение об ошибке для администратора. */
    static String message(Throwable error) {
        if (error instanceof OfflineException) {
            return "Нет связи с сервером. Управлять сотрудниками можно только онлайн.";
        }
        if (error instanceof ApiException api) {
            if (api.is("FORBIDDEN")) {
                return "Недостаточно прав: управлять сотрудниками может только администратор.";
            }
            if (api.is("CONFLICT")) {
                return "Этот email уже используется.";
            }
            if (api.fields() != null && !api.fields().isEmpty()) {
                return api.fields().values().stream()
                        .flatMap(Collection::stream)
                        .distinct()
                        .collect(Collectors.joining("\n"));
            }
            return api.getMessage();
        }
        if (error instanceof IllegalArgumentException) {
            return error.getMessage();
        }
        return "Не удалось выполнить действие. Подробности в журнале приложения.";
    }

    private static List<RoleAssignment> globalRoles(EmployeeForm form) {
        List<RoleAssignment> roles = new ArrayList<>();
        if (form.admin()) {
            roles.add(RoleAssignment.global(RoleAssignment.ADMIN));
        }
        if (form.projectManager()) {
            roles.add(RoleAssignment.global(RoleAssignment.PROJECT_MANAGER));
        }
        return roles;
    }

    static boolean sameRoles(List<RoleAssignment> a, List<RoleAssignment> b) {
        return a.size() == b.size() && a.containsAll(b) && b.containsAll(a);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

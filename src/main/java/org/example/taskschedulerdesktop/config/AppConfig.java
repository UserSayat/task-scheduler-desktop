package org.example.taskschedulerdesktop.config;

import com.github.f4b6a3.uuid.UuidCreator;
import org.example.taskschedulerdesktop.auth.AuthService;
import org.example.taskschedulerdesktop.auth.TokenStore;
import org.example.taskschedulerdesktop.dashboard.DashboardController;
import org.example.taskschedulerdesktop.project.AsyncProjectService;
import org.example.taskschedulerdesktop.project.CreateProjectController;
import org.example.taskschedulerdesktop.project.EditProjectController;
import org.example.taskschedulerdesktop.project.H2ProjectRepository;
import org.example.taskschedulerdesktop.project.ProjectCardController;
import org.example.taskschedulerdesktop.project.ProjectCardService;
import org.example.taskschedulerdesktop.project.ProjectDetailsController;
import org.example.taskschedulerdesktop.project.ProjectServiceImpl;
import org.example.taskschedulerdesktop.project.ProjectSyncAdapter;
import org.example.taskschedulerdesktop.project.ProjectsController;
import org.example.taskschedulerdesktop.session.Session;
import org.example.taskschedulerdesktop.shared.api.ApiClient;
import org.example.taskschedulerdesktop.shared.api.ServerConfig;
import org.example.taskschedulerdesktop.shared.database.DatabaseConnection;
import org.example.taskschedulerdesktop.shared.database.UnitOfWork;
import org.example.taskschedulerdesktop.shell.AppShell;
import org.example.taskschedulerdesktop.sync.SyncScheduler;
import org.example.taskschedulerdesktop.sync.SyncService;
import org.example.taskschedulerdesktop.sync.local.ChangeTracker;
import org.example.taskschedulerdesktop.sync.local.SyncStateRepository;
import org.example.taskschedulerdesktop.sync.protocol.HttpSyncApi;
import org.example.taskschedulerdesktop.tag.H2TagRepository;
import org.example.taskschedulerdesktop.tag.TagService;
import org.example.taskschedulerdesktop.tag.TagSyncAdapter;
import org.example.taskschedulerdesktop.task.AsyncTaskService;
import org.example.taskschedulerdesktop.task.CreateTaskController;
import org.example.taskschedulerdesktop.task.H2TaskRepository;
import org.example.taskschedulerdesktop.task.TaskCardService;
import org.example.taskschedulerdesktop.task.TaskDetailsController;
import org.example.taskschedulerdesktop.task.TaskServiceImpl;
import org.example.taskschedulerdesktop.task.TaskSyncAdapter;
import org.example.taskschedulerdesktop.task.TaskTableController;
import org.example.taskschedulerdesktop.user.AsyncUserService;
import org.example.taskschedulerdesktop.user.H2UserRepository;
import org.example.taskschedulerdesktop.user.UserService;
import org.example.taskschedulerdesktop.user.UserSyncAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import javafx.stage.Stage;
import javafx.util.Callback;

/**
 * Сборка зависимостей приложения: база, сервисы фич, связь с сервером и синхронизация.
 */
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    private static AppConfig instance;

    private Stage primaryStage;
    private AppShell appShell;

    private final DatabaseConnection databaseConnection;
    private final UnitOfWork unitOfWork;
    private final Session session;
    private final boolean demoMode;

    private final AsyncTaskService asyncTaskService;
    private final AsyncProjectService asyncProjectService;
    private final AsyncUserService asyncUserService;

    private final AuthService authService;
    private final TokenStore tokenStore;
    private final SyncService syncService;
    private final SyncScheduler syncScheduler;

    private final Callback<Class<?>, Object> controllerFactory;

    private AppConfig() {
        this.databaseConnection = DatabaseConnection.getInstance();
        this.unitOfWork = new UnitOfWork(databaseConnection);
        this.demoMode = DevDataSeeder.isEnabled();

        SyncStateRepository syncStateRepository = new SyncStateRepository(unitOfWork);
        UUID deviceId = loadOrCreateDeviceId(syncStateRepository);
        this.session = new Session(deviceId, null);
        if (demoMode) {
            DevDataSeeder.seed(unitOfWork);
            session.signIn(DevDataSeeder.DEV_ADMIN_ID);
        }

        // ===== Фичи =====
        H2TaskRepository taskRepository = new H2TaskRepository(unitOfWork);
        H2ProjectRepository projectRepository = new H2ProjectRepository(unitOfWork);

        ChangeTracker changeTracker = new ChangeTracker(unitOfWork, session);
        TagService tagService = new TagService(new H2TagRepository(unitOfWork), changeTracker, session);

        this.asyncTaskService = new AsyncTaskService(
                new TaskServiceImpl(unitOfWork, taskRepository, tagService, changeTracker, session),
                new TaskCardService());
        this.asyncProjectService = new AsyncProjectService(
                new ProjectServiceImpl(unitOfWork, projectRepository, changeTracker, session),
                new ProjectCardService());
        this.asyncUserService = new AsyncUserService(
                new UserService(unitOfWork, new H2UserRepository(unitOfWork)));

        // ===== Сервер и синхронизация =====
        ApiClient apiClient = new ApiClient(ServerConfig.apiBase());
        this.tokenStore = new TokenStore(apiClient, unitOfWork, session);
        apiClient.setAccessTokens(tokenStore);
        this.authService = new AuthService(apiClient, tokenStore, unitOfWork, session);

        UserSyncAdapter userSync = new UserSyncAdapter(unitOfWork);
        this.syncService = SyncService.create(unitOfWork, new HttpSyncApi(apiClient), session, userSync, List.of(
                userSync,
                new TagSyncAdapter(unitOfWork),
                new ProjectSyncAdapter(unitOfWork, projectRepository, userSync),
                new TaskSyncAdapter(unitOfWork, taskRepository, userSync)));
        this.syncScheduler = new SyncScheduler(syncService);
        if (!demoMode) {
            changeTracker.setOnChange(syncScheduler::changeRecorded);
        }

        this.controllerFactory = clazz -> {
            if (clazz == DashboardController.class) {
                return new DashboardController(asyncProjectService, asyncTaskService);
            }
            if (clazz == TaskTableController.class) {
                return new TaskTableController(asyncTaskService);
            }
            if (clazz == ProjectDetailsController.class) {
                return new ProjectDetailsController(asyncTaskService, asyncProjectService);
            }
            if (clazz == CreateTaskController.class) {
                return new CreateTaskController(asyncTaskService, asyncProjectService, asyncUserService);
            }
            if (clazz == TaskDetailsController.class) {
                return new TaskDetailsController(asyncTaskService, asyncUserService);
            }
            if (clazz == ProjectsController.class) {
                return new ProjectsController(asyncProjectService);
            }
            if (clazz == CreateProjectController.class) {
                return new CreateProjectController(asyncProjectService, asyncUserService);
            }
            if (clazz == EditProjectController.class) {
                return new EditProjectController(asyncProjectService, asyncUserService);
            }
            if (clazz == ProjectCardController.class) {
                return new ProjectCardController(asyncProjectService);
            }
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    /**
     * device_id генерируется при первом запуске и хранится в sync_state (схема синхронизации, 7.2).
     */
    private UUID loadOrCreateDeviceId(SyncStateRepository syncStateRepository) {
        return unitOfWork.inTransaction(() -> {
            var stored = syncStateRepository.get(SyncStateRepository.DEVICE_ID);
            if (stored.isPresent()) {
                return UUID.fromString(stored.get());
            }
            UUID deviceId = UuidCreator.getTimeOrderedEpoch();
            syncStateRepository.set(SyncStateRepository.DEVICE_ID, deviceId.toString());
            log.info("New device id: {}", deviceId);
            return deviceId;
        });
    }

    public static AppConfig getInstance() {
        if (instance == null) {
            instance = new AppConfig();
        }
        return instance;
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public AppShell getAppShell() {
        return appShell;
    }

    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
    }

    public Callback<Class<?>, Object> getControllerFactory() {
        return controllerFactory;
    }

    public DatabaseConnection getDatabaseConnection() {
        return databaseConnection;
    }

    public Session getSession() {
        return session;
    }

    /** Демо-режим: без сервера, с демо-сотрудниками (флаг {@code -Dtaskhub.dev=true}). */
    public boolean isDemoMode() {
        return demoMode;
    }

    public AsyncTaskService getAsyncTaskService() {
        return asyncTaskService;
    }

    public AsyncProjectService getAsyncProjectService() {
        return asyncProjectService;
    }

    public AsyncUserService getAsyncUserService() {
        return asyncUserService;
    }

    public AuthService getAuthService() {
        return authService;
    }

    public TokenStore getTokenStore() {
        return tokenStore;
    }

    public SyncService getSyncService() {
        return syncService;
    }

    public SyncScheduler getSyncScheduler() {
        return syncScheduler;
    }
}

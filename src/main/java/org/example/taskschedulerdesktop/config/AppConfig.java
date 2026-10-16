package org.example.taskschedulerdesktop.config;

import com.github.f4b6a3.uuid.UuidCreator;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.example.taskschedulerdesktop.controllers.projects.*;
import org.example.taskschedulerdesktop.controllers.review.DashboardController;
import org.example.taskschedulerdesktop.controllers.tasks.*;
import org.example.taskschedulerdesktop.database.DatabaseConnection;
import org.example.taskschedulerdesktop.database.UnitOfWork;
import org.example.taskschedulerdesktop.repository.project.H2ProjectRepository;
import org.example.taskschedulerdesktop.repository.tag.H2TagRepository;
import org.example.taskschedulerdesktop.repository.task.H2TaskRepository;
import org.example.taskschedulerdesktop.repository.user.H2UserRepository;
import org.example.taskschedulerdesktop.service.project.AsyncProjectService;
import org.example.taskschedulerdesktop.service.project.ProjectCardService;
import org.example.taskschedulerdesktop.service.project.ProjectServiceImpl;
import org.example.taskschedulerdesktop.service.tag.TagService;
import org.example.taskschedulerdesktop.service.task.AsyncTaskService;
import org.example.taskschedulerdesktop.service.task.TaskCardService;
import org.example.taskschedulerdesktop.service.task.TaskServiceImpl;
import org.example.taskschedulerdesktop.service.user.AsyncUserService;
import org.example.taskschedulerdesktop.service.user.UserService;
import org.example.taskschedulerdesktop.session.Session;
import org.example.taskschedulerdesktop.sync.ChangeTracker;
import org.example.taskschedulerdesktop.sync.SyncStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    private static AppConfig instance;

    private Stage primaryStage;

    private final DatabaseConnection databaseConnection;
    private final UnitOfWork unitOfWork;
    private final Session session;
    private final AsyncTaskService asyncTaskService;
    private final AsyncProjectService asyncProjectService;
    private final AsyncUserService asyncUserService;

    private final Callback<Class<?>, Object> controllerFactory;

    private AppConfig() {
        this.databaseConnection = DatabaseConnection.getInstance();
        this.unitOfWork = new UnitOfWork(databaseConnection);

        SyncStateRepository syncStateRepository = new SyncStateRepository(unitOfWork);
        UUID deviceId = loadOrCreateDeviceId(syncStateRepository);

        UUID currentUserId = null;
        if (DevDataSeeder.isEnabled()) {
            DevDataSeeder.seed(unitOfWork);
            currentUserId = DevDataSeeder.DEV_ADMIN_ID;
        }
        this.session = new Session(deviceId, currentUserId);

        ChangeTracker changeTracker = new ChangeTracker(unitOfWork, session);
        TagService tagService = new TagService(new H2TagRepository(unitOfWork), changeTracker, session);

        this.asyncTaskService = new AsyncTaskService(
                new TaskServiceImpl(unitOfWork, new H2TaskRepository(unitOfWork), tagService, changeTracker, session),
                new TaskCardService());
        this.asyncProjectService = new AsyncProjectService(
                new ProjectServiceImpl(unitOfWork, new H2ProjectRepository(unitOfWork), changeTracker, session),
                new ProjectCardService());
        this.asyncUserService = new AsyncUserService(
                new UserService(unitOfWork, new H2UserRepository(unitOfWork)));

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

    public Callback<Class<?>, Object> getControllerFactory() {
        return controllerFactory;
    }

    public DatabaseConnection getDatabaseConnection() {
        return databaseConnection;
    }

    public Session getSession() {
        return session;
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
}

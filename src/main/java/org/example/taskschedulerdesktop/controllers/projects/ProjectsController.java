package org.example.taskschedulerdesktop.controllers.projects;

import javafx.concurrent.Service;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import org.example.taskschedulerdesktop.config.AppConfig;
import org.example.taskschedulerdesktop.controllers.Shutdownable;
import org.example.taskschedulerdesktop.models.Project;
import org.example.taskschedulerdesktop.models.ProjectCard;
import org.example.taskschedulerdesktop.navigation.NavigationManager;
import org.example.taskschedulerdesktop.navigation.Routes;
import org.example.taskschedulerdesktop.service.project.AsyncProjectService;
import org.example.taskschedulerdesktop.utils.TaskStatus;

import java.io.IOException;
import java.util.List;

public class ProjectsController implements Shutdownable {

    private final AsyncProjectService projectService;
    private final Service<List<Node>> projectsLoader;

    @FXML private Button createProjectButton;

    @FXML private FlowPane projectsFlowPane;
    @FXML private ProgressIndicator projectsLoadingIndicator;

    public ProjectsController(AsyncProjectService projectService) {
        this.projectService = projectService;
        projectsLoader = projectService.createProjectsLoader();

    }

    @FXML
    public void initialize() {
        //projectsLoadingIndicator.visibleProperty().bind(projectsLoader.runningProperty());

        projectsLoader.setOnSucceeded(event -> {
            projectsFlowPane.getChildren().clear();
            List<Node> projects = projectsLoader.getValue();

            if (projects.isEmpty()) {
                projectsFlowPane.getChildren().add(new Label("У вас еще нет созданных проектов"));
            } else {
                projectsFlowPane.getChildren().addAll(projects);
            }
        });

        projectsLoader.setOnFailed(event -> {
            projectsFlowPane.getChildren().clear();
            projectsFlowPane.getChildren().add(new Label("Ошибка загрузки данных из базы"));

            Throwable error = projectsLoader.getException();
            if (error != null) error.printStackTrace();
        });

        //NavigationManager.setCurrentTitle("Проекты");

        createProjectButton.setOnAction(event -> NavigationManager.openDialog(
                Routes.CREATE_PROJECT,
                "Новый проект",
                AppConfig.getInstance().getPrimaryStage()));

        refreshProjects();
    }

    public void refreshProjects() {
        projectsLoader.restart(); // Перезапустит поток, обновив данные
    }

    @Override
    public void shutdown() {
        // Если пользователь ушел с экрана, а проекты еще грузились — отменяем поток
        if (projectsLoader != null && projectsLoader.isRunning()) {
            projectsLoader.cancel();
        }
    }

//
//        //TODO Сделать заполнение карточек данными из БД
//        ProjectCard[] tempProjects = {
//                new ProjectCard("Редизайн портала", "Руководитель: А.К.",
//                        72, 18, 13, 5,
//                        "Утвердить макеты главной страницы", "03 авг",
//                        "Оптимизация загрузки изображений", "06 авг",
//                        "Компонент навигационного меню", "31 июл"),
//                new ProjectCard("Миграция CRM", "Руководитель: М.В.",
//                        41, 24, 10, 14,
//                        "Настроить интеграцию с 1С", "05 авг",
//                        "Перенос базы клиентов", "08 авг",
//                        "Документация API", "07 авг"),
//                new ProjectCard("Мобильное приложение", "Руководитель: Е.Н.",
//                        88, 31, 27, 4,
//                        "Тестирование push-уведомлений", "04 авг",
//                        "Релиз бета-версии 2.1", "01 авг",
//                        null, null),
//                new ProjectCard("Отчетность Q3", "Руководитель: П.С.",
//                        15, 12, 2, 10,
//                        "Подготовить данные по выручке", "10 авг",
//                        "Согласовать KPI с отделами", "12 авг",
//                        null, null),

}

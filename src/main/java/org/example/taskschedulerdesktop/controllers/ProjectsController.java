package org.example.taskschedulerdesktop.controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import org.example.taskschedulerdesktop.config.AppConfig;
import org.example.taskschedulerdesktop.models.Project;
import org.example.taskschedulerdesktop.models.ProjectCard;
import org.example.taskschedulerdesktop.navigation.NavigationManager;
import org.example.taskschedulerdesktop.navigation.Routes;
import org.example.taskschedulerdesktop.service.project.ProjectService;
import org.example.taskschedulerdesktop.utils.TaskStatus;

import java.io.IOException;
import java.util.List;

public class ProjectsController {

    private final ProjectService projectService;

    @FXML private Button createProjectButton;

    @FXML private FlowPane projectsFlowPane;

    public ProjectsController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @FXML
    public void initialize() {
        NavigationManager.setCurrentTitle("Проекты");

        createProjectButton.setOnAction(event -> NavigationManager.openDialog(
                Routes.CREATE_PROJECT,
                "Новый проект",
                AppConfig.getInstance().getPrimaryStage()));

        loadProjects();
    }

    public void loadProjects() {

        List<Project> projects = projectService.findAll();

        //TODO Сделать заполнение карточек данными из БД
        ProjectCard[] tempProjects = {
                new ProjectCard("Редизайн портала", "Руководитель: А.К.",
                        72, 18, 13, 5,
                        "Утвердить макеты главной страницы", "03 авг",
                        "Оптимизация загрузки изображений", "06 авг",
                        "Компонент навигационного меню", "31 июл"),
                new ProjectCard("Миграция CRM", "Руководитель: М.В.",
                        41, 24, 10, 14,
                        "Настроить интеграцию с 1С", "05 авг",
                        "Перенос базы клиентов", "08 авг",
                        "Документация API", "07 авг"),
                new ProjectCard("Мобильное приложение", "Руководитель: Е.Н.",
                        88, 31, 27, 4,
                        "Тестирование push-уведомлений", "04 авг",
                        "Релиз бета-версии 2.1", "01 авг",
                        null, null),
                new ProjectCard("Отчетность Q3", "Руководитель: П.С.",
                        15, 12, 2, 10,
                        "Подготовить данные по выручке", "10 авг",
                        "Согласовать KPI с отделами", "12 авг",
                        null, null),
        };

        // ============================================================
        // ЗАГРУЗКА КАРТОЧЕК
        // ============================================================


        try {
            for (Project project : projects) {
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/org/example/taskschedulerdesktop/view/project_card.fxml")
                );

                VBox card = loader.load();

                ProjectCardController cardController = loader.getController();

                cardController.setProjectNameLabel(project.getName());
                cardController.setProjectSupervisorLabel(project.getSupervisor());

                int numberOfTasks = projectService.findAll().size();
                int completedTasks = projectService.countTasksByProjectNameAndStatus(project.getName(), TaskStatus.COMPLETED);
                int remainingTasks = numberOfTasks - completedTasks;
                int percentOfCompletion = (completedTasks / numberOfTasks) * 100;

                cardController.setNumberOfTasksLabel(numberOfTasks);
                cardController.setRemainingTasksLabel(remainingTasks);
                cardController.setCompletedTasksLabel(completedTasks);
                cardController.setPercentOfCompletionLabel(percentOfCompletion);

                projectsFlowPane.getChildren().add(card);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

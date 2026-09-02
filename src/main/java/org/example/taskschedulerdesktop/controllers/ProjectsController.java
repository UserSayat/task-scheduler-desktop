package org.example.taskschedulerdesktop.controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import org.example.taskschedulerdesktop.models.ProjectCard;
import org.example.taskschedulerdesktop.navigation.NavigationManager;

import java.io.IOException;

public class ProjectsController {

    @FXML
    private FlowPane projectsFlowPane;

    @FXML
    public void initialize() {
        NavigationManager.setCurrentTitle("Проекты");
        loadProjects();
    }

    public void loadProjects() {

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
            for (ProjectCard project : tempProjects) {
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/org/example/taskschedulerdesktop/view/project_card.fxml")
                );

                VBox card = loader.load();

                ProjectCardController cardController = loader.getController();

                cardController.setProjectData(project);

                projectsFlowPane.getChildren().add(card);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

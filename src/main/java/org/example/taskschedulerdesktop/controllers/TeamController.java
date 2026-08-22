package org.example.taskschedulerdesktop.controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import org.example.taskschedulerdesktop.models.EmployeeCard;

import java.io.IOException;

public class TeamController {

    @FXML
    private FlowPane teamFlowPane;

    @FXML
    public void initialize() {
        loadTeam();
    }

    public void loadTeam() {

        //TODO Сделать заполнение карточек данными из БД
        EmployeeCard[] tempEmployees = {
                new EmployeeCard("Алексей Козлов", "Frontend-разработчик",
                        2, 0,
                        "Утвердить макеты главной страницы",
                        "Оптимизация загрузки изображений"),
                new EmployeeCard("Мария Волкова", "Backend-разработчик",
                        1, 0,
                        "Настроить интеграцию 1С",
                        null),
                new EmployeeCard("Елена Никитина", "QA-инженер",
                        2, 1,
                        "Тестирование push-уведомлений",
                        "Релиз бета-версии 2.1"),
                new EmployeeCard("Павел Сорокин", "Аналитик данных",
                        2, 0,
                        "Подготовить данные по выручке",
                        "Согласовать KPI с отделами"),
                new EmployeeCard("Дмитрий Лебедев", "Backend-разработчик",
                        2, 0,
                        "Перенос базы клиентов",
                        "Документация API"),
                new EmployeeCard("Ирина Федорова", "UI/UX Дизайнер",
                        1, 1,
                        "Компонент навигационного меню",
                        null)
        };

        try {
            for (EmployeeCard employee : tempEmployees) {
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/org/example/taskschedulerdesktop/view/employee_card.fxml")
                );

                VBox employeeCard = loader.load();

                EmployeeCardController cardController = loader.getController();

                cardController.setEmployeeData(employee);

                teamFlowPane.getChildren().add(employeeCard);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

package org.example.taskschedulerdesktop.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.TableView;
import org.example.taskschedulerdesktop.models.Task;

import java.util.List;

public class TaskTableController {

    @FXML
    private TableView<Task> taskTable;

    public void setTableData(List<Task> tasks) {
        taskTable.getItems().setAll(tasks);
    }

}

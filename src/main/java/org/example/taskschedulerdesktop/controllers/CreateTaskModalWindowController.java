package org.example.taskschedulerdesktop.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.*;

public class CreateTaskModalWindowController {

    @FXML
    private TextField taskNameTextField;

    @FXML
    private ChoiceBox<?> projectChoiceBox;
    @FXML
    private ChoiceBox<?>executorChoiceBox;

    @FXML
    private Label highPriorityLabel;
    @FXML
    private Label middlePriorityLabel;
    @FXML
    private Label lowPriorityLabel;

    @FXML
    private DatePicker deadlineDatePicker;

    @FXML
    private TextField tagsTextField;

    @FXML
    private Button createTaskButton;
    @FXML
    private Button cancelButton;
    @FXML
    private Button exitButton;
}

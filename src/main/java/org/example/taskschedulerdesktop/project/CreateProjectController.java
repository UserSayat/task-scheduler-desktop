package org.example.taskschedulerdesktop.project;

import org.example.taskschedulerdesktop.shared.ui.ComboSearch;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import org.example.taskschedulerdesktop.user.User;
import org.example.taskschedulerdesktop.shared.navigation.NavigationManager;
import org.example.taskschedulerdesktop.user.AsyncUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateProjectController {

    private final Logger log = LoggerFactory.getLogger(CreateProjectController.class);

    private final AsyncProjectService projectService;
    private final AsyncUserService userService;

    @FXML private TextField projectNameTextField;
    @FXML private TextArea descriptionTextArea;

    @FXML private ComboBox<User> supervisorComboBox;

    @FXML private ToggleGroup visibilityGroup;
    @FXML private ToggleButton publicToggle;
    @FXML private ToggleButton privateToggle;
    @FXML private Label visibilityHint;

    @FXML private VBox membersVBox;
    @FXML private ComboBox<User> newMemberComboBox;
    @FXML private ComboBox<MemberRole> newMemberRoleComboBox;
    @FXML private Button addMemberButton;
    private MembersEditor membersEditor;

    @FXML private Button createProjectButton;
    @FXML private Button cancelButton;

    public CreateProjectController(AsyncProjectService projectService, AsyncUserService userService) {
        this.projectService = projectService;
        this.userService = userService;
    }

    @FXML
    public void initialize() {
        // Поиск вводом с клавиатуры
        ComboSearch.install(supervisorComboBox);
        publicToggle.setUserData(Visibility.PUBLIC);
        privateToggle.setUserData(Visibility.PRIVATE);
        // Выбор есть всегда: повторное нажатие на выбранную кнопку его не снимает
        visibilityGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null) {
                oldToggle.setSelected(true);
            } else {
                showVisibilityHint();
            }
        });
        showVisibilityHint();
        membersEditor = new MembersEditor(membersVBox, newMemberComboBox, newMemberRoleComboBox, addMemberButton,
                supervisorComboBox, false);

        userService.findActiveUsers(
                users -> {
                    supervisorComboBox.getItems().setAll(users);
                    membersEditor.setUsers(users);
                },
                error -> NavigationManager.showToast("Ошибка загрузки сотрудников", "error")
        );

        createProjectButton.setOnAction(event -> {
            User owner = supervisorComboBox.getValue();
            if (projectNameTextField.getText().isBlank() || owner == null) {
                NavigationManager.showToast("Заполните пустые поля", "info");
                return;
            }

            projectService.saveProject(projectNameTextField.getText(), owner.getId(), selectedVisibility(),
                    descriptionTextArea.getText(), membersEditor.members(),
                    created -> {
                        NavigationManager.closeForm();
                        NavigationManager.showToast("Проект создан", "success");
                    },
                    error -> NavigationManager.showToast("Ошибка создания проекта", "error"));
        });

        cancelButton.setOnAction(event -> NavigationManager.closeForm());
    }

    private Visibility selectedVisibility() {
        return (Visibility) visibilityGroup.getSelectedToggle().getUserData();
    }

    private void showVisibilityHint() {
        visibilityHint.setText(selectedVisibility() == Visibility.PUBLIC
                ? "Проект и его задачи видят все сотрудники"
                : "Видят только участники проекта и администраторы");
    }
}

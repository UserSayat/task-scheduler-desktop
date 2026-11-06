package org.example.taskschedulerdesktop.project;

import org.example.taskschedulerdesktop.shared.ui.ComboSearch;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.example.taskschedulerdesktop.shared.navigation.ContextAware;
import org.example.taskschedulerdesktop.shared.navigation.NavigationManager;
import org.example.taskschedulerdesktop.user.AsyncUserService;
import org.example.taskschedulerdesktop.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Редактирование проекта: название, руководитель и состав участников с ролями.
 * Руководитель в списке участников не показывается — он выбирается отдельно.
 */
public class EditProjectController implements ContextAware {
    private final Logger log = LoggerFactory.getLogger(EditProjectController.class);

    private final AsyncProjectService projectService;
    private final AsyncUserService userService;

    @FXML private TextField projectNameTextField;

    @FXML private ComboBox<User> supervisorComboBox;

    @FXML private VBox membersVBox;
    @FXML private ComboBox<User> newMemberComboBox;
    @FXML private ComboBox<MemberRole> newMemberRoleComboBox;
    @FXML private Button addMemberButton;

    @FXML private TextArea descriptionTextArea;
    @FXML private ComboBox<Visibility> visibilityComboBox;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker deadlinePicker;

    @FXML private Button saveProjectButton;
    @FXML private Button cancelButton;

    private ProjectView context;

    /** Активные сотрудники по id — для руководителя. */
    private Map<UUID, User> users = Map.of();

    private MembersEditor membersEditor;

    public EditProjectController(AsyncProjectService projectService, AsyncUserService userService) {
        this.projectService = projectService;
        this.userService = userService;
    }

    @FXML
    public void initialize() {
        // Поиск вводом с клавиатуры
        ComboSearch.install(supervisorComboBox);
        visibilityComboBox.getItems().setAll(Visibility.values());
        // Прежний руководитель остаётся в проекте менеджером, новый уходит из списка участников
        membersEditor = new MembersEditor(membersVBox, newMemberComboBox, newMemberRoleComboBox, addMemberButton,
                supervisorComboBox, true);

        cancelButton.setOnAction(event -> close());

        saveProjectButton.setOnAction(event -> {
            User owner = supervisorComboBox.getValue();
            if (context == null || projectNameTextField.getText().isBlank() || owner == null) {
                NavigationManager.showToast("Заполните пустые поля", "info");
                return;
            }

            if (startDatePicker.getValue() != null && deadlinePicker.getValue() != null
                    && deadlinePicker.getValue().isBefore(startDatePicker.getValue())) {
                NavigationManager.showToast("Срок проекта раньше даты начала", "info");
                return;
            }
            ProjectInfo info = new ProjectInfo(descriptionTextArea.getText(), visibilityComboBox.getValue(),
                    startDatePicker.getValue(), deadlinePicker.getValue());

            projectService.updateProject(context.id(), projectNameTextField.getText(), owner.getId(), membersEditor.members(), info,
                    updated -> {
                        close();
                        NavigationManager.showToast("Проект обновлен", "success");
                    },
                    error -> NavigationManager.showToast("Ошибка обновления проекта", "error"));
        });
    }

    @Override
    public void setContext(Object context) {
        if (!(context instanceof ProjectView project)) {
            log.error("Context isn't an instance of ProjectView: {}", context);
            return;
        }

        this.context = project;
        projectNameTextField.setText(project.name());
        projectService.findInfo(project.id(), info -> {
            descriptionTextArea.setText(info.description());
            visibilityComboBox.setValue(info.visibility());
            startDatePicker.setValue(info.startDate());
            deadlinePicker.setValue(info.deadline());
        }, error -> NavigationManager.showToast("Ошибка загрузки проекта", "error"));

        userService.findActiveUsers(
                activeUsers -> {
                    users = activeUsers.stream().collect(Collectors.toMap(User::getId, Function.identity(),
                            (a, b) -> a, LinkedHashMap::new));
                    loadMembers(project);
                },
                error -> NavigationManager.showToast("Ошибка загрузки сотрудников", "error")
        );
    }

    private void loadMembers(ProjectView project) {
        projectService.findMembers(project.id(),
                current -> {
                    Map<UUID, MemberRole> members = new LinkedHashMap<>();
                    current.stream()
                            .filter(member -> member.role() != MemberRole.OWNER)
                            .forEach(member -> members.put(member.userId(), member.role()));
                    membersEditor.setUsers(users.values());
                    membersEditor.setMembers(members);

                    supervisorComboBox.getItems().setAll(users.values());
                    // Слушатель смены руководителя сработает и здесь: старого нет, новый — текущий руководитель
                    supervisorComboBox.setValue(users.get(project.ownerId()));
                },
                error -> NavigationManager.showToast("Ошибка загрузки участников", "error"));
    }

    private void close() {
        NavigationManager.closeForm();
    }
}

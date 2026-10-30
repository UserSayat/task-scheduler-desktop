package org.example.taskschedulerdesktop.project;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.example.taskschedulerdesktop.shared.navigation.ContextAware;
import org.example.taskschedulerdesktop.shared.navigation.NavigationManager;
import org.example.taskschedulerdesktop.user.AsyncUserService;
import org.example.taskschedulerdesktop.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
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

    /** Роли, которые можно выдать участнику (руководитель задаётся отдельно). */
    private static final List<MemberRole> ASSIGNABLE_ROLES = List.of(MemberRole.MANAGER, MemberRole.MEMBER, MemberRole.OBSERVER);

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

    /** Активные сотрудники по id — для имён в списке и выпадающих списков. */
    private Map<UUID, User> users = Map.of();

    /** Участники, кроме руководителя, в порядке показа. */
    private final Map<UUID, MemberRole> members = new LinkedHashMap<>();

    public EditProjectController(AsyncProjectService projectService, AsyncUserService userService) {
        this.projectService = projectService;
        this.userService = userService;
    }

    @FXML
    public void initialize() {
        visibilityComboBox.getItems().setAll(Visibility.values());
        newMemberRoleComboBox.getItems().setAll(ASSIGNABLE_ROLES);
        newMemberRoleComboBox.setValue(MemberRole.MEMBER);

        // Прежний руководитель остаётся в проекте менеджером, новый уходит из списка участников
        supervisorComboBox.valueProperty().addListener((obs, oldOwner, newOwner) -> {
            if (oldOwner != null && !members.containsKey(oldOwner.getId())) {
                members.put(oldOwner.getId(), MemberRole.MANAGER);
            }
            if (newOwner != null) {
                members.remove(newOwner.getId());
            }
            showMembers();
        });

        addMemberButton.setOnAction(event -> {
            User user = newMemberComboBox.getValue();
            if (user == null) {
                NavigationManager.showToast("Выберите сотрудника", "info");
                return;
            }
            members.put(user.getId(), newMemberRoleComboBox.getValue());
            showMembers();
        });

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

            projectService.updateProject(context.id(), projectNameTextField.getText(), owner.getId(), Map.copyOf(members), info,
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
                    members.clear();
                    current.stream()
                            .filter(member -> member.role() != MemberRole.OWNER)
                            .forEach(member -> members.put(member.userId(), member.role()));

                    supervisorComboBox.getItems().setAll(users.values());
                    // Слушатель смены руководителя сработает и здесь: старого нет, новый — текущий руководитель
                    supervisorComboBox.setValue(users.get(project.ownerId()));
                    showMembers();
                },
                error -> NavigationManager.showToast("Ошибка загрузки участников", "error"));
    }

    private void showMembers() {
        membersVBox.getChildren().clear();
        if (members.isEmpty()) {
            Label empty = new Label("Кроме руководителя, участников нет");
            empty.getStyleClass().add("members-empty");
            membersVBox.getChildren().add(empty);
        }
        members.forEach((userId, role) -> membersVBox.getChildren().add(memberRow(userId, role)));

        // В списке «добавить» — только те, кого ещё нет в проекте
        User owner = supervisorComboBox.getValue();
        newMemberComboBox.getItems().setAll(users.values().stream()
                .filter(user -> !members.containsKey(user.getId()))
                .filter(user -> owner == null || !user.getId().equals(owner.getId()))
                .toList());
        newMemberComboBox.setValue(null);
    }

    private HBox memberRow(UUID userId, MemberRole role) {
        User user = users.get(userId);
        // Заблокированного сотрудника нет среди активных, но из проекта он не пропадает
        Label name = new Label(user != null ? user.getName() : "Неактивный сотрудник");
        name.getStyleClass().add("member-name");
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);

        ComboBox<MemberRole> roleBox = new ComboBox<>();
        roleBox.getItems().setAll(ASSIGNABLE_ROLES);
        roleBox.setValue(role);
        roleBox.getStyleClass().add("combo-box-styled");
        roleBox.setPrefWidth(150);
        roleBox.valueProperty().addListener((obs, oldRole, newRole) -> members.put(userId, newRole));

        Button remove = new Button("×");
        remove.getStyleClass().add("member-remove-btn");
        remove.setOnAction(event -> {
            members.remove(userId);
            showMembers();
        });

        HBox row = new HBox(8, name, roleBox, remove);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void close() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        if (stage != null) {
            NavigationManager.closeDialog(stage);
        }
    }
}

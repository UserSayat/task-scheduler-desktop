package org.example.taskschedulerdesktop.team;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import org.example.taskschedulerdesktop.shared.ui.Animations;
import org.example.taskschedulerdesktop.shared.event.EventBus;
import org.example.taskschedulerdesktop.shared.navigation.NavigationManager;
import org.example.taskschedulerdesktop.shared.ui.Shutdownable;
import org.example.taskschedulerdesktop.task.TaskChangedEvent;
import org.example.taskschedulerdesktop.user.UsersChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * Экран «Команда»: активные сотрудники и их задачи. Сотрудников присылает сервер при синхронизации,
 * экран читает их из локальной базы и перечитывает, когда меняются задачи или сотрудники.
 */
public class TeamController implements Shutdownable {

    private static final Logger log = LoggerFactory.getLogger(TeamController.class);
    private static final String CARD = "/org/example/taskschedulerdesktop/view/employee_card.fxml";

    private final AsyncTeamService teamService;

    @FXML private TilePane teamTilePane;
    @FXML private Label emptyLabel;

    private boolean firstLoad = true;

    private final Consumer<TaskChangedEvent> taskListener = event -> loadTeam();
    private final Consumer<UsersChangedEvent> usersListener = event -> loadTeam();

    public TeamController(AsyncTeamService teamService) {
        this.teamService = teamService;
    }

    @FXML
    public void initialize() {
        EventBus.getInstance().subscribe(TaskChangedEvent.class, taskListener);
        EventBus.getInstance().subscribe(UsersChangedEvent.class, usersListener);
        loadTeam();
    }

    private void loadTeam() {
        teamService.findTeam(
                this::showTeam,
                error -> NavigationManager.showToast("Не удалось загрузить сотрудников", "error"));
    }

    private void showTeam(List<TeamMember> team) {
        teamTilePane.getChildren().clear();
        emptyLabel.setVisible(team.isEmpty());
        emptyLabel.setManaged(team.isEmpty());

        try {
            for (TeamMember member : team) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(CARD));
                VBox card = loader.load();
                Animations.hoverLift(card);
                EmployeeCardController cardController = loader.getController();
                cardController.setMember(member);
                teamTilePane.getChildren().add(card);
            }
        } catch (IOException e) {
            log.error("Employee card loading failed", e);
        }
        if (firstLoad) {
            Animations.staggerIn(teamTilePane.getChildren());
            firstLoad = false;
        }
    }

    @Override
    public void shutdown() {
        EventBus.getInstance().unsubscribe(TaskChangedEvent.class, taskListener);
        EventBus.getInstance().unsubscribe(UsersChangedEvent.class, usersListener);
    }
}

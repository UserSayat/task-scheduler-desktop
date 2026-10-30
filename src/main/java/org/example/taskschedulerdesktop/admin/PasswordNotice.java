package org.example.taskschedulerdesktop.admin;

import org.example.taskschedulerdesktop.shared.ui.Dialogs;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * Показывает администратору временный пароль, чтобы передать его сотруднику.
 * Пароль в поле можно выделить и скопировать; больше он нигде не хранится.
 */
final class PasswordNotice {

    private PasswordNotice() {}

    static void show(Window owner, String employeeName, String temporaryPassword) {
        TextField password = new TextField(temporaryPassword);
        password.setEditable(false);
        password.getStyleClass().add("text-field-styled");

        Label hint = new Label("Передайте пароль сотруднику. После закрытия окна его больше не будет видно.");
        hint.setWrapText(true);

        VBox content = new VBox(8, password, hint);
        content.setPrefWidth(380);
        Dialogs.inform(owner, "Временный пароль", "Временный пароль для «" + employeeName + "»", content);
    }
}

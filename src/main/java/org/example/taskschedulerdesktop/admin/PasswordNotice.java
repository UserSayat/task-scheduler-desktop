package org.example.taskschedulerdesktop.admin;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
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

        Alert alert = new Alert(Alert.AlertType.INFORMATION, null, ButtonType.OK);
        alert.setTitle("Временный пароль");
        alert.setHeaderText("Временный пароль для «" + employeeName + "»");
        alert.getDialogPane().setContent(new VBox(8, password, hint));
        alert.initOwner(owner);
        alert.showAndWait();
    }
}

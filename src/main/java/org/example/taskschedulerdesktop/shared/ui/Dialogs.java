package org.example.taskschedulerdesktop.shared.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Небольшие окна «подтвердить / сообщить» в рамке приложения — вместо системных {@code Alert}.
 * Enter — основная кнопка, Esc — отмена.
 */
public final class Dialogs {

    private Dialogs() {}

    /** Спросить подтверждение. {@code header} можно не задавать. */
    public static boolean confirm(Window owner, String title, String header, String text) {
        boolean[] result = {false};
        Button cancel = new Button("Отмена");
        cancel.getStyleClass().add("btn-secondary");
        cancel.setCancelButton(true);
        Button ok = new Button("OK");
        ok.getStyleClass().add("btn-primary");
        ok.setDefaultButton(true);

        Stage stage = stage(owner, title != null ? title : "Подтверждение");
        cancel.setOnAction(event -> stage.close());
        ok.setOnAction(event -> {
            result[0] = true;
            stage.close();
        });
        show(stage, body(header, text(text), cancel, ok));
        return result[0];
    }

    /** Сообщение с кнопкой «OK»; {@code content} — что угодно под заголовком. */
    public static void inform(Window owner, String title, String header, Node content) {
        Button ok = new Button("OK");
        ok.getStyleClass().add("btn-primary");
        ok.setDefaultButton(true);
        ok.setCancelButton(true);
        Stage stage = stage(owner, title);
        ok.setOnAction(event -> stage.close());
        show(stage, body(header, content, ok));
    }

    private static Stage stage(Window owner, String title) {
        Stage stage = new Stage();
        stage.setTitle(title);
        stage.setResizable(false);
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        } else {
            stage.initModality(Modality.APPLICATION_MODAL);
        }
        return stage;
    }

    private static Label text(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add("dialog-text");
        return label;
    }

    private static VBox body(String header, Node content, Button... buttons) {
        VBox body = new VBox(12);
        body.getStyleClass().add("dialog-body");
        if (header != null && !header.isBlank()) {
            Label headerLabel = new Label(header);
            headerLabel.setWrapText(true);
            headerLabel.getStyleClass().add("dialog-header");
            body.getChildren().add(headerLabel);
        }
        HBox actions = new HBox(8, buttons);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getStyleClass().add("dialog-actions");
        body.getChildren().addAll(content, actions);
        return body;
    }

    private static void show(Stage stage, VBox body) {
        Scene scene = DialogFrame.scene(stage, body);
        stage.setScene(scene);
        stage.setOnShown(event -> Animations.popIn(scene.getRoot()));
        stage.showAndWait();
    }
}

package org.example.taskschedulerdesktop.shared.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;

import java.util.Locale;

/**
 * Своя рамка для диалоговых окон — в том же стиле, что у главного окна: тёмный заголовок с названием,
 * кнопка закрытия по правилам платформы, тень и скруглённые углы. Окно перетаскивается за заголовок.
 * <p>
 * Здесь же общие для всех рамок настройки: включена ли своя рамка и в каком стиле рисовать кнопки.
 */
public final class DialogFrame {

    /** Включена ли своя рамка. Системная возвращается флагом {@code -Dtaskhub.nativeFrame=true}. */
    public static boolean enabled() {
        return !Boolean.getBoolean("taskhub.nativeFrame");
    }

    /**
     * Кнопки окна в стиле macOS или Windows — по системе. Для проверки вида можно задать явно:
     * {@code -Dtaskhub.frameStyle=mac} или {@code windows}.
     */
    public static final boolean MAC = switch (System.getProperty("taskhub.frameStyle", "").toLowerCase(Locale.ROOT)) {
        case "mac" -> true;
        case "windows" -> false;
        default -> System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    };

    /** Отступ под тень вокруг окна. */
    public static final double SHADOW = 14;
    /** Радиус скругления углов окна — как у окон системы. */
    public static final double RADIUS = MAC ? 10 : 8;
    public static final double TITLE_HEIGHT = MAC ? 30 : 32;
    /** Крестик Windows 11 (10×10). */
    public static final String CLOSE_ICON =
            "M0.7 0L5 4.3 9.3 0l0.7 0.7L5.7 5l4.3 4.3-0.7 0.7L5 5.7 0.7 10 0 9.3 4.3 5 0 0.7z";

    private static final String STYLESHEET = "/org/example/taskschedulerdesktop/styles/style.css";

    private DialogFrame() {}

    /**
     * Сцена диалога: содержимое в своей рамке (или как есть, если своя рамка выключена).
     * Вызывать до показа окна — стиль окна потом не поменять. Заголовок берётся из {@code stage.getTitle()}.
     */
    public static Scene scene(Stage stage, Parent content) {
        Scene scene;
        if (enabled()) {
            stage.initStyle(StageStyle.TRANSPARENT);
            scene = new Scene(build(stage, content));
            scene.setFill(Color.TRANSPARENT);
        } else {
            scene = new Scene(content);
        }
        scene.getStylesheets().add(DialogFrame.class.getResource(STYLESHEET).toExternalForm());
        return scene;
    }

    /** Слой с тенью под окном: отдельный, чтобы тень не перерисовывалась при изменении содержимого. */
    public static Region shadowLayer() {
        Region shadow = new Region();
        shadow.getStyleClass().add("window-shadow");
        DropShadow drop = new DropShadow(SHADOW * 1.4, Color.rgb(0, 0, 0, 0.28));
        drop.setOffsetY(3);
        shadow.setEffect(drop);
        shadow.setStyle("-fx-background-color: #f6f6f4; -fx-background-radius: " + RADIUS + ";");
        return shadow;
    }

    /** Обрезка по скруглённому прямоугольнику размером с узел. */
    public static void clipRounded(Region region) {
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(region.widthProperty());
        clip.heightProperty().bind(region.heightProperty());
        clip.setArcWidth(RADIUS * 2);
        clip.setArcHeight(RADIUS * 2);
        region.setClip(clip);
    }

    /** Кнопка окна в стиле Windows 11. */
    public static Button windowButton(String icon, String tooltip, Runnable action, boolean close) {
        SVGPath glyph = new SVGPath();
        glyph.setContent(icon);
        glyph.getStyleClass().add("window-glyph");
        Button button = new Button();
        button.setGraphic(glyph);
        button.getStyleClass().addAll("window-button", close ? "window-button-close" : "window-button-plain");
        button.setFocusTraversable(false);
        button.setTooltip(new Tooltip(tooltip));
        button.setOnAction(event -> action.run());
        return button;
    }

    /** Круглая кнопка окна в стиле macOS. */
    public static Button light(String style, Runnable action, String tooltip) {
        Button button = new Button();
        button.getStyleClass().addAll("window-light", style);
        button.setFocusTraversable(false);
        if (tooltip != null) {
            button.setTooltip(new Tooltip(tooltip));
        }
        if (action != null) {
            button.setOnAction(event -> action.run());
        } else {
            button.getStyleClass().add("window-light-disabled"); // в диалогах свернуть и развернуть нельзя
            button.setMouseTransparent(true);
        }
        return button;
    }

    /** Закрыть окно так же, как системной кнопкой: сначала запрос на закрытие, потом закрытие. */
    public static void requestClose(Stage stage) {
        stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
        stage.close();
    }

    // ===== Построение =====

    private static Parent build(Stage stage, Parent content) {
        StackPane root = new StackPane();
        root.getStyleClass().add("window-root");
        root.setPadding(new Insets(SHADOW));

        VBox window = new VBox(titleBar(stage), content);
        window.getStyleClass().addAll("window", "dialog-window");
        VBox.setVgrow(content, Priority.ALWAYS);
        clipRounded(window);

        root.getChildren().addAll(shadowLayer(), window);
        return root;
    }

    private static Node titleBar(Stage stage) {
        Label title = new Label();
        title.textProperty().bind(stage.titleProperty());
        title.getStyleClass().add("dialog-title");

        StackPane bar = new StackPane();
        bar.getStyleClass().addAll("window-titlebar", "dialog-titlebar");
        bar.setMinHeight(TITLE_HEIGHT);
        bar.setPrefHeight(TITLE_HEIGHT);
        bar.setMaxHeight(TITLE_HEIGHT);

        if (MAC) {
            // Как в macOS: кнопки слева, название по центру
            HBox lights = new HBox(8, light("window-light-close", () -> requestClose(stage), "Закрыть"),
                    light("window-light-minimize", null, null),
                    light("window-light-zoom", null, null));
            lights.setAlignment(Pos.CENTER_LEFT);
            lights.setPadding(new Insets(0, 0, 0, 13));
            lights.getStyleClass().add("window-lights");
            lights.setMaxWidth(Region.USE_PREF_SIZE);
            title.setMaxWidth(Double.MAX_VALUE);
            title.setAlignment(Pos.CENTER);
            StackPane.setMargin(title, new Insets(0, 76, 0, 76));
            bar.getChildren().addAll(title, lights);
            StackPane.setAlignment(lights, Pos.CENTER_LEFT);
        } else {
            // Как в Windows: название слева, крестик справа
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox row = new HBox(title, spacer,
                    windowButton(CLOSE_ICON, "Закрыть", () -> requestClose(stage), true));
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(0, 0, 0, 14));
            bar.getChildren().add(row);
        }
        makeDraggable(bar, stage);
        return bar;
    }

    private static void makeDraggable(Node bar, Stage stage) {
        double[] offset = new double[2];
        bar.setOnMousePressed(event -> {
            offset[0] = event.getScreenX() - stage.getX();
            offset[1] = event.getScreenY() - stage.getY();
        });
        bar.setOnMouseDragged(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                stage.setX(event.getScreenX() - offset[0]);
                stage.setY(event.getScreenY() - offset[1]);
            }
        });
    }
}

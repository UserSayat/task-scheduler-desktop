package org.example.taskschedulerdesktop.shell;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.example.taskschedulerdesktop.shared.ui.DialogFrame;

import java.util.List;

/**
 * Своя рамка главного окна вместо системной: заголовок в цветах приложения, кнопки окна по правилам
 * платформы (macOS — три круглые слева, Windows — справа), перетаскивание, разворачивание двойным щелчком
 * и перетаскиванием к верхнему краю экрана, изменение размера за края, тень и скруглённые углы.
 * <p>
 * Над левым меню заголовок тёмный, над рабочей областью — светлый, поэтому он сливается с интерфейсом.
 * Системная рамка возвращается флагом {@code -Dtaskhub.nativeFrame=true}.
 */
public class WindowFrame {

    /** Включена ли своя рамка. Решается до показа окна: стиль окна потом не поменять. */
    public static boolean enabled() {
        return DialogFrame.enabled();
    }

    /**
     * Кнопки окна в стиле macOS или Windows — по системе. Для проверки вида можно задать явно:
     * {@code -Dtaskhub.frameStyle=mac} или {@code windows}.
     */
    private static final boolean MAC = DialogFrame.MAC;

    /** Отступ под тень вокруг окна (когда окно не развёрнуто). */
    private static final double SHADOW = DialogFrame.SHADOW;
    /** Углы скруглены всегда — и в окне, и в развёрнутом виде, как у IntelliJ IDEA. */
    private static final double RADIUS = DialogFrame.RADIUS;
    /** Ширина зоны у края, за которую тянется размер окна. */
    private static final double GRIP = 6;
    private static final double TITLE_HEIGHT = DialogFrame.TITLE_HEIGHT;

    private final Stage stage;
    private final StackPane root = new StackPane();
    private final Region shadow = new Region();
    private final VBox window = new VBox();
    private final Region outline = DialogFrame.outlineLayer();
    private final Region titleLeft = new Region();
    private HBox titleBar;
    /** Кнопки окна: на Windows — «свернуть / развернуть / закрыть», на macOS — три круглые. */
    private HBox controls;
    /** Где кнопки стоят в собственном заголовке рамки (для экрана входа). */
    private javafx.scene.layout.Pane controlsHome;
    /** Полоса экрана, в которую встроены кнопки окна, или null. */
    private javafx.scene.layout.Pane attachedHeader;
    private Insets logoPaddingBefore;
    private HBox attachedLogo;
    private javafx.scene.layout.Pane attachedRight;
    private final StackPane content = new StackPane();
    private final Rectangle clip = new Rectangle();
    private Button maximizeButton;

    private boolean maximized;
    private Rectangle2D restoreBounds;

    // Перетаскивание и изменение размера
    private double dragOffsetX;
    private double dragOffsetY;
    private Cursor resizeCursor = Cursor.DEFAULT;
    private double pressScreenX;
    private double pressScreenY;
    private Rectangle2D pressBounds;

    public WindowFrame(Stage stage) {
        this.stage = stage;
        stage.initStyle(StageStyle.TRANSPARENT);
        build();
    }

    /** Корень сцены: окно с тенью. */
    public Parent root() {
        return root;
    }

    /** Показать экран внутри рамки (вход или рабочее пространство). */
    public void setContent(Node node) {
        content.getChildren().setAll(node);
    }

    /**
     * Ширина левого меню: над ним заголовок тёмный. 0 — заголовок целиком светлый (экран входа).
     */
    public void setSidebarWidth(double width) {
        titleLeft.setMinWidth(width);
        titleLeft.setPrefWidth(width);
        titleLeft.setMaxWidth(width);
        titleLeft.setVisible(width > 0);
        titleLeft.setManaged(width > 0);
        // Без меню (экран входа) заголовок сливается с фоном экрана, а не с белой шапкой
        titleBar.getStyleClass().remove("window-titlebar-plain");
        if (width <= 0) {
            titleBar.getStyleClass().add("window-titlebar-plain");
        }
    }

    /** Подготовить сцену: прозрачный фон, иначе не видно тени и скруглений. */
    public void install(Scene scene) {
        scene.setFill(Color.TRANSPARENT);
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, this::updateResizeCursor);
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, this::startResize);
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::resize);
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> resizeCursor = Cursor.DEFAULT);
    }

    /** Размер окна вместе с полями под тень. */
    public static double outerSize(double size) {
        return enabled() ? size + 2 * SHADOW : size;
    }

    // ===== Построение =====

    private void build() {
        root.getStyleClass().add("window-root");
        root.setPadding(new Insets(SHADOW));

        shadow.getStyleClass().add("window-shadow");
        DropShadow drop = new DropShadow(SHADOW * 1.4, Color.rgb(0, 0, 0, 0.28));
        drop.setOffsetY(3);
        shadow.setEffect(drop); // тень у отдельного слоя: содержимое меняется, а тень не перерисовывается
        shadow.setBackground(null);
        shadow.setStyle("-fx-background-color: #f6f6f4; -fx-background-radius: " + RADIUS + ";");

        window.getStyleClass().add("window");
        clip.widthProperty().bind(window.widthProperty());
        clip.heightProperty().bind(window.heightProperty());
        clip.setArcWidth(RADIUS * 2);
        clip.setArcHeight(RADIUS * 2);
        window.setClip(clip);

        VBox.setVgrow(content, Priority.ALWAYS);
        window.getChildren().addAll(titleBar(), content);
        root.getChildren().addAll(shadow, window, outline);

        stage.maximizedProperty().addListener((obs, was, now) -> {
            // Системные сочетания (например, Win+↑) разворачивают окно сами — подстраиваем рамку
            if (now && !maximized) {
                stage.setMaximized(false);
                maximize();
            }
        });
        // На весь экран — прямоугольное окно без тени; при выходе возвращается прежний вид
        stage.fullScreenProperty().addListener((obs, was, now) -> {
            setFrameless(now || maximized);
            setRounded(!now);
        });
    }

    private Node titleBar() {
        HBox bar = new HBox();
        titleBar = bar;
        bar.getStyleClass().add("window-titlebar");
        bar.setMinHeight(TITLE_HEIGHT);
        bar.setPrefHeight(TITLE_HEIGHT);
        bar.setMaxHeight(TITLE_HEIGHT);
        bar.setAlignment(Pos.CENTER_LEFT);

        titleLeft.getStyleClass().add("window-titlebar-dark");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        if (MAC) {
            // Кнопки поверх тёмной части, как в приложениях macOS с боковой панелью
            controls = new HBox(8, light("window-light-close", this::close, "Закрыть"),
                    light("window-light-minimize", () -> stage.setIconified(true), "Свернуть"),
                    light("window-light-zoom", this::toggleFullScreen, "На весь экран"));
            controls.setAlignment(Pos.CENTER_LEFT);
            controls.setPadding(new Insets(0, 0, 0, 13));
            controls.getStyleClass().add("window-lights");
            controls.setMaxWidth(Region.USE_PREF_SIZE);
            StackPane left = new StackPane(titleLeft, controls);
            left.setAlignment(Pos.CENTER_LEFT);
            bar.getChildren().addAll(left, spacer);
            controlsHome = left;
            // Пока меню нет (экран входа), кнопкам нужна своя подложка
            left.minWidthProperty().bind(titleLeft.minWidthProperty().map(w -> Math.max(w.doubleValue(), 76)));
        } else {
            maximizeButton = windowButton(MAXIMIZE, "Развернуть", this::toggleMaximize, false);
            controls = new HBox(windowButton(MINIMIZE, "Свернуть", () -> stage.setIconified(true), false),
                    maximizeButton,
                    windowButton(CLOSE, "Закрыть", this::close, true));
            controls.setAlignment(Pos.CENTER);
            controls.getStyleClass().add("window-controls");
            bar.getChildren().addAll(titleLeft, spacer, controls);
            controlsHome = bar;
        }
        makeDraggable(bar);
        return bar;
    }

    /**
     * Встроить кнопки окна в верхнюю полосу экрана (как в макете: логотип, синхронизация, профиль и кнопки
     * окна в одной строке) и сделать её перетаскиваемой. Собственный заголовок рамки при этом прячется.
     *
     * @param header    полоса целиком: за неё окно перетаскивается
     * @param logoCell  левая ячейка над меню: на macOS кнопки встают в неё слева
     * @param logo      логотип в этой ячейке: на macOS сдвигается вправо, освобождая место кнопкам
     * @param rightEdge правая часть полосы: на Windows кнопки встают в её конец
     */
    public void attachHeader(javafx.scene.layout.Pane header, StackPane logoCell, HBox logo, javafx.scene.layout.Pane rightEdge) {
        detachHeader();
        titleBar.setVisible(false);
        titleBar.setManaged(false);
        if (MAC) {
            controls.getStyleClass().add("window-lights-in-header");
            logoCell.getChildren().add(controls);
            StackPane.setAlignment(controls, Pos.CENTER_LEFT);
            logoPaddingBefore = logo.getPadding();
            attachedLogo = logo;
            logo.setPadding(new Insets(0, 0, 0, 96));
        } else {
            controls.getStyleClass().add("window-controls-in-header");
            rightEdge.getChildren().add(controls);
            rightEdge.getStyleClass().add("header-with-window-controls"); // кнопки окна — вплотную к краю
            attachedRight = rightEdge;
        }
        attachedHeader = header;
        makeDraggable(header);
    }

    /** Вернуть кнопки окна в собственный заголовок рамки (экран входа). */
    public void detachHeader() {
        if (attachedHeader == null) {
            return;
        }
        controls.getStyleClass().removeAll("window-lights-in-header", "window-controls-in-header");
        ((javafx.scene.layout.Pane) controls.getParent()).getChildren().remove(controls);
        controlsHome.getChildren().add(controls);
        if (attachedRight != null) {
            attachedRight.getStyleClass().remove("header-with-window-controls");
            attachedRight = null;
        }
        if (attachedLogo != null) {
            attachedLogo.setPadding(logoPaddingBefore);
            attachedLogo = null;
        }
        attachedHeader = null;
        titleBar.setVisible(true);
        titleBar.setManaged(true);
    }

    /** Перетаскивание окна за узел, двойной щелчок — развернуть, отпустили у верхнего края — развернуть. */
    private void makeDraggable(Node bar) {
        bar.setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.PRIMARY && resizeCursor == Cursor.DEFAULT) {
                dragOffsetX = event.getScreenX() - stage.getX();
                dragOffsetY = event.getScreenY() - stage.getY();
            }
        });
        bar.setOnMouseDragged(this::drag);
        bar.setOnMouseReleased(this::snapToTop);
        bar.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                toggleMaximize();
            }
        });
    }

    // Иконки кнопок Windows 11 (контуры 10×10)
    private static final String MINIMIZE = "M0 5h10v1H0z";
    private static final String MAXIMIZE = "M0 0h10v10H0V0zm1 1v8h8V1H1z";
    private static final String RESTORE = "M2 0h8v8H8v2H0V2h2V0zm1 2h5v5h1V1H3v1zM1 3v6h6V3H1z";
    private static final String CLOSE = "M0.7 0L5 4.3 9.3 0l0.7 0.7L5.7 5l4.3 4.3-0.7 0.7L5 5.7 0.7 10 0 9.3 4.3 5 0 0.7z";

    private Button windowButton(String icon, String tooltip, Runnable action, boolean close) {
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

    private Button light(String style, Runnable action, String tooltip) {
        Button button = new Button();
        button.getStyleClass().addAll("window-light", style);
        button.setFocusTraversable(false);
        button.setTooltip(new Tooltip(tooltip));
        button.setOnAction(event -> action.run());
        return button;
    }

    // ===== Действия окна =====

    private void close() {
        DialogFrame.requestClose(stage);
    }

    private void toggleFullScreen() {
        stage.setFullScreen(!stage.isFullScreen());
    }

    private void toggleMaximize() {
        if (maximized) {
            restore();
        } else {
            maximize();
        }
    }

    /** Развернуть на рабочую область экрана (не закрывая панель задач и Dock). */
    private void maximize() {
        restoreBounds = new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
        Rectangle2D area = currentScreen().getVisualBounds();
        maximized = true;
        setFrameless(true);
        stage.setX(area.getMinX());
        stage.setY(area.getMinY());
        stage.setWidth(area.getWidth());
        stage.setHeight(area.getHeight());
        updateMaximizeIcon();
    }

    private void restore() {
        maximized = false;
        setFrameless(false);
        if (restoreBounds != null) {
            stage.setX(restoreBounds.getMinX());
            stage.setY(restoreBounds.getMinY());
            stage.setWidth(restoreBounds.getWidth());
            stage.setHeight(restoreBounds.getHeight());
        }
        updateMaximizeIcon();
    }

    /** Развёрнутое окно — без тени, вплотную к краям экрана. Скругление углов не меняется. */
    private void setFrameless(boolean frameless) {
        root.setPadding(frameless ? Insets.EMPTY : new Insets(SHADOW));
        shadow.setVisible(!frameless);
    }

    /** Скруглённые углы окна; на весь экран — прямые, иначе по углам видно то, что под окном. */
    private void setRounded(boolean rounded) {
        double arc = rounded ? RADIUS * 2 : 0;
        clip.setArcWidth(arc);
        clip.setArcHeight(arc);
        outline.setVisible(rounded);
    }

    private void updateMaximizeIcon() {
        if (maximizeButton != null) {
            ((SVGPath) maximizeButton.getGraphic()).setContent(maximized ? RESTORE : MAXIMIZE);
            maximizeButton.getTooltip().setText(maximized ? "Свернуть в окно" : "Развернуть");
        }
    }

    private Screen currentScreen() {
        double centerX = stage.getX() + stage.getWidth() / 2;
        double centerY = stage.getY() + stage.getHeight() / 2;
        List<Screen> screens = Screen.getScreensForRectangle(centerX, centerY, 1, 1);
        return screens.isEmpty() ? Screen.getPrimary() : screens.get(0);
    }

    // ===== Перетаскивание =====

    private void drag(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY || resizeCursor != Cursor.DEFAULT || stage.isFullScreen()) {
            return;
        }
        if (maximized) {
            // Как в системе: потянули развёрнутое окно — оно возвращается к прежнему размеру,
            // а курсор остаётся на том же месте заголовка (в той же доле ширины)
            double ratio = dragOffsetX / stage.getWidth();
            restore();
            dragOffsetX = SHADOW + (stage.getWidth() - 2 * SHADOW) * ratio;
            dragOffsetY = SHADOW + Math.min(dragOffsetY, 24);
        }
        stage.setX(event.getScreenX() - dragOffsetX);
        stage.setY(event.getScreenY() - dragOffsetY);
    }

    /** Отпустили заголовок у верхнего края экрана — окно разворачивается. */
    private void snapToTop(MouseEvent event) {
        if (maximized || resizeCursor != Cursor.DEFAULT || event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        Rectangle2D area = currentScreen().getVisualBounds();
        if (event.getScreenY() <= area.getMinY() + 2) {
            maximize();
        }
    }

    // ===== Изменение размера за края =====

    private void updateResizeCursor(MouseEvent event) {
        if (maximized || stage.isFullScreen()) {
            root.setCursor(Cursor.DEFAULT);
            return;
        }
        Cursor cursor = cursorAt(event.getSceneX(), event.getSceneY());
        root.setCursor(cursor == Cursor.DEFAULT ? null : cursor);
    }

    /** Курсор по положению относительно краёв окна (без полей под тень). */
    private Cursor cursorAt(double x, double y) {
        double left = SHADOW;
        double top = SHADOW;
        double right = stage.getScene().getWidth() - SHADOW;
        double bottom = stage.getScene().getHeight() - SHADOW;
        boolean w = x >= left - GRIP && x <= left + GRIP;
        boolean e = x >= right - GRIP && x <= right + GRIP;
        boolean n = y >= top - GRIP && y <= top + GRIP;
        boolean s = y >= bottom - GRIP && y <= bottom + GRIP;
        if (n && w) return Cursor.NW_RESIZE;
        if (n && e) return Cursor.NE_RESIZE;
        if (s && w) return Cursor.SW_RESIZE;
        if (s && e) return Cursor.SE_RESIZE;
        if (w) return Cursor.W_RESIZE;
        if (e) return Cursor.E_RESIZE;
        if (n) return Cursor.N_RESIZE;
        if (s) return Cursor.S_RESIZE;
        return Cursor.DEFAULT;
    }

    private void startResize(MouseEvent event) {
        resizeCursor = maximized || stage.isFullScreen() ? Cursor.DEFAULT : cursorAt(event.getSceneX(), event.getSceneY());
        if (resizeCursor != Cursor.DEFAULT) {
            pressScreenX = event.getScreenX();
            pressScreenY = event.getScreenY();
            pressBounds = new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
            event.consume();
        }
    }

    private void resize(MouseEvent event) {
        if (resizeCursor == Cursor.DEFAULT) {
            return;
        }
        event.consume();
        double dx = event.getScreenX() - pressScreenX;
        double dy = event.getScreenY() - pressScreenY;
        double minW = stage.getMinWidth();
        double minH = stage.getMinHeight();
        double x = pressBounds.getMinX();
        double y = pressBounds.getMinY();
        double width = pressBounds.getWidth();
        double height = pressBounds.getHeight();

        if (resizeCursor == Cursor.E_RESIZE || resizeCursor == Cursor.NE_RESIZE || resizeCursor == Cursor.SE_RESIZE) {
            width = Math.max(minW, pressBounds.getWidth() + dx);
        }
        if (resizeCursor == Cursor.S_RESIZE || resizeCursor == Cursor.SE_RESIZE || resizeCursor == Cursor.SW_RESIZE) {
            height = Math.max(minH, pressBounds.getHeight() + dy);
        }
        if (resizeCursor == Cursor.W_RESIZE || resizeCursor == Cursor.NW_RESIZE || resizeCursor == Cursor.SW_RESIZE) {
            width = Math.max(minW, pressBounds.getWidth() - dx);
            x = pressBounds.getMaxX() - width;
        }
        if (resizeCursor == Cursor.N_RESIZE || resizeCursor == Cursor.NW_RESIZE || resizeCursor == Cursor.NE_RESIZE) {
            height = Math.max(minH, pressBounds.getHeight() - dy);
            y = pressBounds.getMaxY() - height;
        }
        stage.setX(x);
        stage.setY(y);
        stage.setWidth(width);
        stage.setHeight(height);
    }
}

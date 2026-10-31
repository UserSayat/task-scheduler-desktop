package org.example.taskschedulerdesktop.shell;

import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import org.example.taskschedulerdesktop.shared.navigation.NavigationManager;

import java.util.function.BooleanSupplier;

/**
 * «Назад» без кнопки на экране, как в браузере и Finder:
 * <ul>
 *   <li>боковая кнопка мыши «Назад»;</li>
 *   <li>Alt+← (Windows) и Cmd+[ (macOS);</li>
 *   <li>свайп двумя пальцами вправо по тачпаду — только там, где нет горизонтальной прокрутки;</li>
 *   <li>Esc закрывает открытую панель задачи.</li>
 * </ul>
 * Если открыта панель задачи, «Назад» сначала закрывает её.
 */
public final class BackNavigation {

    private static final KeyCombination ALT_LEFT = new KeyCodeCombination(KeyCode.LEFT, KeyCombination.ALT_DOWN);
    private static final KeyCombination CMD_BRACKET = new KeyCodeCombination(KeyCode.OPEN_BRACKET, KeyCombination.SHORTCUT_DOWN);

    /** Сколько пикселей нужно провести двумя пальцами, чтобы это был жест «Назад». */
    static final double SWIPE_DISTANCE = 140;
    /** Пауза между событиями прокрутки, после которой жест начинается заново. */
    private static final long GESTURE_GAP_MS = 250;
    /** После срабатывания жест «молчит», пока не закончится инерция прокрутки. */
    private static final long COOLDOWN_MS = 700;

    private final BooleanSupplier active;
    private double swipe;
    private long lastScroll;
    private long cooldownUntil;

    private BackNavigation(BooleanSupplier active) {
        this.active = active;
    }

    /**
     * @param active включено ли сейчас (только на главном экране, не на экране входа)
     */
    public static void install(Scene scene, BooleanSupplier active) {
        BackNavigation back = new BackNavigation(active);
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, back::onMouse);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, back::onKey);
        scene.addEventFilter(ScrollEvent.SCROLL, back::onScroll);
    }

    /** Закрыть панель задачи, а если её нет — вернуться на предыдущую страницу. */
    static boolean goBack() {
        if (NavigationManager.isRightSidebarOpen()) {
            NavigationManager.closeRightSidebar();
            return true;
        }
        if (NavigationManager.canGoBack()) {
            NavigationManager.goBack();
            return true;
        }
        return false;
    }

    private void onMouse(MouseEvent event) {
        if (event.getButton() == MouseButton.BACK && active.getAsBoolean()) {
            goBack();
            event.consume();
        }
    }

    private void onKey(KeyEvent event) {
        if (!active.getAsBoolean()) {
            return;
        }
        if (ALT_LEFT.match(event) || CMD_BRACKET.match(event)) {
            goBack();
            event.consume();
        } else if (event.getCode() == KeyCode.ESCAPE && NavigationManager.isRightSidebarOpen()) {
            NavigationManager.closeRightSidebar();
            event.consume();
        }
    }

    private void onScroll(ScrollEvent event) {
        long now = System.currentTimeMillis();
        if (now - lastScroll > GESTURE_GAP_MS) {
            swipe = 0; // новый жест
        }
        lastScroll = now;
        // Shift+колесо тоже даёт горизонтальную прокрутку — это не жест
        if (!active.getAsBoolean() || event.isShiftDown() || now < cooldownUntil) {
            return;
        }
        double dx = event.getDeltaX();
        double dy = event.getDeltaY();
        if (Math.abs(dx) <= Math.abs(dy)) {
            swipe = 0; // движение скорее вертикальное — это прокрутка страницы
            return;
        }
        if (scrollsHorizontally(event.getPickResult().getIntersectedNode())) {
            swipe = 0; // здесь горизонтальный свайп прокручивает содержимое
            return;
        }
        swipe += dx;
        if (swipe >= SWIPE_DISTANCE) {
            swipe = 0;
            cooldownUntil = now + COOLDOWN_MS;
            if (goBack()) {
                event.consume();
            }
        } else if (swipe < 0) {
            swipe = 0; // свайп в другую сторону «Вперёд» пока не используется
        }
    }

    /** Есть ли над узлом (или в нём самом) видимая горизонтальная полоса прокрутки. */
    static boolean scrollsHorizontally(Node node) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (current instanceof Parent parent) {
                for (Node child : parent.getChildrenUnmodifiable()) {
                    if (child instanceof ScrollBar bar && bar.getOrientation() == Orientation.HORIZONTAL
                            && bar.isVisible() && bar.getVisibleAmount() < bar.getMax() - bar.getMin()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}

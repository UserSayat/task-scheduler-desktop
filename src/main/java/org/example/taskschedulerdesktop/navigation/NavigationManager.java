package org.example.taskschedulerdesktop.navigation;

import javafx.animation.TranslateTransition;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.Duration;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Центральный менеджер навигации.
 * Управляет сменой страниц, открытием диалогов и передачей контекста.
 */
public class NavigationManager {

    private static StackPane contentArea;
    private static Callback<Class<?>, Object> controllerFactory;
    private static Parent rightSidebar = null;
    private static final double RIGHT_SIDEBAR_WIDTH = 300.0;

    // История переходов
    private static final Stack<String> history = new Stack<>();
    private static String currentPage = null;

    private static final List<Runnable> listeners = new ArrayList<>();

    private static boolean isBackNavigation = false;

    private static String currentTitle = "";

    // ============================================================
    // ИНИЦИАЛИЗАЦИЯ
    // ============================================================

    public static void init(StackPane contentArea, Callback<Class<?>, Object> controllerFactory) {
        NavigationManager.contentArea = contentArea;
        NavigationManager.controllerFactory = controllerFactory;
    }

    // ============================================================
    // РЕГИСТРАЦИЯ СЛУШАТЕЛЕЙ
    // ============================================================

    public static void addListener(Runnable listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public static void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private static void notifyListeners() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }



    //TODO Настроить правильное отображение заголовка в верхнем сайдбаре
    //TODO Настроить кнопку создать задачу






    // ============================================================
    // НАВИГАЦИЯ ПО СТРАНИЦАМ
    // ============================================================

    public static void navigateTo(String fxmlPath) {
        navigateTo(fxmlPath, null);
    }

    public static void navigateTo(String fxmlPath, String title) {
        navigateTo(fxmlPath, title, null);
    }

    public static void navigateTo(String fxmlPath, String title, Object context) {
        // Сохраняем текущую страницу в историю (если есть)
        if (!isBackNavigation && currentPage != null && !fxmlPath.equals(currentPage)) {
            history.push(currentPage);
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    NavigationManager.class.getResource(fxmlPath)
            );
            loader.setControllerFactory(controllerFactory);

            Parent page = loader.load();

            // Передаем контекст, если контроллер умеет его принимать
            if (loader.getController() instanceof ContextAware aware) {
                aware.setContext(context);
            }

            contentArea.getChildren().setAll(page);
            currentPage = fxmlPath;

            if (title != null) {
                currentTitle = title;
            }

            notifyListeners();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ============================================================
    // КНОПКА НАЗАД
    // ============================================================

    /**
     * Возврат на предыдущую страницу.
     */
    public static void goBack() {
        if (!history.isEmpty()) {
            String previousPage = history.pop();
            isBackNavigation = true;
            // При возврате контекст не передаем
            navigateTo(previousPage, null, null);
            isBackNavigation = false;
        } else {
            System.out.println("⚠️ История пуста, некуда возвращаться");
        }
    }

    /**
     * Проверка: есть ли куда возвращаться.
     */
    public static boolean canGoBack() {
        return !history.isEmpty();
    }

    /**
     * Возвращает размер истории (количество сохраненных страниц).
     */
    public static int getHistorySize() {
        return history.size();
    }

    /**
     * Возвращает текущую страницу.
     */
    public static String getCurrentPage() {
        return currentPage;
    }

    public static void setCurrentTitle(String title) {
        currentTitle = title;
        notifyListeners();
    }

    public static String getCurrentTitle() {
        return currentTitle;
    }

    // ============================================================
    // МОДАЛЬНЫЕ ДИАЛОГИ
    // ============================================================

    public static void openDialog(String fxmlPath, String title, Stage owner) {
        openDialog(fxmlPath, title, owner, null);
    }

    public static void openDialog(String fxmlPath, String title, Stage owner, Object context) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    NavigationManager.class.getResource(fxmlPath)
            );
            loader.setControllerFactory(controllerFactory);

            Parent root = loader.load();

            if (loader.getController() instanceof ContextAware aware) {
                aware.setContext(context);
            }

            Stage dialogStage = new Stage();
            dialogStage.setTitle(title);
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(owner);
            dialogStage.setScene(new Scene(root));
            dialogStage.showAndWait();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ============================================================
    // НОВЫЕ ОКНА
    // ============================================================

    public static void openWindow(String fxmlPath, String title) {
        openWindow(fxmlPath, title, null);
    }

    public static void openWindow(String fxmlPath, String title, Object context) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    NavigationManager.class.getResource(fxmlPath)
            );
            loader.setControllerFactory(controllerFactory);

            Parent root = loader.load();

            if (loader.getController() instanceof ContextAware aware) {
                aware.setContext(context);
            }

            Stage newStage = new Stage();
            newStage.setTitle(title);
            newStage.setScene(new Scene(root));
            newStage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void openRightSidebar(String path, Object context) {
        if (rightSidebar != null) {
            contentArea.getChildren().remove(rightSidebar);
    }
        navigateTo(path, null, context);

        if (rightSidebar instanceof VBox sidebarVBox) {
            sidebarVBox.setPrefWidth(RIGHT_SIDEBAR_WIDTH);
            sidebarVBox.setMaxWidth(RIGHT_SIDEBAR_WIDTH);
        }

        StackPane.setAlignment(rightSidebar, Pos.CENTER_RIGHT);

        contentArea.getChildren().add(rightSidebar);

        contentArea.setOnMouseClicked(event -> {
            Node clickedNode = (Node) event.getTarget();

            if (clickedNode == rightSidebar || isChildOf(clickedNode, rightSidebar)) {
                return;
            }

            closeRightSidebar();
        });

        TranslateTransition animate = new TranslateTransition(Duration.millis(200), rightSidebar);

        animate.setFromX(RIGHT_SIDEBAR_WIDTH);
        animate.setToX(0);
        animate.play();
    }

    public static void closeRightSidebar() {
        if (rightSidebar != null) {
            TranslateTransition animate = new TranslateTransition(Duration.millis(200), rightSidebar);

            animate.setToX(RIGHT_SIDEBAR_WIDTH);

            animate.setOnFinished(event -> {
                contentArea.getChildren().remove(rightSidebar);
                rightSidebar = null;
            });

            animate.play();
        }
    }

    private static boolean isChildOf(Node node, Node potentialParent) {
        while (node != null) {
            if (node == potentialParent) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }
}
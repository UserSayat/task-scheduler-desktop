package org.example.taskschedulerdesktop.shared.navigation;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.Duration;
import org.example.taskschedulerdesktop.shared.ui.Animations;
import org.example.taskschedulerdesktop.shared.ui.DialogFrame;
import org.example.taskschedulerdesktop.shared.ui.DateFormatter;
import org.example.taskschedulerdesktop.config.AppConfig;
import org.example.taskschedulerdesktop.shared.ui.RightSidebar;
import org.example.taskschedulerdesktop.shared.ui.Shutdownable;
import org.example.taskschedulerdesktop.shared.event.EventBus;
import org.example.taskschedulerdesktop.shared.event.PageTitleChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.example.taskschedulerdesktop.project.ProjectChangedEvent;
import org.example.taskschedulerdesktop.task.TaskChangedEvent;
import org.example.taskschedulerdesktop.user.UsersChangedEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Центральный менеджер навигации.
 * Управляет сменой страниц, открытием диалогов и передачей контекста.
 */
public class NavigationManager {

    private static final Logger log = LoggerFactory.getLogger(NavigationManager.class);

    private static StackPane globalStackPane;
    private static StackPane contentArea;

    private static Callback<Class<?>, Object> controllerFactory;

    private static Parent rightSidebar = null;
    private static Object rightSidebarController = null;
    /** Ширина правой панели (задача); по ней же — правая колонка страницы проекта. */
    public static final double RIGHT_SIDEBAR_WIDTH = 400.0;
    private static Region sidebarOverlay = null;

    /**
     * Запись истории. При переходе вглубь страница сохраняется целиком ({@code node} и {@code controller}):
     * «Назад» показывает её мгновенно и в том же состоянии, как в macOS. После переключения
     * раздела меню хранится только путь: разделы меню берутся из {@link #sectionCache}, остальное загружается заново.
     */
    private static class PageInfo {
        final String fxmlPath;
        final String title;
        final Parent node;
        final Object controller;
        final Object context;

        PageInfo(String fxmlPath, String title, Parent node, Object controller, Object context) {
            this.fxmlPath = fxmlPath;
            this.title = title;
            this.node = node;
            this.controller = controller;
            this.context = context;
        }
    }
    private static final Stack<PageInfo> history = new Stack<>();

    /** Разделы меню, которые сохраняются между переходами (см. {@link CachedPage}). */
    private static final Set<String> CACHED_SECTIONS = Set.of(Routes.DASHBOARD, Routes.TASKS, Routes.PROJECTS, Routes.TEAM);

    /** Сохранённый раздел: экран, его контроллер и стоит ли он на паузе. */
    private static final class CachedSection {
        final Parent node;
        final CachedPage controller;
        boolean paused;
        /** Значение {@link #dataVersion} в момент паузы. */
        long pausedAt;

        CachedSection(Parent node, CachedPage controller) {
            this.node = node;
            this.controller = controller;
        }
    }
    private static final Map<String, CachedSection> sectionCache = new HashMap<>();

    /** Растёт при каждом изменении задач, проектов и сотрудников: по нему раздел понимает, устарел ли он. */
    private static final AtomicLong dataVersion = new AtomicLong();
    private static final Consumer<TaskChangedEvent> taskChanged = event -> dataVersion.incrementAndGet();
    private static final Consumer<ProjectChangedEvent> projectChanged = event -> dataVersion.incrementAndGet();
    /** Растёт при изменении сотрудников: от них зависит заготовка панели задачи (списки исполнителей). */
    private static final AtomicLong usersVersion = new AtomicLong();
    private static final Consumer<UsersChangedEvent> usersChanged = event -> {
        dataVersion.incrementAndGet();
        usersVersion.incrementAndGet();
    };

    /** Растёт при выходе из аккаунта: запоздавшие заготовки старого окна не попадают в новое. */
    private static int generation;

    /**
     * Заранее загруженный экземпляр боковой панели: разметка загружена в фоне, стили применены.
     * {@code freshness} — версия данных, от которых он зависит, на момент подготовки.
     */
    private record Spare(FXMLLoader loader, java.util.function.LongSupplier source, long freshness) {
        boolean stale() {
            return source.getAsLong() != freshness;
        }
    }
    private static final Map<String, Spare> spares = new HashMap<>();
    /**
     * Формы (создание и изменение задачи, проекта, сотрудника, профиль) — отдельные страницы. В историю
     * они не попадают: «Назад» не возвращает к брошенной форме, а сама форма после сохранения или отмены
     * возвращает на страницу, с которой её открыли.
     */
    private static final Set<String> FORM_PAGES = Set.of(Routes.CREATE_TASK, Routes.CREATE_PROJECT,
            Routes.EDIT_PROJECT, Routes.PROFILE, Routes.EMPLOYEE_DIALOG);
    /** Пауза перед первым шагом подготовки (пока появляется первый экран) и между шагами. */
    private static final Duration WARM_UP_DELAY = Duration.millis(1500);
    private static final Duration WARM_UP_GAP = Duration.millis(400);
    /** Сколько ждать данных раздела перед расчётом раскладки, чтобы сразу создались строки таблиц. */
    private static final Duration DATA_SETTLE = Duration.millis(600);
    /** Когда готовить следующий запасной экземпляр: после анимации открытия или закрытия диалога. */
    private static final Duration SPARE_REFILL_DELAY = Duration.millis(700);
    private static String currentPage = null;
    private static String currentTitle = "";

    private static boolean isBackNavigation = false;

    //Уведомления
    private static StackPane activeToast = null;

    // Пул потоков специально для асинхронной загрузки интерфейсов (хватит 2-х потоков)
    private static final ExecutorService navigationExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        return thread;
    });

    // Храним ссылку на контроллер ТЕКУЩЕЙ страницы, чтобы вовремя вызывать shutdown
    private static Object currentController = null;
    private static Parent currentNode = null;
    /** С каким объектом открыта текущая страница (например, проект), или null. */
    private static Object currentContext = null;

    // ============================================================
    // ИНИЦИАЛИЗАЦИЯ
    // ============================================================

    public static void init(StackPane globalStackPane, StackPane contentArea, Callback<Class<?>, Object> controllerFactory) {

        NavigationManager.globalStackPane = globalStackPane;
        NavigationManager.contentArea = contentArea;
        NavigationManager.controllerFactory = controllerFactory;
        // Сдвигаемые при переходах экраны не должны заезжать на меню и шапку
        Animations.clipToBounds(contentArea);

        EventBus bus = EventBus.getInstance();
        bus.subscribe(TaskChangedEvent.class, taskChanged);
        bus.subscribe(ProjectChangedEvent.class, projectChanged);
        bus.subscribe(UsersChangedEvent.class, usersChanged);
    }

    public static void navigateTo(String fxmlPath) {
        navigateTo(fxmlPath, null);
    }

    public static void navigateTo(String fxmlPath, String title) {
        navigateTo(fxmlPath, title, null);
    }

    public static void navigateTo(String fxmlPath, String title, Object context) {
        navigate(fxmlPath, title, context, null);
    }

    /**
     * Открыть страницу из бокового меню: как переключение раздела (без перехода «вглубь»),
     * но с контекстом — например, проект из списка проектов в меню.
     */
    public static void navigateToSection(String fxmlPath, String title, Object context) {
        navigate(fxmlPath, title, context, Animations.Direction.SECTION);
    }

    private static void navigate(String fxmlPath, String title, Object context, Animations.Direction forced) {
        // Уже на этой странице (например, повторное нажатие на пункт меню) — не перезагружаем, без моргания
        if (!isBackNavigation && context == null && fxmlPath.equals(currentPage)) {
            return;
        }

        // Направление анимации определяем сразу: флаг «Назад» действует только во время этого вызова
        Animations.Direction direction = isBackNavigation ? Animations.Direction.BACK
                : forced != null ? forced
                : context != null ? Animations.Direction.FORWARD
                : Animations.Direction.SECTION;

        // Сохраняем текущую страницу в историю (если есть). При переходе вглубь — вместе с её состоянием
        // Сохранённый раздел меню в историю целиком не кладём: «Назад» достанет его из sectionCache
        boolean keepLeaving = false;
        if (Routes.DASHBOARD.equals(fxmlPath)) {
            clearHistory(); // «Обзор» — начальная страница: с неё назад не возвращаются
        } else if (!isBackNavigation && currentPage != null && !fxmlPath.equals(currentPage)
                && !FORM_PAGES.contains(currentPage)) {
            keepLeaving = direction == Animations.Direction.FORWARD && currentNode != null
                    && !sectionCache.containsKey(currentPage);
            history.push(keepLeaving
                    ? new PageInfo(currentPage, currentTitle, currentNode, currentController, currentContext)
                    : new PageInfo(currentPage, currentTitle, null, null, null));
        }
        boolean leavingKept = keepLeaving;

        CachedSection cached = context == null ? sectionCache.get(fxmlPath) : null;
        if (cached != null) {
            showCachedSection(fxmlPath, title, cached, direction, leavingKept);
            return;
        }

        log.debug("Start async loading page: {}", fxmlPath);

        Task<FXMLLoader> loadTask = new Task<>() {
            @Override
            protected FXMLLoader call() throws Exception {
                FXMLLoader loader = new FXMLLoader(
                        NavigationManager.class.getResource(fxmlPath)
                );
                loader.setControllerFactory(controllerFactory);
                DateFormatter.applyToPickers(loader.load());
                return loader;
            }
        };

        loadTask.setOnSucceeded(event -> {
            try {
                FXMLLoader loader = loadTask.getValue();
                Parent page = loader.getRoot();
                Object newController = loader.getController();

                leaveCurrentPage(leavingKept);
                if (context == null && CACHED_SECTIONS.contains(fxmlPath) && newController instanceof CachedPage section) {
                    sectionCache.put(fxmlPath, new CachedSection(page, section));
                }

                currentController = newController;
                currentNode = page;
                currentContext = context;

                if (newController instanceof ContextAware aware) {
                    aware.setContext(context);
                }
                if (newController instanceof RightSidebar sidebarController) {
                    sidebarController.setContext(context);
                }

                Animations.swapPage(contentArea, page, direction);
                currentPage = fxmlPath;

                if (title != null) {
                    currentTitle = title;
                }

                EventBus.getInstance().fire(new PageTitleChangedEvent(currentTitle));
                log.debug("Page displayed successfully: {}", fxmlPath);

            } catch (Exception e) {
                log.error("Error displaying page after load", e);
            }
        });

        loadTask.setOnFailed(event -> {
            Throwable e = loadTask.getException();
            log.error("Async load critical error");
            e.printStackTrace();
        });

        navigationExecutor.submit(loadTask);
    }

    /** Показать сохранённый раздел сразу, без загрузки и построения экрана. */
    private static void showCachedSection(String fxmlPath, String title, CachedSection cached,
                                          Animations.Direction direction, boolean leavingKept) {
        leaveCurrentPage(leavingKept);
        currentController = cached.controller;
        currentNode = cached.node;
        currentContext = null;
        Animations.swapPage(contentArea, cached.node, direction);
        currentPage = fxmlPath;
        if (title != null) {
            currentTitle = title;
        }
        if (cached.paused) {
            cached.paused = false;
            cached.controller.resume(dataVersion.get() != cached.pausedAt);
        }
        EventBus.getInstance().fire(new PageTitleChangedEvent(currentTitle));
        log.debug("Cached page displayed: {}", fxmlPath);
    }

    /**
     * Уход с текущей страницы: сохранённый раздел меню ставится на паузу, остальные страницы закрываются.
     * Страница, сохранённая в истории целиком ({@code kept}), продолжает жить.
     */
    private static void leaveCurrentPage(boolean kept) {
        if (kept || currentController == null) {
            return;
        }
        CachedSection section = currentPage != null ? sectionCache.get(currentPage) : null;
        if (section != null && section.controller == currentController) {
            if (!section.paused) {
                section.paused = true;
                section.pausedAt = dataVersion.get();
                section.controller.pause();
            }
        } else if (currentController instanceof Shutdownable shutdownable) {
            shutdownable.shutdown();
        }
    }

    // ============================================================
    // КНОПКА НАЗАД
    // ============================================================

    /**
     * Возврат на предыдущую страницу.
     */
    public static void goBack() {
        if (!history.isEmpty() && history.peek().node != null) {
            // Страница сохранена целиком: показываем её сразу, текущую закрываем
            PageInfo previousPage = history.pop();
            leaveCurrentPage(false);
            if (Routes.DASHBOARD.equals(previousPage.fxmlPath)) {
                clearHistory();
            }
            Animations.swapPage(contentArea, previousPage.node, Animations.Direction.BACK);
            currentNode = previousPage.node;
            currentController = previousPage.controller;
            currentContext = previousPage.context;
            currentPage = previousPage.fxmlPath;
            currentTitle = previousPage.title;
            EventBus.getInstance().fire(new PageTitleChangedEvent(currentTitle));
            return;
        }
        if (!history.isEmpty()) {
            PageInfo previousPage = history.pop();
            isBackNavigation = true;
            // При возврате контекст не передаем
            navigateTo(previousPage.fxmlPath, previousPage.title, null);
            isBackNavigation = false;
        } else {
            log.info("History is empty, nowhere to go");
        }
    }

    /** Забыть историю; страницы, сохранённые в ней целиком, закрываются. */
    private static void clearHistory() {
        for (PageInfo page : history) {
            shutdownController(page.controller);
        }
        history.clear();
    }

    /** Открыть форму (см. {@link #FORM_PAGES}) страницей; страница под ней сохраняется для возврата. */
    public static void openForm(String fxmlPath, String title, Object context) {
        closeRightSidebar();
        navigate(fxmlPath, title, context, Animations.Direction.FORWARD);
    }

    /** Форма сохранена или отменена — вернуться туда, откуда её открыли. */
    public static void closeForm() {
        if (canGoBack()) {
            goBack();
        } else {
            navigateTo(Routes.DASHBOARD, "Обзор");
        }
    }

    /** Корневой узел открытой страницы. */
    public static Node getCurrentPageNode() {
        return currentNode;
    }

    /** Открыта ли сейчас форма. */
    public static boolean isFormPage() {
        return FORM_PAGES.contains(currentPage);
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

    /** С каким объектом открыта текущая страница (например, {@code ProjectView}), или null. */
    public static Object getCurrentContext() {
        return currentContext;
    }

    /** Эта страница сейчас на экране (а не лежит в истории для «Назад»). */
    public static boolean isCurrent(Object controller) {
        return controller != null && controller == currentController;
    }

    public static void setCurrentTitle(String title) {
        currentTitle = title;
        EventBus.getInstance().fire(new PageTitleChangedEvent(title));
    }

    public static String getCurrentTitle() {
        return currentTitle;
    }

    // ============================================================
    // МОДАЛЬНЫЕ ДИАЛОГИ
    // ============================================================

    public static Stage openDialog(String fxmlPath, String title, Stage owner) {
        return openDialog(fxmlPath, title, owner, null);
    }

    public static Stage openDialog(String fxmlPath, String title, Stage owner, Object context) {
        try {
            FXMLLoader loader = new FXMLLoader(NavigationManager.class.getResource(fxmlPath));
            loader.setControllerFactory(controllerFactory);
            DateFormatter.applyToPickers(loader.load());
            Stage dialogStage = dialogStage(loader.getRoot(), owner);

            if (loader.getController() instanceof ContextAware aware) {
                aware.setContext(context);
            }
            dialogStage.setTitle(title);
            dialogStage.showAndWait();
            return dialogStage;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /** Окно диалога со своей рамкой: модальное, закрывается по Esc, появляется с анимацией. */
    private static Stage dialogStage(Parent root, Stage owner) {
        Stage dialogStage = new Stage();
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.initOwner(owner);

        Scene scene = DialogFrame.scene(dialogStage, root);
        // Esc закрывает окно (если клавишу не забрал, например, открытый выпадающий список)
        scene.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE && !event.isConsumed()) {
                closeDialog(dialogStage);
                event.consume();
            }
        });
        dialogStage.setScene(scene);
        dialogStage.setOnShown(event -> Animations.popIn(scene.getRoot()));
        return dialogStage;
    }

    public static void closeDialog(Stage stage) {
        if (stage != null) {
            log.debug("Close Stage: {}", stage.getTitle());
            Platform.runLater(() -> {
                if (stage.getScene() != null && stage.isShowing()) {
                    Animations.popOut(stage.getScene().getRoot(), stage::close);
                } else {
                    stage.close();
                }
            });
        } else {
            log.debug("Stage is null");
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
            DateFormatter.applyToPickers(root);

            if (loader.getController() instanceof ContextAware aware) {
                aware.setContext(context);
            }

            Stage newStage = new Stage();
            newStage.setTitle(title);
            newStage.setScene(DialogFrame.scene(newStage, root));
            newStage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void openRightSidebar(String path, Object context) {
        log.debug("openRightSidebar: path={}, context={}", path, context);
        stopSidebarSlide();

        if (rightSidebar != null) {
            globalStackPane.getChildren().remove(rightSidebar);
            rightSidebar = null;
        }
        if (sidebarOverlay != null) {
            globalStackPane.getChildren().remove(sidebarOverlay);
            sidebarOverlay = null;
        }

        try {
            // Заранее подготовленный экземпляр открывается без загрузки разметки; иначе — загружаем сейчас
            Spare spare = takeSpare(path);
            FXMLLoader loader;
            if (spare != null) {
                loader = spare.loader();
            } else {
                loader = new FXMLLoader(NavigationManager.class.getResource(path));
                loader.setControllerFactory(AppConfig.getInstance().getControllerFactory());
                DateFormatter.applyToPickers(loader.load());
            }
            Parent sidebar = loader.getRoot();

            Object controller = loader.getController();

            if (controller instanceof RightSidebar sidebarController) {
                log.debug("sidebarController found, calling setContext()");
                sidebarController.setContext(context);
            } else {
                log.warn("\n" +
                        "The controller does not implement RightSidebar: {}", controller);
            }

            rightSidebar = sidebar;
            rightSidebarController = controller;
            sizeSidebar(rightSidebar);

            sidebarOverlay = new Region();
            sidebarOverlay.setBackground(new Background(
                    new BackgroundFill(Color.rgb(0, 0, 0, 0.4), null, null)
            ));
            sidebarOverlay.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);

            sidebarOverlay.setOnMouseClicked(e -> closeRightSidebar());

            StackPane.setAlignment(sidebarOverlay, Pos.CENTER);

            // Панель и затемнение — под верхней полосой окна, не поверх неё
            javafx.geometry.Insets belowHeader = new javafx.geometry.Insets(headerHeight(), 0, 0, 0);
            StackPane.setMargin(rightSidebar, belowHeader);
            StackPane.setMargin(sidebarOverlay, belowHeader);

            globalStackPane.getChildren().addAll(sidebarOverlay, rightSidebar);
            // Раскладка панели — до начала выезда, иначе первый кадр анимации запаздывает
            globalStackPane.applyCss();
            globalStackPane.layout();
            var slide = Animations.slidePanel(rightSidebar, sidebarOverlay, RIGHT_SIDEBAR_WIDTH, true);
            sidebarSlide = slide;
            slide.setOnFinished(event -> {
                if (sidebarSlide == slide) {
                    sidebarSlide = null;
                }
            });
            slide.play();
            // Следующий экземпляр — когда панель уже выехала, чтобы не мешать анимации
            int gen = generation;
            later(SPARE_REFILL_DELAY, gen, () -> prepareSidebar(path, gen, () -> {}));
        } catch (IOException e) {
            log.error("Sidebar loading error", e);
        }
    }

    /** Анимация выезда панели, пока она идёт, иначе null. */
    private static javafx.animation.Animation sidebarSlide;

    /** Панель закрывают или меняют на другую посреди выезда: выезд останавливается, чтобы не спорить с закрытием. */
    private static void stopSidebarSlide() {
        if (sidebarSlide != null) {
            sidebarSlide.stop();
            sidebarSlide = null;
        }
    }

    /** Высота верхней полосы главного окна (логотип, синхронизация, профиль). */
    private static double headerHeight() {
        Node header = globalStackPane.lookup("#appHeader");
        return header instanceof Region region ? region.getHeight() : 0;
    }

    /** Открыта ли сейчас боковая панель (например, задача). */
    public static boolean isRightSidebarOpen() {
        return rightSidebar != null;
    }

    public static void closeRightSidebar() {
        if (rightSidebar == null) {
            return;
        }
        stopSidebarSlide();

        if (rightSidebarController instanceof Shutdownable shutdownable) {
            shutdownable.shutdown();
        }

        rightSidebarController = null;

        Parent closingSidebar = rightSidebar;
        Region closingOverlay = sidebarOverlay;
        rightSidebar = null;
        sidebarOverlay = null;

        // Пока панель уезжает, щелчки проходят сквозь неё и затемнение к странице
        closingSidebar.setMouseTransparent(true);
        closingOverlay.setMouseTransparent(true);
        var animateSidebar = Animations.slidePanel(closingSidebar, closingOverlay, RIGHT_SIDEBAR_WIDTH, false);
        animateSidebar.setOnFinished(event -> globalStackPane.getChildren().removeAll(closingSidebar, closingOverlay));
        animateSidebar.play();
    }

    // ============================================================
    // ЗАРАНЕЕ ПОДГОТОВЛЕННЫЕ ЭКРАНЫ
    // ============================================================

    /** Шаг подготовки; по завершении вызывает {@code next}. */
    private interface WarmUpStep {
        void run(Runnable next);
    }

    /**
     * Заранее построить разделы «Задачи», «Проекты», «Команда» и панель задачи —
     * по одному, с паузами, пока пользователь смотрит на первый экран. Тогда их первое открытие
     * не подтормаживает: разметка загружается в фоне, а стили и раскладка считаются заранее.
     */
    public static void warmUp() {
        if ("false".equalsIgnoreCase(System.getProperty("taskhub.warmUp"))) {
            return; // для проверки: -Dtaskhub.warmUp=false
        }
        int gen = generation;
        java.util.Deque<WarmUpStep> steps = new java.util.ArrayDeque<>(List.of(
                next -> warmSection(Routes.TASKS, gen, next),
                next -> warmSection(Routes.PROJECTS, gen, next),
                next -> warmSection(Routes.TEAM, gen, next),
                next -> prepareSidebar(Routes.TASK_DETAILS, gen, next)));
        runSteps(steps, gen, WARM_UP_DELAY);
    }

    private static void runSteps(java.util.Deque<WarmUpStep> steps, int gen, Duration delay) {
        WarmUpStep step = steps.poll();
        if (step == null) {
            return;
        }
        later(delay, gen, () -> step.run(() -> runSteps(steps, gen, WARM_UP_GAP)));
    }

    /** Сколько пользователь должен ничего не делать, чтобы заготовка не помешала ему подтормаживанием. */
    private static final long IDLE_NANOS = 700_000_000L;
    private static volatile long lastInputNanos;
    private static Scene watchedScene;
    /** Пути, для которых запасной экземпляр сейчас готовится: второй раз не начинаем. */
    private static final Set<String> preparing = new java.util.HashSet<>();

    /**
     * Выполнить, когда пользователь ничего не делает (не двигает мышь, не нажимает клавиши) хотя бы 0,7 с:
     * стили и раскладка заготовки занимают до ~150 мс, и в простое это не заметно.
     */
    private static void whenIdle(int gen, Runnable action) {
        watchInput();
        long idleFor = System.nanoTime() - lastInputNanos;
        if (idleFor >= IDLE_NANOS) {
            if (gen == generation) {
                action.run();
            }
        } else {
            later(Duration.millis(Math.max(100, (IDLE_NANOS - idleFor) / 1_000_000.0)), gen, () -> whenIdle(gen, action));
        }
    }

    /** Следить за вводом в главном окне (для {@link #whenIdle}). */
    private static void watchInput() {
        Scene scene = globalStackPane != null ? globalStackPane.getScene() : null;
        if (scene != null && scene != watchedScene) {
            scene.addEventFilter(javafx.scene.input.InputEvent.ANY, event -> lastInputNanos = System.nanoTime());
            watchedScene = scene;
        }
    }

    /** Выполнить в потоке интерфейса через {@code delay}, если окно за это время не сменилось. */
    private static void later(Duration delay, int gen, Runnable action) {
        PauseTransition pause = new PauseTransition(delay);
        pause.setOnFinished(event -> {
            if (gen == generation) {
                action.run();
            }
        });
        pause.play();
    }

    /** Загрузить разметку в фоновом потоке; {@code onLoaded} вызывается в потоке интерфейса. */
    private static void loadInBackground(String fxmlPath, Consumer<FXMLLoader> onLoaded) {
        Task<FXMLLoader> task = new Task<>() {
            @Override
            protected FXMLLoader call() throws Exception {
                FXMLLoader loader = new FXMLLoader(NavigationManager.class.getResource(fxmlPath));
                loader.setControllerFactory(controllerFactory);
                DateFormatter.applyToPickers(loader.load());
                return loader;
            }
        };
        task.setOnSucceeded(event -> onLoaded.accept(task.getValue()));
        task.setOnFailed(event -> log.warn("Background load failed: {}", fxmlPath, task.getException()));
        navigationExecutor.submit(task);
    }

    /**
     * Применить стили и посчитать раскладку заранее: узел на мгновение добавляется под текущий экран
     * и сразу убирается — кадр с ним не рисуется, а при показе почти вся работа уже сделана.
     */
    private static void prerender(Pane parent, Node node) {
        parent.getChildren().add(0, node);
        node.applyCss();
        parent.layout();
        parent.getChildren().remove(node);
    }

    private static void shutdownController(Object controller) {
        if (controller instanceof Shutdownable shutdownable) {
            shutdownable.shutdown();
        }
    }

    /** Построить раздел меню заранее и сохранить его на паузе (см. {@link CachedPage}). */
    private static void warmSection(String fxmlPath, int gen, Runnable next) {
        if (sectionCache.containsKey(fxmlPath) || fxmlPath.equals(currentPage)) {
            next.run();
            return;
        }
        loadInBackground(fxmlPath, loader -> {
            Object controller = loader.getController();
            // Окно сменилось или раздел уже открыли сами — заготовка не нужна
            if (gen != generation || !(controller instanceof CachedPage page)
                    || sectionCache.containsKey(fxmlPath) || fxmlPath.equals(currentPage)) {
                shutdownController(controller);
                if (gen == generation) {
                    next.run();
                }
                return;
            }
            CachedSection section = new CachedSection(loader.getRoot(), page);
            section.paused = true;
            section.pausedAt = dataVersion.get();
            page.pause();
            sectionCache.put(fxmlPath, section);
            // Раскладку считаем, когда данные раздела уже пришли (тогда сразу создаются и строки таблиц)
            // и пользователь ничего не делает
            later(DATA_SETTLE, gen, () -> whenIdle(gen, () -> {
                if (sectionCache.get(fxmlPath) == section && section.paused) {
                    prerender(contentArea, section.node);
                }
                next.run();
            }));
        });
    }

    /** Подготовить запасной экземпляр боковой панели. */
    private static void prepareSidebar(String path, int gen, Runnable next) {
        if (spares.containsKey(path) || !preparing.add(path)) {
            next.run();
            return;
        }
        loadInBackground(path, loader -> whenIdle(gen, () -> {
            preparing.remove(path);
            Parent root = loader.getRoot();
            sizeSidebar(root);
            prerender(globalStackPane, root);
            spares.put(path, new Spare(loader, usersVersion::get, usersVersion.get()));
            next.run();
        }));
    }

    /**
     * Забрать запасной экземпляр, если он есть и данные, от которых он зависит, не менялись.
     * Устаревший закрывается: вместо него загрузится свежий.
     */
    private static Spare takeSpare(String path) {
        Spare spare = spares.remove(path);
        if (spare != null && spare.stale()) {
            shutdownController(spare.loader().getController());
            return null;
        }
        return spare;
    }

    private static void sizeSidebar(Parent sidebar) {
        if (sidebar instanceof Region region) {
            region.setPrefWidth(RIGHT_SIDEBAR_WIDTH);
            region.setMaxWidth(RIGHT_SIDEBAR_WIDTH);
        }
        StackPane.setAlignment(sidebar, Pos.CENTER_RIGHT);
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

    // ============================================================
    // УВЕДОМЛЕНИЯ
    // ============================================================

    /**
     * Показывает всплывающее уведомление внизу экрана.
     */
    public static void showToast(String message, String type) {
        if (globalStackPane == null) {
            log.warn("globalStackPane not initialized, Toast not shown");
            return;
        }

        // Тост — слой внутри окна, а не отдельное всплывающее окно: у всплывающего окна на Windows
        // вокруг скруглённой плашки просвечивал прямоугольник (особенно поверх затемнения у боковой панели)
        if (activeToast != null) {
            globalStackPane.getChildren().remove(activeToast);
            activeToast = null;
        }

        String color = switch (type) {
            case "success" -> "#2ecc71";
            case "error" -> "#e74c3c";
            case "info" -> "#3498db";
            default -> "#333333";
        };

        Label label = new Label(message);
        label.setWrapText(true);
        label.setStyle("-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold;");

        StackPane toast = new StackPane(label);
        toast.setStyle(
                "-fx-background-color: " + color + "; " +
                        "-fx-padding: 12 24 12 24; " +
                        "-fx-background-radius: 8;"
        );
        label.setMaxWidth(352); // длинный текст переносится, а плашка не шире 400
        toast.setMaxWidth(javafx.scene.layout.Region.USE_PREF_SIZE); // по размеру текста, не во всю ширину
        toast.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        toast.setMouseTransparent(true); // не мешает нажимать то, что под ним
        StackPane.setAlignment(toast, javafx.geometry.Pos.BOTTOM_CENTER);
        StackPane.setMargin(toast, new javafx.geometry.Insets(0, 0, 40, 0));

        StackPane shownToast = toast;
        globalStackPane.getChildren().add(toast); // поверх всего, в том числе боковой панели
        activeToast = toast;

        toast.setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), toast);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();

        PauseTransition stay = new PauseTransition(Duration.seconds(2.5));
        stay.setOnFinished(e -> {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(300), shownToast);
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(ev -> {
                globalStackPane.getChildren().remove(shownToast);
                if (activeToast == shownToast) {
                    activeToast = null;
                }
            });
            fadeOut.play();
        });
        stay.play();

        log.debug("Toast is displayed: {}", message);
    }

    public static void dispose() {
        if (globalStackPane != null && rightSidebar != null) {
            globalStackPane.getChildren().remove(rightSidebar);
            rightSidebar = null;
        }
        rightSidebarController = null;
        // Страницы, сохранённые в истории целиком, и сохранённые разделы ещё подписаны на события — закрываем их
        for (PageInfo page : history) {
            if (page.controller instanceof Shutdownable shutdownable) {
                shutdownable.shutdown();
            }
        }
        boolean currentIsCached = false;
        for (CachedSection section : sectionCache.values()) {
            section.controller.shutdown();
            currentIsCached |= section.controller == currentController;
        }
        if (!currentIsCached && currentController instanceof Shutdownable shutdownable) {
            shutdownable.shutdown();
        }
        sectionCache.clear();
        // Заготовки старого окна больше не нужны, а запоздавшие — не попадут в новое
        generation++;
        spares.values().forEach(spare -> shutdownController(spare.loader().getController()));
        spares.clear();
        preparing.clear();
        EventBus bus = EventBus.getInstance();
        bus.unsubscribe(TaskChangedEvent.class, taskChanged);
        bus.unsubscribe(ProjectChangedEvent.class, projectChanged);
        bus.unsubscribe(UsersChangedEvent.class, usersChanged);
        history.clear();
        currentPage = null;
        currentController = null;
        currentNode = null;
        log.debug("NavigationManager disposed");
    }
}
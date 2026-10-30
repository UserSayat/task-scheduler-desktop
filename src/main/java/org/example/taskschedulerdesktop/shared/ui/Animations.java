package org.example.taskschedulerdesktop.shared.ui;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.List;

/**
 * Анимации интерфейса в духе macOS: движение по пружинной кривой (быстрый старт, мягкая остановка),
 * переход вглубь — «проталкивание» экрана с параллаксом, разделы меню — перекрёстная смена.
 * Анимации не блокируют нажатия и не накапливаются при быстрых переходах.
 * <p>
 * Выключаются флагом {@code -Dtaskhub.animations=false}.
 */
public final class Animations {

    /** Как появляется новый экран. */
    public enum Direction {
        /** Вглубь (проект → его страница): новый экран въезжает справа поверх старого. */
        FORWARD,
        /** «Назад»: текущий экран уезжает вправо, открывая предыдущий. */
        BACK,
        /** Разделы меню: перекрёстная смена без сдвига. */
        SECTION
    }

    private static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("taskhub.animations"));

    /** Пружина без отскока — как навигация в macOS (Finder, Системные настройки). */
    public static final Interpolator SPRING = new Spring(1.0, 9.0);
    /** Пружина с едва заметным отскоком — для диалогов и всплывающих элементов. */
    public static final Interpolator SPRING_BOUNCE = new Spring(0.72, 11.0);

    private static final Duration PUSH = Duration.millis(420);
    private static final Duration CROSSFADE = Duration.millis(200);
    /** Насколько уходит экран «под» новый (доля ширины) — эффект глубины. */
    private static final double PARALLAX = 0.28;
    /** Насколько темнеет экран, который уходит под новый. */
    private static final double DIM = 0.55;

    /** Фон области контента — подкладывается под экраны на время движения, чтобы они не просвечивали. */
    private static final Background PAGE_BACKGROUND =
            new Background(new BackgroundFill(Color.web("#f6f6f4"), null, null));

    private static Animation pageTransition;

    private Animations() {}

    public static boolean enabled() {
        return ENABLED;
    }

    /**
     * Обрезать содержимое по границам контейнера: сдвигаемые экраны не заезжают на меню и шапку.
     */
    public static void clipToBounds(Region region) {
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(region.widthProperty());
        clip.heightProperty().bind(region.heightProperty());
        region.setClip(clip);
    }

    // ===== Смена экранов =====

    /**
     * Сменить экран в контейнере. Если предыдущая смена ещё идёт, она мгновенно завершается.
     */
    public static void swapPage(Pane container, Node page, Direction direction) {
        if (pageTransition != null) {
            pageTransition.jumpTo("end");
            pageTransition.stop();
            pageTransition = null;
        }
        List<Node> old = List.copyOf(container.getChildren());
        double width = container.getWidth();
        if (!ENABLED || old.isEmpty() || width <= 0) {
            container.getChildren().setAll(page);
            reset(page);
            if (ENABLED && old.isEmpty()) {
                pageTransition = crossfadeIn(page);
                pageTransition.play();
            }
            return;
        }

        old.forEach(node -> node.setMouseTransparent(true));
        Animation animation = switch (direction) {
            case FORWARD -> push(container, old, page, width);
            case BACK -> pop(container, old, page, width);
            case SECTION -> crossfade(container, old, page);
        };
        animation.setOnFinished(event -> {
            container.getChildren().removeAll(old);
            old.forEach(Animations::reset);
            reset(page);
            pageTransition = null;
        });
        pageTransition = animation;
        animation.play();
    }

    /** Вглубь: новый экран въезжает справа поверх, старый уходит влево на треть и притемняется. */
    private static Animation push(Pane container, List<Node> old, Node page, double width) {
        container.getChildren().add(page); // поверх старого
        opaque(page);
        old.forEach(Animations::opaque);
        page.setEffect(edgeShadow());
        page.setTranslateX(width);

        ParallelTransition all = new ParallelTransition(slide(page, 0));
        for (Node node : old) {
            all.getChildren().addAll(slide(node, -width * PARALLAX), dim(node, DIM));
        }
        return all;
    }

    /** «Назад»: текущий экран уезжает вправо, предыдущий выходит из-под него слева и светлеет. */
    private static Animation pop(Pane container, List<Node> old, Node page, double width) {
        container.getChildren().add(0, page); // под старым
        opaque(page);
        old.forEach(Animations::opaque);
        old.forEach(node -> node.setEffect(edgeShadow()));
        page.setTranslateX(-width * PARALLAX);
        page.setOpacity(DIM);

        ParallelTransition all = new ParallelTransition(slide(page, 0), dim(page, 1));
        for (Node node : old) {
            all.getChildren().add(slide(node, width));
        }
        return all;
    }

    /** Разделы меню: старый экран гаснет, новый проявляется с едва заметным приближением. */
    private static Animation crossfade(Pane container, List<Node> old, Node page) {
        container.getChildren().add(page);
        ParallelTransition all = new ParallelTransition(crossfadeIn(page));
        for (Node node : old) {
            FadeTransition out = new FadeTransition(Duration.millis(140), node);
            out.setToValue(0);
            all.getChildren().add(out);
        }
        return all;
    }

    private static Animation crossfadeIn(Node page) {
        page.setOpacity(0);
        page.setScaleX(0.992);
        page.setScaleY(0.992);
        FadeTransition fade = new FadeTransition(CROSSFADE, page);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT);
        ScaleTransition scale = new ScaleTransition(Duration.millis(260), page);
        scale.setToX(1);
        scale.setToY(1);
        scale.setInterpolator(SPRING);
        return new ParallelTransition(fade, scale);
    }

    private static Animation slide(Node node, double toX) {
        TranslateTransition move = new TranslateTransition(PUSH, node);
        move.setToX(toX);
        move.setInterpolator(SPRING);
        return move;
    }

    private static Animation dim(Node node, double toOpacity) {
        FadeTransition fade = new FadeTransition(PUSH, node);
        fade.setToValue(toOpacity);
        fade.setInterpolator(SPRING);
        return fade;
    }

    /** Мягкая тень по краю въезжающего экрана — как у листа, лежащего сверху. */
    private static DropShadow edgeShadow() {
        DropShadow shadow = new DropShadow(24, Color.rgb(0, 0, 0, 0.16));
        shadow.setOffsetX(-6);
        return shadow;
    }

    /** На время движения у экрана непрозрачный фон, иначе экраны просвечивают друг через друга. */
    private static void opaque(Node node) {
        if (node instanceof Region region && region.getBackground() == null) {
            region.setBackground(PAGE_BACKGROUND);
            region.getProperties().put("taskhub.temporaryBackground", Boolean.TRUE);
        }
    }

    private static void reset(Node node) {
        node.setOpacity(1);
        node.setTranslateX(0);
        node.setTranslateY(0);
        node.setScaleX(1);
        node.setScaleY(1);
        node.setEffect(null);
        node.setMouseTransparent(false);
        if (node instanceof Region region && region.getProperties().remove("taskhub.temporaryBackground") != null) {
            region.setBackground(null);
        }
    }

    // ===== Диалоги и панели =====

    /** Открытие диалога: из чуть уменьшенного и прозрачного — с лёгким пружинным «доводом». */
    public static void popIn(Node root) {
        if (!ENABLED) {
            return;
        }
        root.setOpacity(0);
        root.setScaleX(0.94);
        root.setScaleY(0.94);
        FadeTransition fade = new FadeTransition(Duration.millis(160), root);
        fade.setToValue(1);
        ScaleTransition scale = new ScaleTransition(Duration.millis(380), root);
        scale.setToX(1);
        scale.setToY(1);
        scale.setInterpolator(SPRING_BOUNCE);
        new ParallelTransition(fade, scale).play();
    }

    /** Закрытие диалога: быстро гаснет и чуть уменьшается, затем {@code onFinished}. */
    public static void popOut(Node root, Runnable onFinished) {
        if (!ENABLED) {
            onFinished.run();
            return;
        }
        FadeTransition fade = new FadeTransition(Duration.millis(130), root);
        fade.setToValue(0);
        ScaleTransition scale = new ScaleTransition(Duration.millis(130), root);
        scale.setToX(0.97);
        scale.setToY(0.97);
        ParallelTransition out = new ParallelTransition(fade, scale);
        out.setOnFinished(event -> onFinished.run());
        out.play();
    }

    /** Боковая панель въезжает справа (или уезжает обратно), фон под ней плавно темнеет. */
    public static Animation slidePanel(Node panel, Node overlay, double width, boolean open) {
        if (open) {
            panel.setTranslateX(width);
            overlay.setOpacity(0);
        }
        TranslateTransition move = new TranslateTransition(Duration.millis(open ? 420 : 260), panel);
        move.setToX(open ? 0 : width);
        move.setInterpolator(open ? SPRING : Interpolator.EASE_IN);
        FadeTransition fade = new FadeTransition(Duration.millis(open ? 260 : 220), overlay);
        fade.setToValue(open ? 1 : 0);
        ParallelTransition both = new ParallelTransition(move, fade);
        if (!ENABLED) {
            both.jumpTo("end");
        }
        return both;
    }

    /** Плавное появление (смена «вход ↔ главное окно»). */
    public static void fadeIn(Node node) {
        if (!ENABLED) {
            return;
        }
        node.setOpacity(0);
        FadeTransition fade = new FadeTransition(Duration.millis(280), node);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT);
        fade.play();
    }

    // ===== Мелочи =====

    /**
     * Карточки появляются по очереди (шаг 30 мс, не больше 10 с задержкой — остальные сразу).
     */
    public static void staggerIn(List<? extends Node> nodes) {
        if (!ENABLED) {
            return;
        }
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            node.setOpacity(0);
            node.setTranslateY(6);
            FadeTransition fade = new FadeTransition(Duration.millis(240), node);
            fade.setToValue(1);
            TranslateTransition move = new TranslateTransition(Duration.millis(380), node);
            move.setToY(0);
            move.setInterpolator(SPRING);
            ParallelTransition appear = new ParallelTransition(fade, move);
            appear.setDelay(Duration.millis(30L * Math.min(i, 10)));
            appear.play();
        }
    }

    /** Лёгкое «подпрыгивание» — счётчик на колокольчике при новом уведомлении. */
    public static void pulse(Node node) {
        if (!ENABLED) {
            return;
        }
        ScaleTransition up = new ScaleTransition(Duration.millis(110), node);
        up.setToX(1.22);
        up.setToY(1.22);
        up.setInterpolator(Interpolator.EASE_OUT);
        ScaleTransition down = new ScaleTransition(Duration.millis(420), node);
        down.setToX(1);
        down.setToY(1);
        down.setInterpolator(SPRING_BOUNCE);
        new SequentialTransition(up, down).play();
    }

    /** Карточка чуть приподнимается при наведении (тень становится глубже). */
    public static void hoverLift(Node node) {
        if (!ENABLED) {
            return;
        }
        DropShadow shadow = new DropShadow(0, Color.rgb(0, 0, 0, 0.10));
        shadow.setOffsetY(0);
        node.setEffect(shadow);
        Timeline raise = new Timeline(new KeyFrame(Duration.millis(220),
                new KeyValue(shadow.radiusProperty(), 18, Interpolator.EASE_OUT),
                new KeyValue(shadow.offsetYProperty(), 4, Interpolator.EASE_OUT),
                new KeyValue(node.translateYProperty(), -2, Interpolator.EASE_OUT)));
        Timeline lower = new Timeline(new KeyFrame(Duration.millis(260),
                new KeyValue(shadow.radiusProperty(), 0, Interpolator.EASE_OUT),
                new KeyValue(shadow.offsetYProperty(), 0, Interpolator.EASE_OUT),
                new KeyValue(node.translateYProperty(), 0, Interpolator.EASE_OUT)));
        node.setOnMouseEntered(event -> {
            lower.stop();
            raise.playFromStart();
        });
        node.setOnMouseExited(event -> {
            raise.stop();
            lower.playFromStart();
        });
    }

    /** «Дыхание» — пока идёт синхронизация. Возвращает анимацию, чтобы её остановить. */
    public static Animation breathe(Node node) {
        FadeTransition fade = new FadeTransition(Duration.millis(800), node);
        fade.setFromValue(1);
        fade.setToValue(0.45);
        fade.setAutoReverse(true);
        fade.setInterpolator(Interpolator.EASE_BOTH);
        fade.setCycleCount(Animation.INDEFINITE);
        if (ENABLED) {
            fade.play();
        }
        return fade;
    }

    /**
     * Пружинная кривая: решение уравнения затухающего колебания, нормированное так,
     * чтобы в конце анимации значение было ровно 1.
     *
     * @param damping   1 — без отскока, меньше 1 — с отскоком
     * @param frequency жёсткость пружины: чем больше, тем быстрее движение «доезжает»
     */
    private static final class Spring extends Interpolator {

        private final double damping;
        private final double frequency;
        private final double end;

        Spring(double damping, double frequency) {
            this.damping = damping;
            this.frequency = frequency;
            this.end = raw(1);
        }

        @Override
        protected double curve(double t) {
            return t >= 1 ? 1 : raw(t) / end;
        }

        private double raw(double t) {
            double w = frequency;
            if (damping >= 1) {
                return 1 - (1 + w * t) * Math.exp(-w * t);
            }
            double wd = w * Math.sqrt(1 - damping * damping);
            return 1 - Math.exp(-damping * w * t)
                    * (Math.cos(wd * t) + damping * w / wd * Math.sin(wd * t));
        }
    }
}

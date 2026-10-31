package org.example.taskschedulerdesktop.shared.ui;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;


/**
 * Небольшие анимации интерфейса: появление диалогов, приподнимание карточек при наведении,
 * «подпрыгивание» счётчика уведомлений, «дыхание» значка синхронизации.
 * Боковая панель выезжает справа. Переходы между экранами — без анимации: сразу.
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
     * Сменить экран в контейнере — сразу, без анимации перехода.
     * Направление оставлено в API: по нему навигация решает, хранить ли страницу для «Назад».
     */
    public static void swapPage(Pane container, Node page, Direction direction) {
        container.getChildren().setAll(page);
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


    // ===== Боковая панель =====

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

    // ===== Мелочи =====

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

    /**
     * При наведении по краю карточки проступает мягкая тень — без подъёма и сдвига.
     * Эффект висит на карточке только при наведении: постоянная тень у каждой карточки
     * заставляла перерисовывать их через промежуточный буфер.
     */
    public static void hoverShadow(Node node) {
        DropShadow shadow = new DropShadow(0, Color.rgb(0, 0, 0, 0.08));
        node.setOnMouseEntered(event -> {
            if (!ENABLED) {
                shadow.setRadius(10);
                node.setEffect(shadow);
                return;
            }
            node.setEffect(shadow);
            new Timeline(new KeyFrame(Duration.millis(160),
                    new KeyValue(shadow.radiusProperty(), 10, Interpolator.EASE_OUT))).play();
        });
        node.setOnMouseExited(event -> {
            shadow.setRadius(0);
            node.setEffect(null);
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

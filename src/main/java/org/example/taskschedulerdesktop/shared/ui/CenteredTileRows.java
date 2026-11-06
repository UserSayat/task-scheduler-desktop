package org.example.taskschedulerdesktop.shared.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

import java.util.function.DoubleUnaryOperator;

/**
 * Сетка карточек: ряд целиком стоит по центру страницы, а карточки идут от его левого края. У полных
 * рядов отступы слева и справа одинаковые, а неполный последний ряд начинается под первой карточкой.
 * <p>
 * Сетка сужается ровно до ширины помещающихся колонок, а страница ставит её по центру.
 */
public final class CenteredTileRows {

    private CenteredTileRows() {}

    /**
     * @param tiles      сетка карточек
     * @param page       страница вокруг сетки (её ширина за вычетом полей — место под ряд)
     * @param tileWidth  ширина карточки для такого места; для карточек постоянной ширины — константа
     * @param maxColumns больше скольких карточек в ряд не ставить
     */
    public static void install(TilePane tiles, VBox page, DoubleUnaryOperator tileWidth, int maxColumns) {
        page.setAlignment(Pos.TOP_CENTER);
        tiles.setAlignment(Pos.TOP_LEFT);
        tiles.setTileAlignment(Pos.TOP_LEFT);
        Runnable update = () -> fit(tiles, page, tileWidth, maxColumns);
        page.widthProperty().addListener((obs, old, width) -> update.run());
        // Карточек меньше, чем помещается в ряд, — ряд сужается до них и тоже стоит по центру
        tiles.getChildren().addListener((javafx.collections.ListChangeListener<javafx.scene.Node>) change -> update.run());
    }

    private static void fit(TilePane tiles, VBox page, DoubleUnaryOperator tileWidth, int maxColumns) {
        Insets insets = page.getInsets();
        double available = page.getWidth() - insets.getLeft() - insets.getRight();
        if (available <= 0) {
            return;
        }
        double gap = tiles.getHgap();
        double tile = tileWidth.applyAsDouble(available);
        int columns = Math.min(maxColumns, columns(available, tile, gap));
        columns = Math.max(1, Math.min(columns, tiles.getChildren().size()));
        double row = columns * tile + (columns - 1) * gap;
        tiles.setPrefTileWidth(tile);
        tiles.setPrefWidth(row);
        tiles.setMaxWidth(row);
    }

    /**
     * Ширина карточки, при которой ряд заполняется до полей страницы: столько колонок, сколько влезает
     * карточек не уже {@code min} (но не больше {@code maxColumns}), карточки делят ряд поровну, но не шире
     * {@code max} — тогда ряд стоит по центру с равными отступами.
     */
    public static double fillWidth(double available, double gap, double min, double max, int maxColumns) {
        if (available <= 0) {
            return min; // ширина ещё не посчитана
        }
        int columns = (int) Math.floor((available + gap) / (min + gap));
        columns = Math.max(1, Math.min(maxColumns, columns));
        double width = (available - gap * (columns - 1)) / columns;
        return Math.floor(Math.max(Math.min(width, max), Math.min(min, available)));
    }

    /** Сколько карточек ширины {@code tile} помещается в ряд (хотя бы одна). */
    public static int columns(double available, double tile, double gap) {
        return Math.max(1, (int) Math.floor((available + gap) / (tile + gap)));
    }
}

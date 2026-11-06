package org.example.taskschedulerdesktop.shared.ui;

import javafx.beans.InvalidationListener;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Таблица или список показывают только целые строки: нижняя видимая строка не обрезается.
 * Строки одной высоты ({@code fixedCellSize}), остаток места, которого не хватает на целую строку,
 * остаётся снизу пустым.
 */
public final class WholeRows {

    private WholeRows() {}

    /**
     * Таблица растягивается в своём контейнере (колонке или ряду), но берёт из отведённого места только
     * заголовок и целые строки: рамка заканчивается на последней строке, а остаток (меньше строки)
     * остаётся фоном страницы внизу.
     */
    public static void fit(TableView<?> table) {
        InvalidationListener update = observable -> fitTable(table);
        if (table.getParent() instanceof Region region) {
            region.heightProperty().addListener(update); // таблица уже в контейнере (загружена вместе с ним)
        }
        table.parentProperty().addListener((obs, old, parent) -> {
            if (parent instanceof Region region) {
                region.heightProperty().addListener(update);
                update.invalidated(null);
            }
        });
        table.skinProperty().addListener((obs, old, skin) -> {
            // Высота заголовка известна, когда у таблицы появилась оболочка (skin) и применились стили
            if (table.lookup(".column-header-background") instanceof Region header) {
                header.heightProperty().addListener(update);
            }
            update.invalidated(null);
        });
    }

    private static void fitTable(TableView<?> table) {
        double row = table.getFixedCellSize();
        Parent parent = table.getParent();
        if (row <= 0 || !(parent instanceof Region container) || container.getHeight() <= 0) {
            return;
        }
        Insets room = container.getInsets();
        double available = container.getHeight() - room.getTop() - room.getBottom();
        if (container instanceof VBox column) {
            // В колонке таблице достаётся место за вычетом соседей (поиск сверху, кнопки снизу) и промежутков
            for (Node sibling : column.getChildren()) {
                if (sibling != table && sibling.isManaged()) {
                    available -= sibling.getLayoutBounds().getHeight() + column.getSpacing();
                }
            }
        }
        double frame = table.getInsets().getTop() + table.getInsets().getBottom() + headerHeight(table);
        int rows = (int) Math.max(1, Math.floor((available - frame) / row));
        double height = frame + rows * row;
        if (Math.abs(table.getMaxHeight() - height) > 0.5) {
            table.setMinHeight(0);
            table.setMaxHeight(height);
        }
    }

    private static double headerHeight(TableView<?> table) {
        Node header = table.lookup(".column-header-background");
        return header instanceof Region region ? region.getHeight() : 0;
    }

    /**
     * Список без рамки (карточки на «Обзоре»): остаток высоты, которого не хватает на целую карточку,
     * уходит в нижнее поле списка.
     */
    public static void fit(ListView<?> list) {
        list.heightProperty().addListener(observable -> {
            double row = list.getFixedCellSize();
            if (row <= 0 || list.getHeight() <= 0) {
                return;
            }
            Insets border = list.getBorder() != null ? list.getBorder().getInsets() : Insets.EMPTY;
            double rows = list.getHeight() - border.getTop() - border.getBottom();
            double rest = Math.max(0, rows - Math.floor(rows / row) * row);
            if (Math.abs(list.getPadding().getBottom() - rest) > 0.01 || list.getPadding().getTop() != 0) {
                list.setPadding(new Insets(0, 0, rest, 0));
            }
        });
    }
}

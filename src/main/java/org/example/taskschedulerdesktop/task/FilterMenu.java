package org.example.taskschedulerdesktop.task;

import javafx.scene.control.CheckBox;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.layout.Region;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Выпадающий фильтр с галочками: «Проект», «Исполнитель», «Срок». Меню не закрывается после
 * каждой галочки — можно отметить несколько значений подряд. Ничего не отмечено — фильтр выключен.
 *
 * @param <T> значение фильтра
 */
final class FilterMenu<T> {

    private final MenuButton button = new MenuButton();
    private final String title;
    private final Consumer<Set<T>> onChange;
    private final Set<T> selected = new LinkedHashSet<>();
    private Map<T, String> options = Map.of();
    private final MenuItem resetItem = new MenuItem("Снять все");

    FilterMenu(String title, Consumer<Set<T>> onChange) {
        this.title = title;
        this.onChange = onChange;
        button.getStyleClass().add("filter-menu");
        button.setFocusTraversable(false);
        button.setMinWidth(Region.USE_PREF_SIZE); // не обрезать подпись, сжимается поле поиска
        resetItem.setOnAction(event -> clear());
        rebuild();
    }

    MenuButton node() {
        return button;
    }

    /**
     * Варианты в порядке показа: значение → подпись. Отмеченные значения, которых больше нет
     * среди вариантов, снимаются.
     */
    void setOptions(Map<T, String> newOptions) {
        if (List.copyOf(options.entrySet()).equals(List.copyOf(newOptions.entrySet()))) {
            return; // те же варианты в том же порядке — меню не трогаем
        }
        options = new LinkedHashMap<>(newOptions);
        boolean dropped = selected.retainAll(options.keySet());
        rebuild();
        if (dropped) {
            onChange.accept(Set.copyOf(selected));
        }
    }

    void clear() {
        if (!selected.isEmpty()) {
            selected.clear();
            rebuild();
            onChange.accept(Set.of());
        }
    }

    private void rebuild() {
        button.getItems().clear();
        if (options.isEmpty()) {
            MenuItem empty = new MenuItem("Нет вариантов");
            empty.setDisable(true);
            button.getItems().add(empty);
        }
        options.forEach((value, label) -> {
            CheckBox box = new CheckBox(label);
            box.setSelected(selected.contains(value));
            box.getStyleClass().add("filter-menu-check");
            box.selectedProperty().addListener((obs, was, now) -> {
                if (now) {
                    selected.add(value);
                } else {
                    selected.remove(value);
                }
                showTitle();
                onChange.accept(Set.copyOf(selected));
            });
            CustomMenuItem item = new CustomMenuItem(box);
            item.setHideOnClick(false);
            button.getItems().add(item);
        });
        button.getItems().addAll(new SeparatorMenuItem(), resetItem);
        showTitle();
    }

    /** Длинное название в подписи кнопки обрезается, целиком оно видно в списке. */
    private static String shorten(String label) {
        return label.length() <= 18 ? label : label.substring(0, 17).strip() + "…";
    }

    /** «Проект», «Проект: Миграция CRM» или «Проект: 3». */
    private void showTitle() {
        if (selected.isEmpty()) {
            button.setText(title);
        } else if (selected.size() == 1) {
            button.setText(title + ": " + shorten(options.getOrDefault(selected.iterator().next(), "…")));
        } else {
            button.setText(title + ": " + selected.size());
        }
        resetItem.setDisable(selected.isEmpty());
        button.getStyleClass().remove("filter-menu-active");
        if (!selected.isEmpty()) {
            button.getStyleClass().add("filter-menu-active");
        }
    }
}

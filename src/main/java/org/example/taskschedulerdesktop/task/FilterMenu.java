package org.example.taskschedulerdesktop.task;

import javafx.scene.control.Hyperlink;
import org.example.taskschedulerdesktop.shared.ui.MultiSelectField;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Выпадающий фильтр с галочками: «Проект», «Исполнитель», «Срок». В поле можно печатать — список
 * фильтруется; галочки не закрывают список, можно отметить несколько значений подряд.
 * Ничего не отмечено — фильтр выключен.
 *
 * @param <T> значение фильтра
 */
final class FilterMenu<T> {

    private final MultiSelectField<T> field = new MultiSelectField<>();
    private final String title;
    private final Consumer<Set<T>> onChange;
    private Map<T, String> options = Map.of();
    private final Hyperlink resetLink = new Hyperlink("Снять все");

    FilterMenu(String title, Consumer<Set<T>> onChange) {
        this.title = title;
        this.onChange = onChange;
        field.getStyleClass().add("filter-menu");
        field.setPrefWidth(185);
        field.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE); // не сжимать подпись, сжимается поле поиска
        field.setPromptText(title);
        // «Проект: Миграция CRM» или «Проект: 3»
        field.setSummary(labels -> labels.size() == 1 ? title + ": " + shorten(labels.get(0)) : title + ": " + labels.size());
        field.setEmptyHint("Нет вариантов");
        resetLink.getStyleClass().add("filter-reset-link");
        resetLink.setOnAction(event -> clear());
        field.setFooter(resetLink);
        field.setOnChange(values -> {
            showActive();
            onChange.accept(values);
        });
        showActive();
    }

    MultiSelectField<T> node() {
        return field;
    }

    /**
     * Варианты в порядке показа: значение → подпись. Отмеченные значения, которых больше нет
     * среди вариантов, снимаются.
     */
    void setOptions(Map<T, String> newOptions) {
        if (List.copyOf(options.entrySet()).equals(List.copyOf(newOptions.entrySet()))) {
            return; // те же варианты в том же порядке — список не трогаем
        }
        options = new LinkedHashMap<>(newOptions);
        Set<T> before = field.selected();
        field.setOptions(options);
        if (!field.selected().equals(before)) {
            showActive();
            onChange.accept(field.selected());
        }
    }

    void clear() {
        if (!field.selected().isEmpty()) {
            field.setSelected(List.of());
            showActive();
            onChange.accept(Set.of());
        }
    }

    /** Длинное название в поле обрезается, целиком оно видно в списке. */
    private static String shorten(String label) {
        return label.length() <= 10 ? label : label.substring(0, 9).strip() + "…";
    }

    private void showActive() {
        boolean active = !field.selected().isEmpty();
        resetLink.setDisable(!active);
        field.getStyleClass().remove("filter-menu-active");
        if (active) {
            field.getStyleClass().add("filter-menu-active");
        }
    }
}

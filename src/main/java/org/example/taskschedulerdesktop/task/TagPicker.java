package org.example.taskschedulerdesktop.task;

import org.example.taskschedulerdesktop.shared.ui.MultiSelectField;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Выбор тегов задачи из существующих: поле с курсором и список с галочками под ним. Вводить тег вручную
 * не нужно: нельзя ошибиться в названии или указать тег, которого нет (теги заводит администратор
 * в разделе «Теги»).
 */
final class TagPicker {

    private final MultiSelectField<String> field;

    TagPicker(MultiSelectField<String> field, String emptyText) {
        this.field = field;
        field.setPromptText(emptyText);
        field.setEmptyHint("Тегов пока нет — их добавляет администратор в разделе «Теги»");
    }

    /**
     * Показать существующие теги, отметив выбранные. Выбранный тег, которого нет среди существующих
     * (например, его удалили), остаётся в списке отмеченным — его можно снять.
     */
    void show(Collection<String> available, Collection<String> selected) {
        Set<String> all = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        all.addAll(available);
        all.addAll(selected);
        Map<String, String> options = new LinkedHashMap<>();
        all.forEach(name -> options.put(name, name));
        field.setOptions(options);
        Set<String> chosen = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        chosen.addAll(selected);
        field.setSelected(all.stream().filter(chosen::contains).toList());
    }

    List<String> selected() {
        return new ArrayList<>(field.selected());
    }
}

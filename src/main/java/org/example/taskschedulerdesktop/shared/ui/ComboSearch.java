package org.example.taskschedulerdesktop.shared.ui;

import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Выпадающий список (один вариант), в котором можно печатать: нажали на поле — появился курсор,
 * текущее значение выделено, набранный текст его заменяет, а в открывшемся списке остаются варианты
 * с этим текстом (без учёта регистра, «ё» = «е»). Enter выбирает первый подходящий.
 * <p>
 * Набранный текст сам значение не меняет: ушли из поля, ничего не выбрав, — значение прежнее
 * (и зависящие от него списки не дёргаются). Значение меняется только выбором варианта.
 */
public final class ComboSearch<T> {

    private final ComboBox<T> combo;
    private final TextField editor;
    /** Все варианты, пока список отфильтрован; null — фильтра нет. */
    private List<T> all;
    private boolean applying;
    /** Как показывать вариант текстом: конвертер, заданный экраном (до обёртки), или toString. */
    private StringConverter<T> base;
    private final StringConverter<T> converter = new StringConverter<>() {
        @Override
        public String toString(T item) {
            return text(item);
        }

        @Override
        public T fromString(String text) {
            // Только точное совпадение с вариантом; иначе значение не меняется
            for (T item : all != null ? all : combo.getItems()) {
                if (text(item).equalsIgnoreCase(text == null ? "" : text.strip())) {
                    return item;
                }
            }
            return combo.getValue();
        }
    };

    private ComboSearch(ComboBox<T> combo) {
        this.combo = combo;
        this.base = combo.getConverter();
        combo.setEditable(true);
        combo.setConverter(converter);
        // Экран может задать свой конвертер позже (например, для проектов) — оборачиваем и его
        combo.converterProperty().addListener((obs, old, now) -> {
            if (now != converter) {
                base = now;
                combo.setConverter(converter);
            }
        });
        editor = combo.getEditor();
        editor.textProperty().addListener((obs, old, text) -> {
            // Текст совпадает со значением — это список сам показал выбранное, а не ввод пользователя
            if (!applying && !text.equals(text(combo.getValue()))) {
                filter(text);
            }
        });
        // Нажали на поле — значение выделено, можно сразу печатать поверх
        editor.focusedProperty().addListener((obs, was, focused) -> {
            if (focused) {
                Platform.runLater(editor::selectAll);
            } else {
                reset();
            }
        });
        // На самом списке, а не на поле: список перехватывает Enter раньше поля и подставил бы набранный текст
        combo.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER && all != null && !combo.getItems().isEmpty()) {
                event.consume();
                choose(combo.getItems().get(0));
            } else if (event.getCode() == KeyCode.ESCAPE && all != null) {
                event.consume();
                reset();
                combo.hide();
            }
        });
        combo.addEventHandler(ComboBox.ON_HIDDEN, event -> {
            if (!editor.isFocused()) {
                reset();
            }
        });
        combo.valueProperty().addListener((obs, old, value) -> {
            if (!applying && all != null) {
                reset(); // выбрали вариант из списка — фильтр больше не нужен
            }
        });
        combo.getItems().addListener((javafx.collections.ListChangeListener<T>) change -> {
            if (!applying && all != null) {
                all = new ArrayList<>(combo.getItems()); // варианты заменили, пока список отфильтрован
            }
        });
    }

    public static <T> void install(ComboBox<T> combo) {
        new ComboSearch<>(combo);
    }

    private void filter(String text) {
        if (all == null) {
            all = new ArrayList<>(combo.getItems());
        }
        String needle = normalize(text);
        List<T> shown = new ArrayList<>();
        for (T item : all) {
            if (needle.isEmpty() || normalize(text(item)).contains(needle)) {
                shown.add(item);
            }
        }
        // Замена вариантов заставляет список показать в поле текущее значение — возвращаем набранное
        int caret = editor.getCaretPosition();
        replaceItems(shown);
        if (!combo.isShowing()) {
            combo.show();
        }
        applying = true;
        try {
            if (!editor.getText().equals(text)) {
                editor.setText(text);
            }
            editor.positionCaret(Math.min(caret, text.length()));
        } finally {
            applying = false;
        }
    }

    private void choose(T item) {
        applying = true;
        try {
            combo.setValue(item);
        } finally {
            applying = false;
        }
        reset();
        combo.hide();
    }

    /** Все варианты на место, в поле — текущее значение. */
    private void reset() {
        if (all != null) {
            List<T> items = all;
            all = null;
            replaceItems(items);
        }
        applying = true;
        try {
            editor.setText(text(combo.getValue()));
        } finally {
            applying = false;
        }
    }

    /** Заменить варианты, не трогая значение (замена всех вариантов сразу его не сбрасывает). */
    private void replaceItems(List<T> items) {
        applying = true;
        try {
            T value = combo.getValue();
            combo.getItems().setAll(items);
            if (combo.getValue() != value) {
                combo.setValue(value);
            }
        } finally {
            applying = false;
        }
    }

    private String text(T item) {
        if (item == null) {
            return "";
        }
        return base != null ? base.toString(item) : String.valueOf(item);
    }

    private static String normalize(String text) {
        return text == null ? "" : text.strip().toLowerCase(Locale.ROOT).replace('ё', 'е');
    }
}

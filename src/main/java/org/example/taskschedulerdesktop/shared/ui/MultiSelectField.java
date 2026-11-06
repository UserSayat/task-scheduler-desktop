package org.example.taskschedulerdesktop.shared.ui;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Поле выбора нескольких вариантов (теги, соисполнители, фильтры): нажали — в поле курсор и под ним
 * список с галочками; набранный текст фильтрует список (без учёта регистра, «ё» = «е»), Enter отмечает
 * первый подходящий вариант. Галочки не закрывают список. Ушли из поля — в нём снова видно, что выбрано.
 * <p>
 * Список — отдельное всплывающее окно, которое не забирает ввод с клавиатуры: печатать можно прямо в поле,
 * как в обычном выпадающем списке.
 *
 * @param <T> значение варианта
 */
public class MultiSelectField<T> extends HBox {

    private final TextField field = new TextField();
    private final StackPane arrowButton = new StackPane();
    private final Popup popup = new Popup();
    private final VBox optionsBox = new VBox();
    private final ScrollPane scroll = new ScrollPane(optionsBox);
    private final Label nothingFound = new Label("Ничего не найдено");
    private final Label emptyHint = new Label();
    private final VBox popupContent = new VBox();

    private final Map<T, CheckBox> boxes = new LinkedHashMap<>();
    private final Set<T> selected = new LinkedHashSet<>();
    private String promptText = "";
    private Function<List<String>, String> summary = labels -> String.join(", ", labels);
    private Consumer<Set<T>> onChange = values -> { };
    /** Пока пользователь печатает в поле — в нём запрос, а не перечень выбранного. */
    private boolean searching;
    private boolean updating;

    public MultiSelectField() {
        getStyleClass().add("multi-select");
        setAlignment(Pos.CENTER_LEFT);
        field.getStyleClass().add("multi-select-field");
        HBox.setHgrow(field, Priority.ALWAYS);
        field.setMaxWidth(Double.MAX_VALUE);
        Region arrow = new Region();
        arrow.getStyleClass().add("arrow");
        arrowButton.getStyleClass().add("arrow-button");
        arrowButton.getChildren().add(arrow);
        getChildren().addAll(field, arrowButton);

        optionsBox.getStyleClass().add("multi-select-options");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setMaxHeight(320);
        scroll.getStyleClass().add("multi-select-scroll");
        nothingFound.getStyleClass().add("multi-select-hint");
        emptyHint.getStyleClass().add("multi-select-hint");
        emptyHint.setWrapText(true);
        popupContent.getStyleClass().add("multi-select-popup");
        popupContent.getChildren().addAll(scroll, nothingFound, emptyHint);
        popup.getContent().add(popupContent);
        popup.setAutoHide(true);
        popup.setOnAutoHide(event -> finishSearch());
        // Список закрылся, а в поле уже не печатают — показать в поле выбранное
        popup.setOnHidden(event -> {
            if (!field.isFocused()) {
                finishSearch();
            }
        });
        sceneProperty().addListener((obs, old, scene) -> {
            // Стили страницы и для всплывающего списка
            if (scene != null) {
                popupContent.getStylesheets().setAll(scene.getStylesheets());
            }
        });

        field.textProperty().addListener((obs, old, text) -> {
            if (!updating) {
                searching = true;
                filter(text);
                open();
            }
        });
        field.focusedProperty().addListener((obs, was, focused) -> {
            if (focused) {
                startSearch();
            } else {
                popup.hide();
                finishSearch();
            }
        });
        field.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> open());
        arrowButton.setOnMousePressed(event -> {
            if (popup.isShowing()) {
                popup.hide();
            } else {
                field.requestFocus();
                open();
            }
            event.consume();
        });
        field.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                checkFirstMatch();
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE && popup.isShowing()) {
                popup.hide();
                finishSearch();
                event.consume();
            } else if (event.getCode() == KeyCode.DOWN) {
                open();
                event.consume();
            }
        });
        showSummary();
    }

    // ===== Настройка =====

    /** Что показывать в пустом поле (ничего не выбрано): «Без тегов», «Проект». */
    public void setPromptText(String text) {
        promptText = text == null ? "" : text;
        showSummary();
    }

    public String getPromptText() {
        return promptText;
    }

    /** Как показывать выбранное в поле; по умолчанию — подписи через запятую. */
    public void setSummary(Function<List<String>, String> summary) {
        this.summary = summary;
        showSummary();
    }

    /** Вызывается при каждой галочке. */
    public void setOnChange(Consumer<Set<T>> onChange) {
        this.onChange = onChange;
    }

    /** Подсказка, когда вариантов нет вовсе. */
    public void setEmptyHint(String hint) {
        emptyHint.setText(hint);
        updateHints();
    }

    /** Дополнительная строка внизу списка (например, «Снять все»). */
    public void setFooter(Node footer) {
        popupContent.getChildren().add(footer);
    }

    // ===== Варианты и выбор =====

    /** Варианты в порядке показа: значение → подпись. Отмеченные, которых больше нет, снимаются. */
    public void setOptions(Map<T, String> options) {
        boxes.clear();
        optionsBox.getChildren().clear();
        selected.retainAll(options.keySet());
        options.forEach((value, label) -> {
            CheckBox box = new CheckBox(label);
            box.getStyleClass().add("filter-menu-check");
            box.setFocusTraversable(false);
            box.setMaxWidth(Double.MAX_VALUE);
            box.setSelected(selected.contains(value));
            box.selectedProperty().addListener((obs, was, now) -> {
                if (updating) {
                    return;
                }
                if (now) {
                    selected.add(value);
                } else {
                    selected.remove(value);
                }
                if (!searching) {
                    showSummary();
                }
                onChange.accept(selected());
            });
            boxes.put(value, box);
            optionsBox.getChildren().add(box);
        });
        filter(searching ? field.getText() : "");
        if (!searching) {
            showSummary();
        }
    }

    /** Отметить ровно эти значения (без вызова onChange). */
    public void setSelected(Collection<T> values) {
        updating = true;
        try {
            selected.clear();
            for (T value : values) {
                if (boxes.containsKey(value)) {
                    selected.add(value);
                }
            }
            boxes.forEach((value, box) -> box.setSelected(selected.contains(value)));
        } finally {
            updating = false;
        }
        if (!searching) {
            showSummary();
        }
    }

    /** Отмеченные значения в порядке вариантов. */
    public Set<T> selected() {
        Set<T> ordered = new LinkedHashSet<>();
        for (T value : boxes.keySet()) {
            if (selected.contains(value)) {
                ordered.add(value);
            }
        }
        return ordered;
    }

    public void hidePopup() {
        popup.hide();
    }

    // ===== Поиск и список =====

    private void startSearch() {
        searching = true;
        setText("");
        field.setPromptText(summaryText().isEmpty() ? promptText : summaryText());
        filter("");
    }

    private void finishSearch() {
        searching = false;
        filter("");
        showSummary();
    }

    private void open() {
        if (popup.isShowing() || getScene() == null || getScene().getWindow() == null || isDisabled()) {
            return;
        }
        Bounds bounds = localToScreen(getBoundsInLocal());
        if (bounds == null) {
            return;
        }
        popupContent.setPrefWidth(Math.max(bounds.getWidth(), 220));
        popup.show(this, bounds.getMinX(), bounds.getMaxY() + 2);
    }

    private void filter(String text) {
        String needle = normalize(text);
        boolean any = false;
        for (CheckBox box : boxes.values()) {
            boolean match = needle.isEmpty() || normalize(box.getText()).contains(needle);
            box.setVisible(match);
            box.setManaged(match);
            any |= match;
        }
        nothingFound.setVisible(!boxes.isEmpty() && !any);
        nothingFound.setManaged(!boxes.isEmpty() && !any);
        updateHints();
    }

    private void updateHints() {
        boolean none = boxes.isEmpty() && !emptyHint.getText().isEmpty();
        emptyHint.setVisible(none);
        emptyHint.setManaged(none);
        scroll.setVisible(!boxes.isEmpty());
        scroll.setManaged(!boxes.isEmpty());
    }

    /** Enter: отметить первый подходящий вариант и начать поиск заново. */
    private void checkFirstMatch() {
        for (CheckBox box : boxes.values()) {
            if (box.isVisible()) {
                box.setSelected(true);
                break;
            }
        }
        setText("");
        filter("");
    }

    private void showSummary() {
        field.setPromptText(promptText);
        setText(summaryText());
    }

    private String summaryText() {
        List<String> labels = new ArrayList<>();
        for (T value : selected()) {
            labels.add(boxes.get(value).getText());
        }
        return labels.isEmpty() ? "" : summary.apply(labels);
    }

    private void setText(String text) {
        updating = true;
        try {
            field.setText(text);
        } finally {
            updating = false;
        }
        // Выбранное показываем с начала (длинное обрезается справа), при вводе курсор — в конце
        Platform.runLater(() -> field.positionCaret(searching ? field.getText().length() : 0));
    }

    private static String normalize(String text) {
        return text == null ? "" : text.strip().toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    /** Для проверок: поле ввода. */
    public TextField editor() {
        return field;
    }
}

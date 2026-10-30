package org.example.taskschedulerdesktop.shared.ui;

import javafx.scene.Parent;
import javafx.scene.control.DatePicker;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public final class DateFormatter {

    private DateFormatter() {}

    private static final String[] MONTHS_SHORT = {
            "янв", "фев", "мар", "апр", "май", "июн",
            "июл", "авг", "сен", "окт", "ноя", "дек"
    };

    private static final String[] MONTHS_FULL = {
            "января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря"
    };

    private static final Locale RU = Locale.forLanguageTag("ru");

    /** Формат даты из системных настроек (API.md, раздел 10). Пока настроек нет — как в ТЗ. */
    private static volatile String datePattern = "dd.MM.yyyy";
    private static volatile DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern(datePattern, RU);

    /** Вызывается при загрузке и изменении настроек. Неверный шаблон игнорируется. */
    public static void setDatePattern(String pattern) {
        if (pattern == null || pattern.isBlank() || pattern.equals(datePattern)) {
            return;
        }
        try {
            dateFormat = DateTimeFormatter.ofPattern(pattern, RU);
            datePattern = pattern;
        } catch (IllegalArgumentException ignored) {
            // оставляем прежний формат
        }
    }

    public static String datePattern() {
        return datePattern;
    }

    /**
     * Поля ввода дат на экране показывают и разбирают дату в формате из настроек.
     * Вызывается после загрузки FXML.
     */
    public static void applyToPickers(Parent root) {
        root.lookupAll(".date-picker").forEach(node -> {
            if (node instanceof DatePicker picker) {
                picker.setConverter(new StringConverter<>() {
                    @Override
                    public String toString(LocalDate date) {
                        return date == null ? "" : date.format(dateFormat);
                    }

                    @Override
                    public LocalDate fromString(String text) {
                        if (text == null || text.isBlank()) {
                            return null;
                        }
                        try {
                            return LocalDate.parse(text.strip(), dateFormat);
                        } catch (DateTimeParseException e) {
                            return picker.getValue(); // неверный ввод — остаётся прежняя дата
                        }
                    }
                });
                picker.setPromptText(datePattern.toLowerCase(RU)
                        .replace("dd", "дд").replace("mmmm", "месяц").replace("mm", "мм")
                        .replace("yyyy", "гггг").replace("d ", "д "));
            }
        });
    }

    private static final DateTimeFormatter FULL_DATE_WITH_DAY =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.forLanguageTag("ru"));

    /**
     * Форматирует дату как "18 сен".
     */
    public static String formatShort(LocalDate date) {
        if (date == null) return "";
        return date.getDayOfMonth() + " " + MONTHS_SHORT[date.getMonthValue() - 1];
    }

    /**
     * Полная дата в формате из настроек: "18.09.2026", "18 сентября 2026", "2026-09-18"...
     */
    public static String formatFull(LocalDate date) {
        if (date == null) return "";
        if (datePattern.contains("MMMM")) {
            // Родительный падеж («сентября»), а не именительный («сентябрь»)
            return date.getDayOfMonth() + " " + MONTHS_FULL[date.getMonthValue() - 1] + " " + date.getYear();
        }
        return date.format(dateFormat);
    }

    /**
     * Форматирует дату как "18 сен 2026".
     */
    public static String formatShortWithYear(LocalDate date) {
        if (date == null) return "";
        return date.getDayOfMonth() + " " + MONTHS_SHORT[date.getMonthValue() - 1]
                + " " + date.getYear();
    }

    /**
     * Форматирует дату как "18 сен 2026".
     */
    public static String formatFullWithDay(LocalDate date) {
        if (date == null) return "";
        String formatted = date.format(FULL_DATE_WITH_DAY);
        return formatted.toLowerCase();
    }

    /**
     * Форматирует дату как "Сегодня", "Завтра", "Через 3 дня", "18 сен".
     */
    public static String formatRelative(LocalDate date) {
        if (date == null) return "";

        LocalDate today = LocalDate.now();
        long days = java.time.temporal.ChronoUnit.DAYS.between(today, date);

        if (days == 0) return "Сегодня";
        if (days == 1) return "Завтра";
        if (days == -1) return "Вчера";
        if (days > 1 && days <= 7) return "Через " + days + " дня";
        if (days < -1 && days >= -7) return Math.abs(days) + " дня назад";

        return formatShort(date);
    }

    /**
     * Проверяет, просрочена ли дата.
     */
    public static boolean isOverdue(LocalDate date) {
        return date != null && date.isBefore(LocalDate.now());
    }
}
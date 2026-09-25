package org.example.taskschedulerdesktop.utils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
     * Форматирует дату как "18 сентября 2026".
     */
    public static String formatFull(LocalDate date) {
        if (date == null) return "";
        return date.getDayOfMonth() + " " + MONTHS_FULL[date.getMonthValue() - 1]
                + " " + date.getYear();
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
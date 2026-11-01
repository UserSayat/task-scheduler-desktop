package org.example.taskschedulerdesktop.task;

import java.text.Collator;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Locale;

/**
 * Порядок колонок таблицы задач при сортировке по возрастанию. Пустые значения — в конце.
 */
public final class TaskSorting {

    private static final Collator RUSSIAN = Collator.getInstance(Locale.forLanguageTag("ru"));

    /** Текст — по алфавиту, без учёта регистра. */
    public static final Comparator<String> TEXT = Comparator.nullsLast((a, b) -> RUSSIAN.compare(a, b));

    /** Приоритет — от важного к неважному: «Критический» первым. */
    public static final Comparator<TaskPriority> PRIORITY =
            Comparator.nullsLast(Comparator.<TaskPriority>naturalOrder().reversed());

    /** Статус — по ходу работы: «К выполнению» → «Завершена». */
    public static final Comparator<TaskStatus> STATUS = Comparator.nullsLast(Comparator.naturalOrder());

    /** Срок — ближайший первым, без срока — в конце. */
    public static final Comparator<LocalDate> DEADLINE = Comparator.nullsLast(Comparator.naturalOrder());

    static {
        RUSSIAN.setStrength(Collator.SECONDARY);
    }

    private TaskSorting() {}
}

package org.example.taskschedulerdesktop.task;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Фильтр таблицы задач. Внутри параметра значения складываются через «или» («К выполнению» или «В работе»),
 * параметры — через «и». Пустой набор — параметр не ограничивает. Поиск без учёта регистра по названию,
 * проекту, исполнителю и тегам.
 */
public record TaskFilter(
        String query,
        Set<TaskStatus> statuses,
        Set<TaskPriority> priorities,
        Set<UUID> projectIds,
        Set<UUID> executorIds,
        Set<DeadlineRange> deadlines
) implements Predicate<TaskView> {

    public static final TaskFilter ALL = new TaskFilter("", Set.of(), Set.of(), Set.of(), Set.of(), Set.of());

    public TaskFilter {
        query = query == null ? "" : query;
        statuses = Set.copyOf(statuses);
        priorities = Set.copyOf(priorities);
        projectIds = Set.copyOf(projectIds);
        executorIds = Set.copyOf(executorIds);
        deadlines = Set.copyOf(deadlines);
    }

    /** Срок задачи. */
    public enum DeadlineRange {
        OVERDUE("Просрочено"),
        TODAY("Сегодня"),
        THIS_WEEK("На этой неделе"),
        NO_DEADLINE("Без срока");

        private final String displayName;

        DeadlineRange(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        boolean matches(TaskView task, LocalDate today) {
            LocalDate deadline = task.deadline();
            return switch (this) {
                case OVERDUE -> deadline != null && deadline.isBefore(today) && task.status() != TaskStatus.DONE;
                case TODAY -> today.equals(deadline);
                // С сегодняшнего дня по воскресенье
                case THIS_WEEK -> deadline != null && !deadline.isBefore(today)
                        && !deadline.isAfter(today.with(DayOfWeek.SUNDAY));
                case NO_DEADLINE -> deadline == null;
            };
        }
    }

    public TaskFilter withQuery(String query) {
        return new TaskFilter(query, statuses, priorities, projectIds, executorIds, deadlines);
    }

    public TaskFilter withStatuses(Set<TaskStatus> statuses) {
        return new TaskFilter(query, statuses, priorities, projectIds, executorIds, deadlines);
    }

    public TaskFilter withPriorities(Set<TaskPriority> priorities) {
        return new TaskFilter(query, statuses, priorities, projectIds, executorIds, deadlines);
    }

    public TaskFilter withProjects(Set<UUID> projectIds) {
        return new TaskFilter(query, statuses, priorities, projectIds, executorIds, deadlines);
    }

    public TaskFilter withExecutors(Set<UUID> executorIds) {
        return new TaskFilter(query, statuses, priorities, projectIds, executorIds, deadlines);
    }

    public TaskFilter withDeadlines(Set<DeadlineRange> deadlines) {
        return new TaskFilter(query, statuses, priorities, projectIds, executorIds, deadlines);
    }

    /** Ограничивает ли фильтр хоть что-то (пробелы в поиске не считаются). */
    public boolean isActive() {
        return !query.isBlank() || !statuses.isEmpty() || !priorities.isEmpty()
                || !projectIds.isEmpty() || !executorIds.isEmpty() || !deadlines.isEmpty();
    }

    @Override
    public boolean test(TaskView task) {
        return test(task, LocalDate.now());
    }

    public boolean test(TaskView task, LocalDate today) {
        return (statuses.isEmpty() || statuses.contains(task.status()))
                && (priorities.isEmpty() || priorities.contains(task.priority()))
                && (projectIds.isEmpty() || projectIds.contains(task.projectId()))
                && (executorIds.isEmpty() || (task.responsibleId() != null && executorIds.contains(task.responsibleId())))
                && (deadlines.isEmpty() || deadlines.stream().anyMatch(range -> range.matches(task, today)))
                && matchesQuery(task);
    }

    private boolean matchesQuery(TaskView task) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return true;
        }
        return contains(task.title(), needle)
                || contains(task.projectName(), needle)
                || contains(task.responsibleName(), needle)
                || task.tagNames().stream().anyMatch(tag -> contains(tag, needle));
    }

    private static boolean contains(String text, String needle) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(needle);
    }
}

package org.example.taskschedulerdesktop.task;

import org.example.taskschedulerdesktop.task.TaskFilter.DeadlineRange;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Фильтр и порядок сортировки таблицы задач. */
class TaskFilterTest {

    /** Среда. */
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);

    private final UUID portal = UUID.randomUUID();
    private final UUID crm = UUID.randomUUID();
    private final UUID alexey = UUID.randomUUID();
    private final UUID maria = UUID.randomUUID();

    private final TaskView layout = task("Утвердить макеты", portal, "Редизайн портала", alexey, "Алексей",
            TaskStatus.ON_REVIEW, TaskPriority.HIGH, TODAY.minusDays(1), "дизайн");
    private final TaskView integration = task("Настроить интеграцию с 1С", crm, "Миграция CRM", maria, "Мария",
            TaskStatus.IN_PROGRESS, TaskPriority.LOW, null, "backend");
    private final TaskView report = task("Отчёт", crm, "Миграция CRM", alexey, "Алексей",
            TaskStatus.NEW, TaskPriority.CRITICAL, TODAY.plusDays(4), "аналитика");
    private final TaskView done = task("Готово", portal, "Редизайн портала", maria, "Мария",
            TaskStatus.DONE, TaskPriority.MEDIUM, TODAY.minusDays(3), "дизайн");

    private final List<TaskView> all = List.of(layout, integration, report, done);

    @Test
    void emptyFilterShowsEverything() {
        assertEquals(all, shown(TaskFilter.ALL));
        assertEquals(all, shown(TaskFilter.ALL.withQuery("   ")));
        assertFalse(TaskFilter.ALL.withQuery("   ").isActive(), "пробелы — не фильтр");
    }

    @Test
    void searchLooksAtTitleProjectExecutorAndTagsIgnoringCase() {
        assertEquals(List.of(layout), shown(TaskFilter.ALL.withQuery("макеты")));
        assertEquals(List.of(integration, report), shown(TaskFilter.ALL.withQuery("crm")));
        assertEquals(List.of(integration, done), shown(TaskFilter.ALL.withQuery("МАРИЯ")));
        assertEquals(List.of(layout, done), shown(TaskFilter.ALL.withQuery("дизайн")), "тег");
    }

    @Test
    void valuesOfOneParameterAreOrParametersAreAnd() {
        TaskFilter twoStatuses = TaskFilter.ALL.withStatuses(Set.of(TaskStatus.NEW, TaskStatus.IN_PROGRESS));
        assertEquals(List.of(integration, report), shown(twoStatuses));
        assertEquals(List.of(report), shown(twoStatuses.withPriorities(Set.of(TaskPriority.CRITICAL, TaskPriority.HIGH))));
        assertEquals(List.of(integration), shown(twoStatuses.withExecutors(Set.of(maria))));
        assertEquals(List.of(layout, done), shown(TaskFilter.ALL.withProjects(Set.of(portal))));
        assertEquals(all, shown(TaskFilter.ALL.withProjects(Set.of(portal, crm))));
    }

    @Test
    void deadlineRanges() {
        assertEquals(List.of(layout), shown(TaskFilter.ALL.withDeadlines(Set.of(DeadlineRange.OVERDUE))),
                "завершённая задача не просрочена");
        assertEquals(List.of(report), shown(TaskFilter.ALL.withDeadlines(Set.of(DeadlineRange.THIS_WEEK))),
                "до воскресенья включительно");
        assertEquals(List.of(integration), shown(TaskFilter.ALL.withDeadlines(Set.of(DeadlineRange.NO_DEADLINE))));
        assertEquals(List.of(layout, integration),
                shown(TaskFilter.ALL.withDeadlines(Set.of(DeadlineRange.OVERDUE, DeadlineRange.NO_DEADLINE))));

        TaskView today = task("Сегодня", crm, "Миграция CRM", maria, "Мария",
                TaskStatus.NEW, TaskPriority.LOW, TODAY, "x");
        assertTrue(TaskFilter.ALL.withDeadlines(Set.of(DeadlineRange.TODAY)).test(today, TODAY));
        assertTrue(TaskFilter.ALL.withDeadlines(Set.of(DeadlineRange.THIS_WEEK)).test(today, TODAY));
        assertFalse(TaskFilter.ALL.withDeadlines(Set.of(DeadlineRange.THIS_WEEK))
                .test(today, TODAY.plusDays(1)), "срок уже прошёл");
    }

    @Test
    void menuOptionsAreUniqueAndAlphabetical() {
        Map<UUID, String> projects = TasksController.sortedOptions(all, TaskView::projectId, TaskView::projectName);
        assertEquals(List.of("Миграция CRM", "Редизайн портала"), List.copyOf(projects.values()));
        Map<UUID, String> executors = TasksController.sortedOptions(all, TaskView::responsibleId, TaskView::responsibleName);
        assertEquals(List.of(alexey, maria), List.copyOf(executors.keySet()));
    }

    @Test
    void sortOrderOfColumns() {
        List<TaskPriority> priorities = new ArrayList<>(Arrays.asList(TaskPriority.LOW, null, TaskPriority.CRITICAL, TaskPriority.MEDIUM));
        priorities.sort(TaskSorting.PRIORITY);
        assertEquals(Arrays.asList(TaskPriority.CRITICAL, TaskPriority.MEDIUM, TaskPriority.LOW, null), priorities);

        List<LocalDate> deadlines = new ArrayList<>(Arrays.asList(null, LocalDate.of(2026, 8, 5), LocalDate.of(2026, 7, 31)));
        deadlines.sort(TaskSorting.DEADLINE);
        assertEquals(Arrays.asList(LocalDate.of(2026, 7, 31), LocalDate.of(2026, 8, 5), null), deadlines, "без срока — в конце");

        List<String> names = new ArrayList<>(List.of("елена", "Алексей", "Ёлка", "борис"));
        names.sort(TaskSorting.TEXT);
        assertEquals(List.of("Алексей", "борис", "елена", "Ёлка"), names, "по алфавиту без учёта регистра");
    }

    @Test
    void countWordAgreesWithNumber() {
        assertEquals("задача", TasksController.tasksWord(1));
        assertEquals("задачи", TasksController.tasksWord(3));
        assertEquals("задач", TasksController.tasksWord(5));
        assertEquals("задач", TasksController.tasksWord(11));
        assertEquals("задача", TasksController.tasksWord(21));
        assertEquals("задач", TasksController.tasksWord(112));
    }

    private List<TaskView> shown(TaskFilter filter) {
        return all.stream().filter(task -> filter.test(task, TODAY)).toList();
    }

    private static TaskView task(String title, UUID projectId, String project, UUID executorId, String executor,
                                 TaskStatus status, TaskPriority priority, LocalDate deadline, String tag) {
        return new TaskView(UUID.randomUUID(), title, null, projectId, project, executorId, executor,
                status, priority, deadline, List.of(tag), true);
    }
}

package org.example.taskschedulerdesktop.project;

import java.time.LocalDate;

/**
 * Поля проекта, которые есть в карточке и форме редактирования, но не нужны в списках.
 */
public record ProjectInfo(String description, Visibility visibility, LocalDate startDate, LocalDate deadline) {
}

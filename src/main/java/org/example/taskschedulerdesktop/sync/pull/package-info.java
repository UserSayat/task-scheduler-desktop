/**
 * Получение: {@code ChangeApplier} записывает данные pull и snapshot в H2 по правилу field-level LWW,
 * {@code AppliedChanges} — отчёт о том, что изменилось (для обновления экранов и догрузки проектов).
 */
package org.example.taskschedulerdesktop.sync.pull;

/**
 * Локальное состояние синхронизации в H2.
 * <ul>
 *   <li>{@code ChangeTracker} — фиксирует каждую локальную правку в журнале;</li>
 *   <li>{@code SyncLogRepository} — sync_log, очередь неотправленных изменений;</li>
 *   <li>{@code FieldVersionRepository} — field_versions, метки времени полей для LWW;</li>
 *   <li>{@code SyncErrorRepository} — sync_errors, изменения, отклонённые сервером;</li>
 *   <li>{@code SyncStateRepository} — sync_state: устройство, курсор, вошедший пользователь, refresh-токен;</li>
 *   <li>{@code LocalReplica} — очистка базы при смене пользователя и удаление проекта при REVOKE.</li>
 * </ul>
 */
package org.example.taskschedulerdesktop.sync.local;

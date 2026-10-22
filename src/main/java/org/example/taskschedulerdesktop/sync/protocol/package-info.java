/**
 * Протокол синхронизации: как выглядят сообщения и как они доходят до сервера.
 * <ul>
 *   <li>{@code SyncMessages} — DTO push, pull и snapshot (API.md, 8.2–8.5);</li>
 *   <li>{@code SyncValues} — значения полей JSON ↔ Java;</li>
 *   <li>{@code EntityType}, {@code Operation} — перечисления протокола;</li>
 *   <li>{@code SyncApi} — эндпоинты синхронизации, {@code HttpSyncApi} — их вызов через ApiClient.</li>
 * </ul>
 */
package org.example.taskschedulerdesktop.sync.protocol;

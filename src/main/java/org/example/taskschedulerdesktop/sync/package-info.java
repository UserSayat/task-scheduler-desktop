/**
 * Синхронизация с сервером (API.md, 8). В корне — точка входа:
 * <ul>
 *   <li>{@code SyncScheduler} — <b>когда</b> синхронизировать (таймер, после правки, по кнопке);</li>
 *   <li>{@code SyncService} — <b>в каком порядке</b>: push, затем pull, при необходимости snapshot;</li>
 *   <li>{@code SyncStatus} — состояние для индикатора в шапке окна.</li>
 * </ul>
 * Подпакеты:
 * <ul>
 *   <li>{@code protocol} — формат сообщений и доставка на сервер;</li>
 *   <li>{@code local} — служебные таблицы H2: журнал, версии полей, ошибки;</li>
 *   <li>{@code push} — сборка локальных изменений для отправки;</li>
 *   <li>{@code pull} — запись данных с сервера в H2 по правилу LWW;</li>
 *   <li>{@code adapter} — контракт, который реализует каждая фича ({@code TaskSyncAdapter}, ...).</li>
 * </ul>
 */
package org.example.taskschedulerdesktop.sync;

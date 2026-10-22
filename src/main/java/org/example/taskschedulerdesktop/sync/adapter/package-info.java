/**
 * Контракт между синхронизацией и фичами. Синхронизация не знает полей задачи или проекта —
 * их описывает адаптер в пакете фичи ({@code TaskSyncAdapter}, {@code ProjectSyncAdapter}, ...).
 * <ul>
 *   <li>{@code SyncEntityAdapter} — прочитать поля сущности для push и записать пришедшие с сервера;</li>
 *   <li>{@code KnownUsers} — гарантировать строки пользователей, на которых ссылаются данные;</li>
 *   <li>{@code MissingReferenceException} — сущность ссылается на проект, которого ещё нет локально.</li>
 * </ul>
 */
package org.example.taskschedulerdesktop.sync.adapter;

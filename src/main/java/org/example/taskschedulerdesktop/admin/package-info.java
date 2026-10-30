/**
 * Администрирование сотрудников (только для роли ADMIN).
 * <p>
 * Список берётся из локальной базы — туда сотрудников приносит синхронизация. Изменения идут
 * через REST сервера (API.md, раздел 6), поэтому работают только онлайн; после успешного
 * запроса запускается синхронизация, и список обновляется.
 * <ul>
 *   <li>{@code Employee}, {@code RoleAssignment} — сотрудник и его роли;</li>
 *   <li>{@code EmployeeRepository} — чтение сотрудников и ролей из H2;</li>
 *   <li>{@code EmployeeAdminApi} — запросы к серверу;</li>
 *   <li>{@code EmployeeAdminService} — всё вместе в фоне, с понятными сообщениями об ошибках;</li>
 *   <li>{@code EmployeesController}, {@code EmployeeDialogController} — экран и диалог.</li>
 * </ul>
 */
package org.example.taskschedulerdesktop.admin;

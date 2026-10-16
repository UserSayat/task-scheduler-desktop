# TaskHub — Desktop Client

JavaFX-клиент корпоративного планировщика задач.

## Документация

- **Схема данных:** [SCHEMA.md (backend)](https://github.com/UserSayat/task-manager-api/blob/main/SCHEMA.md)
- **API:** [API.md (backend)](https://github.com/UserSayat/task-manager-api/blob/main/API.md)

## Стек

- Java 21
- JavaFX 21
- H2 (offline-first)
- Flyway (миграции)
- HikariCP

## Запуск

```bash
    mvn clean javafx:run
```

### Режим разработки

Пока нет входа и синхронизации с сервером, клиент работает в режиме разработки: при запуске в локальную базу добавляются демо-сотрудники, текущий пользователь — демо-администратор. Выключить: `-Dtaskhub.dev=false`.

H2 TCP-сервер для просмотра базы из IDE: `-Dtaskhub.h2.tcp=true` (порт 9092, только localhost).

## Слой данных

- `UnitOfWork` — одна транзакция на операцию; репозитории берут соединение из неё.
- `ChangeTracker` — каждое изменение сущности в той же транзакции пишет `field_versions` и `sync_log` (очередь для синхронизации).
- Модели (`models`) — зеркало таблиц; экраны работают с `TaskView` и `ProjectView`.
- Тесты слоя данных: `mvn test`.

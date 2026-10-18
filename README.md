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

## Структура (package by feature)

Код сгруппирован по фичам, как и на бэкенде: всё о задачах — в `task`, о проектах — в `project` и т. д.
Внутри фичи лежат модель, репозиторий, сервисы и экраны.

```
org.example.taskschedulerdesktop
├── MainLauncher, TaskSchedulerApplication   точка входа
├── config/      AppConfig (сборка зависимостей), DevDataSeeder
├── task/        задачи: Task, TaskView, TaskRepository, TaskService, контроллеры экранов задач
├── project/     проекты: Project, ProjectView, ProjectRepository, ProjectService, контроллеры
├── user/        сотрудники
├── tag/         теги
├── team/        экран «Команда»
├── dashboard/   дашборд «Мои задачи»
├── shell/       главное окно (MainController)
├── sync/        журнал изменений: ChangeTracker, sync_state
├── session/     текущий пользователь и устройство
└── shared/      общее для всех фич
    ├── database/    пул соединений, UnitOfWork, Jdbc
    ├── event/       EventBus
    ├── navigation/  NavigationManager, Routes
    ├── ui/          общие интерфейсы экранов, форматирование дат
    └── exception/   исключения
```

FXML, стили и шрифты лежат в `src/main/resources/org/example/taskschedulerdesktop/` (`view/`, `styles/`, `fonts/`); пути к FXML собраны в `Routes`.

## Слой данных

- `UnitOfWork` — одна транзакция на операцию; репозитории берут соединение из неё.
- `ChangeTracker` — каждое изменение сущности в той же транзакции пишет `field_versions` и `sync_log` (очередь для синхронизации).
- Модели (`Task`, `Project`, ...) — зеркало таблиц; экраны работают с `TaskView` и `ProjectView`.
- Тесты слоя данных: `mvn test`.

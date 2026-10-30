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

### Сервер и вход

Клиент работает с [сервером TaskHub](https://github.com/UserSayat/task-manager-api). Адрес по умолчанию — `http://localhost:8080`, другой задаётся флагом `-Dtaskhub.server=https://...`.

- Первый вход на устройстве — только онлайн: после входа загружаются все доступные данные (snapshot).
- Дальше вход сохраняется (refresh-токен в локальной базе): приложение открывается и без сети, изменения отправятся, когда сервер станет доступен.
- Если на устройстве входит другой пользователь, локальные данные предыдущего удаляются.

### Синхронизация

Индикатор в шапке показывает состояние: «Синхронизировано», «Не отправлено: N», «Офлайн», «Отклонено сервером: N». Нажатие — синхронизировать сейчас.
Синхронизация запускается при старте, каждые 120 секунд, через 5 секунд после изменения и по кнопке (без сети — раз в 30 секунд).

### Уведомления

Колокольчик в шапке показывает число непрочитанных. Уведомления (назначение задачи, смена статуса или срока, «завтра срок») создаёт сервер; клиент получает последние 50 в конце каждой синхронизации и показывает их и без сети. Отметка «прочитано» сохраняется сразу и уходит на сервер при ближайшей синхронизации.

### Демо-режим

Без сервера, с демо-сотрудниками, текущий пользователь — демо-администратор: `-Dtaskhub.dev=true`. Демо-данные на сервер не попадают и удаляются при первом настоящем входе.

H2 TCP-сервер для просмотра базы из IDE: `-Dtaskhub.h2.tcp=true` (порт 9092, только localhost).

## Структура (package by feature)

Код сгруппирован по фичам, как и на бэкенде: всё о задачах — в `task`, о проектах — в `project` и т. д.
Внутри фичи лежат модель, репозиторий, сервисы и экраны.

```
org.example.taskschedulerdesktop
├── MainLauncher, TaskSchedulerApplication   точка входа
├── config/      AppConfig (сборка зависимостей), DevDataSeeder
├── auth/        вход и выход, токены, экран входа
├── task/        задачи: Task, TaskView, TaskRepository, TaskService, контроллеры экранов задач
├── project/     проекты: Project, ProjectView, ProjectRepository, ProjectService, контроллеры
├── user/        сотрудники
├── tag/         теги
├── team/        экран «Команда»: сотрудники и их нагрузка
├── admin/       экран «Сотрудники» для администратора: создание, роли, блокировка, сброс пароля
├── access/      права текущего пользователя (те же правила, что на сервере): какие кнопки показывать
├── notification/ уведомления: колокольчик, локальная копия, отметки «прочитано» офлайн
├── dashboard/   дашборд «Мои задачи»
├── shell/       главное окно (AppShell — вход или рабочее пространство, MainController)
├── sync/        синхронизация: SyncScheduler (когда), SyncService (в каком порядке), SyncStatus
│   ├── protocol/    сообщения push/pull/snapshot и их доставка на сервер
│   ├── local/       служебные таблицы H2: sync_log, field_versions, sync_errors, sync_state
│   ├── push/        сборка локальных изменений для отправки
│   ├── pull/        запись данных с сервера по LWW
│   └── adapter/     контракт для фич (TaskSyncAdapter и др. лежат в своих пакетах)
├── session/     текущий пользователь и устройство
└── shared/      общее для всех фич
    ├── api/         HTTP-клиент сервера (ApiClient)
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
- Синхронизация: `OutgoingChanges` собирает push из журнала, `ChangeApplier` применяет pull и snapshot по field-level LWW. Каждая фича описывает свои поля в `*SyncAdapter` (`TaskSyncAdapter`, `ProjectSyncAdapter`, ...).
- Тесты: `mvn test` (слой данных и синхронизация с сервером-заглушкой).
- Проверка с настоящим сервером (на чистой базе, нужен администратор):
  `mvn test -Dtest=ServerIntegrationTest -Dtaskhub.e2e.server=http://localhost:8080 -Dtaskhub.e2e.email=... -Dtaskhub.e2e.password=...`

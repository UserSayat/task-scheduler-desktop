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

Экраны сменяются сразу, без анимации; боковая панель выезжает справа. Анимации (панель, появление диалогов, тень карточек при наведении) выключаются флагом `-Dtaskhub.animations=false`.

Окно со своей рамкой в стиле приложения: на macOS кнопки окна слева, на Windows справа. Системная рамка возвращается флагом `-Dtaskhub.nativeFrame=true`; вид кнопок для проверки задаётся `-Dtaskhub.frameStyle=mac` или `windows`.

H2 TCP-сервер для просмотра базы из IDE: `-Dtaskhub.h2.tcp=true` (порт 9092, только localhost).

Локальная база и журналы — в папке пользователя: `~/.taskschedulerdb/data` и `~/.taskschedulerdb/logs`.

## Сборка для Windows

Адрес сервера вшивается в приложение при сборке (`-Dtaskhub.server.url`), сотрудникам ничего настраивать не нужно.

**Установщик `.msi`** — выбор папки и галочки «ярлык на рабочем столе» и «ярлык в меню Пуск» (обе отмечены), удаление через «Приложения»; новая версия ставится поверх старой:

```bash
mvn clean package -Pinstaller -Dtaskhub.server.url=https://203-0-113-10.sslip.io
```

Результат — `target/dist/TaskHub-1.0.0.msi`. Для сборки нужен [WiX Toolset 3.14](https://github.com/wixtoolset/wix3/releases): архив `wix314-binaries.zip`, распакованный в `~/tools/wix314` (другая папка — `-Dwix.dir=...`). Номер версии — свойство `app.version` в `pom.xml`: перед выпуском новой версии его нужно увеличить, иначе установщик не обновит старую.

**Папка с программой** (без установки, WiX не нужен): `mvn clean package -Dtaskhub.server.url=https://...` → `target/dist/TaskHub/TaskHub.exe`.

Java на компьютерах сотрудников не нужна: она входит в сборку.

## Сборка для macOS

```bash
mvn clean package -Pmac-installer -Dtaskhub.server.url=https://203-0-113-10.sslip.io
```

Только на Mac: результат — `target/dist/TaskHub-1.0.0.dmg` для процессора, на котором шла сборка (Apple Silicon или Intel). Значок `.icns` подставляется сам. Приложение не подписано сертификатом Apple, поэтому при первом запуске macOS попросит подтвердить открытие: правый щелчок по TaskHub → «Открыть».

## Сборка в GitHub Actions

Workflow `.github/workflows/installers.yml` собирает `.msi` (Windows) и `.dmg` (macOS, Apple Silicon) на серверах GitHub — свой Mac не нужен.

1. Один раз задать адрес сервера: Settings → Secrets and variables → Actions → Variables → `TASKHUB_SERVER_URL` = `https://203-0-113-10.sslip.io`.
2. Собрать вручную: Actions → «Установщики» → Run workflow (адрес можно указать в поле) — файлы появятся в разделе Artifacts.
3. Выпустить версию: увеличить `app.version` в `pom.xml`, затем `git tag v1.0.1 && git push origin v1.0.1` — установщики прикрепятся к GitHub Release.

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
├── settings/   системные настройки: формат даты, часовой пояс по умолчанию, интервал синхронизации
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

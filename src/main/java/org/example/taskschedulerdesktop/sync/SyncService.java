package org.example.taskschedulerdesktop.sync;

import org.example.taskschedulerdesktop.session.Session;
import org.example.taskschedulerdesktop.shared.api.ApiException;
import org.example.taskschedulerdesktop.shared.api.OfflineException;
import org.example.taskschedulerdesktop.shared.database.Jdbc;
import org.example.taskschedulerdesktop.shared.database.UnitOfWork;
import org.example.taskschedulerdesktop.sync.adapter.KnownUsers;
import org.example.taskschedulerdesktop.sync.adapter.SyncEntityAdapter;
import org.example.taskschedulerdesktop.sync.local.FieldVersionRepository;
import org.example.taskschedulerdesktop.sync.local.LocalReplica;
import org.example.taskschedulerdesktop.sync.local.SyncErrorRepository;
import org.example.taskschedulerdesktop.sync.local.SyncLogRepository;
import org.example.taskschedulerdesktop.sync.local.SyncStateRepository;
import org.example.taskschedulerdesktop.sync.protocol.EntityType;
import org.example.taskschedulerdesktop.sync.protocol.SyncApi;
import org.example.taskschedulerdesktop.sync.protocol.SyncMessages.Change;
import org.example.taskschedulerdesktop.sync.protocol.SyncMessages.PullResponse;
import org.example.taskschedulerdesktop.sync.protocol.SyncMessages.PushResponse;
import org.example.taskschedulerdesktop.sync.protocol.SyncMessages.PushResult;
import org.example.taskschedulerdesktop.sync.protocol.SyncMessages.SnapshotResponse;
import org.example.taskschedulerdesktop.sync.pull.AppliedChanges;
import org.example.taskschedulerdesktop.sync.pull.ChangeApplier;
import org.example.taskschedulerdesktop.sync.push.OutgoingChanges;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * Синхронизация с сервером (API.md, 8.1): сначала push всех локальных изменений, затем pull по курсору.
 * <p>
 * Первый запуск (курсора ещё нет) и устаревший курсор — полный snapshot. Если в pull появился новый проект
 * (пользователя добавили в участники) или задача сослалась на неизвестный проект — snapshot этого проекта.
 * <p>
 * Методы блокирующие и выполняются по одному: их вызывает {@link SyncScheduler} в своём потоке.
 */
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    /** Размер пачки push по умолчанию, пока не загружены системные настройки. */
    private static final int DEFAULT_PUSH_BATCH = 100;
    /** Защита от бесконечного цикла, если сервер почему-то не принимает изменения. */
    private static final int MAX_ROUNDS = 50;

    private final UnitOfWork uow;
    private final SyncApi api;
    private final Session session;
    private final OutgoingChanges outgoing;
    private final ChangeApplier applier;
    private final SyncLogRepository syncLog;
    private final SyncErrorRepository syncErrors;
    private final SyncStateRepository syncState;
    private final FieldVersionRepository fieldVersions;
    private final LocalReplica replica;

    private final List<Consumer<SyncStatus>> statusListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<AppliedChanges>> changeListeners = new CopyOnWriteArrayList<>();
    private final List<SyncStep> steps = new CopyOnWriteArrayList<>();
    private volatile IntSupplier pushBatch = () -> DEFAULT_PUSH_BATCH;
    private volatile SyncStatus status = SyncStatus.idle();

    /**
     * Сборка синхронизации из адаптеров сущностей. {@code knownUsers} — обычно адаптер пользователей.
     */
    public static SyncService create(UnitOfWork uow, SyncApi api, Session session, KnownUsers knownUsers,
                                     List<SyncEntityAdapter> adapters) {
        Map<EntityType, SyncEntityAdapter> byType = new EnumMap<>(EntityType.class);
        adapters.forEach(adapter -> byType.put(adapter.type(), adapter));
        FieldVersionRepository fieldVersions = new FieldVersionRepository(uow);
        OutgoingChanges outgoing = new OutgoingChanges(byType, new SyncLogRepository(uow), fieldVersions, session);
        ChangeApplier applier = new ChangeApplier(uow, byType, fieldVersions, new LocalReplica(uow), knownUsers, session);
        return new SyncService(uow, api, session, outgoing, applier);
    }

    public SyncService(UnitOfWork uow, SyncApi api, Session session, OutgoingChanges outgoing, ChangeApplier applier) {
        this.uow = uow;
        this.api = api;
        this.session = session;
        this.outgoing = outgoing;
        this.applier = applier;
        this.syncLog = new SyncLogRepository(uow);
        this.syncErrors = new SyncErrorRepository(uow);
        this.syncState = new SyncStateRepository(uow);
        this.fieldVersions = new FieldVersionRepository(uow);
        this.replica = new LocalReplica(uow);
    }

    // ===== Подписки для экранов =====

    public void addStatusListener(Consumer<SyncStatus> listener) {
        statusListeners.add(listener);
        listener.accept(status);
    }

    public void removeStatusListener(Consumer<SyncStatus> listener) {
        statusListeners.remove(listener);
    }

    /** Данные с сервера изменили локальную базу. Вызывается в потоке синхронизации. */
    public void addChangeListener(Consumer<AppliedChanges> listener) {
        changeListeners.add(listener);
    }

    public void removeChangeListener(Consumer<AppliedChanges> listener) {
        changeListeners.remove(listener);
    }

    /** Сколько изменений отправлять за один push — из системных настроек ({@code sync.batch_size}). */
    public void setPushBatch(IntSupplier pushBatch) {
        this.pushBatch = pushBatch;
    }

    /** Дополнительный шаг в конце каждой синхронизации (например, уведомления). */
    public void addStep(SyncStep step) {
        steps.add(step);
    }

    public SyncStatus status() {
        return status;
    }

    /** Локально что-то изменилось — индикатор покажет, что есть неотправленное. */
    public void localChangeRecorded() {
        if (status.state() != SyncStatus.State.SYNCING) {
            publish(new SyncStatus(SyncStatus.State.PENDING, status.pending() + 1, status.errors(),
                    status.lastSyncedAt(), null));
        }
    }

    // ===== Синхронизация =====

    /**
     * Полный цикл: push, затем pull. Ошибки не выбрасываются, а отражаются в возвращаемом состоянии.
     */
    public synchronized SyncStatus synchronize() {
        if (!session.isSignedIn()) {
            return status;
        }
        publish(status.with(SyncStatus.State.SYNCING, null));
        AppliedChanges changes = new AppliedChanges();
        SyncStatus.State result;
        String message = null;
        Instant syncedAt = status.lastSyncedAt();

        try {
            push(changes);
            pull(changes);
            for (SyncStep step : steps) {
                step.run();
            }
            result = SyncStatus.State.SYNCED;
            syncedAt = Instant.now();
        } catch (OfflineException e) {
            log.info("Server is unavailable: {}", e.getCause() != null ? e.getCause().toString() : e.getMessage());
            result = SyncStatus.State.OFFLINE;
        } catch (ApiException e) {
            boolean signedOut = e.status() == 401 || e.is("USER_BLOCKED") || e.is("PASSWORD_CHANGE_REQUIRED");
            log.warn("Sync failed: {} {}", e.code(), e.getMessage());
            result = signedOut ? SyncStatus.State.SIGNED_OUT : SyncStatus.State.ERROR;
            message = e.getMessage();
        } catch (RuntimeException e) {
            log.error("Sync failed", e);
            result = SyncStatus.State.ERROR;
            message = e.getMessage();
        }

        if (!changes.isEmpty()) {
            changeListeners.forEach(listener -> listener.accept(changes));
        }
        int[] counts = counts();
        publish(new SyncStatus(result, counts[0], counts[1], syncedAt, message));
        return status;
    }

    /**
     * Полная загрузка данных — первый вход на устройстве (API.md, 8.1). Ошибки выбрасываются:
     * экран входа покажет их пользователю.
     */
    public synchronized void initialLoad() {
        AppliedChanges changes = new AppliedChanges();
        fullSnapshot(changes);
        int[] counts = counts();
        publish(new SyncStatus(SyncStatus.State.SYNCED, counts[0], counts[1], Instant.now(), null));
    }

    // ===== Отклонённые изменения =====

    public List<SyncErrorRepository.Rejected> rejectedChanges() {
        return uow.read(syncErrors::findUnresolved);
    }

    /**
     * «Повторить»: ошибка считается решённой, изменения сущности уйдут при следующей синхронизации
     * (например, после того как администратор выдал права).
     */
    public synchronized void retryRejected(EntityType type, UUID entityId) {
        uow.inTransaction(() -> syncErrors.resolve(type, entityId, Instant.now()));
        refreshCounts();
    }

    /**
     * «Отменить мою правку»: неотправленные изменения сущности удаляются, а её значения
     * загружаются с сервера заново. Сущность, которой на сервере нет (отклонённое создание), удаляется.
     *
     * @throws OfflineException нужна связь, чтобы получить значения с сервера; локально ничего не меняется
     */
    public synchronized void discardRejected(EntityType type, UUID entityId, boolean createdLocally) {
        AppliedChanges changes = new AppliedChanges();
        if (createdLocally) {
            uow.inTransaction(() -> {
                Instant now = Instant.now();
                syncLog.removeUnsynced(type, List.of(entityId));
                syncErrors.resolve(type, entityId, now);
                UUID projectId = replica.removeLocalOnly(type, entityId, now);
                switch (type) {
                    case PROJECT -> changes.projectRevoked(entityId);
                    case TASK -> changes.taskChanged(projectId);
                    case TAG -> changes.tagChanged();
                    case USER -> changes.userChanged();
                }
            });
        } else {
            // Сначала проверяем связь: без сервера откатывать нечем
            UUID projectId = projectFor(type, entityId);
            api.snapshot(projectId, 1);
            uow.inTransaction(() -> {
                syncLog.removeUnsynced(type, List.of(entityId));
                fieldVersions.deleteForEntities(type, List.of(entityId));
                syncErrors.resolve(type, entityId, Instant.now());
            });
            // Без локальных версий полей серверные значения выигрывают LWW
            if (projectId != null) {
                projectSnapshot(projectId, changes);
            } else {
                fullSnapshot(changes);
            }
        }
        if (!changes.isEmpty()) {
            changeListeners.forEach(listener -> listener.accept(changes));
        }
        refreshCounts();
    }

    /** Проект, который нужно загрузить заново, чтобы вернуть серверные значения; null — полный snapshot. */
    private UUID projectFor(EntityType type, UUID entityId) {
        return switch (type) {
            case PROJECT -> entityId;
            case TASK -> uow.read(() -> {
                try (var ps = uow.connection().prepareStatement("SELECT project_id FROM tasks WHERE id = ?")) {
                    Jdbc.setUuid(ps, 1, entityId);
                    try (var rs = ps.executeQuery()) {
                        return rs.next() ? Jdbc.getUuid(rs, "project_id") : null;
                    }
                }
            });
            case TAG, USER -> null;
        };
    }

    private void refreshCounts() {
        int[] counts = counts();
        publish(new SyncStatus(status.state() == SyncStatus.State.SYNCING ? SyncStatus.State.SYNCED : status.state(),
                counts[0], counts[1], status.lastSyncedAt(), status.message()));
    }

    // ===== push =====

    private void push(AppliedChanges changes) {
        for (int round = 0; round < MAX_ROUNDS; round++) {
            List<Change> batch = uow.inTransaction(() -> outgoing.nextBatch(pushBatch.getAsInt()));
            if (batch.isEmpty()) {
                return;
            }

            PushResponse response = api.push(batch);
            uow.inTransaction(() -> {
                Instant now = Instant.now();
                for (int i = 0; i < batch.size(); i++) {
                    Change change = batch.get(i);
                    PushResult result = response.results().get(i);
                    if (result.applied()) {
                        syncLog.markSynced(change.syncLogIds(), response.serverTime());
                        changes.addAll(applier.applyStale(change.entityType(), change.entityId(), result.staleFields()));
                    } else {
                        String code = result.error() != null ? result.error().code() : "UNKNOWN";
                        String text = result.error() != null ? result.error().message() : null;
                        log.warn("{} {} rejected by server: {} {}", change.entityType(), change.entityId(), code, text);
                        syncErrors.insert(change.entityType(), change.entityId(), change.operation(), code, text, now);
                    }
                }
            });
            log.info("Pushed {} changes", batch.size());
        }
        log.warn("Push stopped after {} rounds", MAX_ROUNDS);
    }

    // ===== pull =====

    private void pull(AppliedChanges changes) {
        Optional<String> saved = uow.read(() -> syncState.get(SyncStateRepository.PULL_CURSOR));
        if (saved.isEmpty()) {
            fullSnapshot(changes);
            saved = uow.read(() -> syncState.get(SyncStateRepository.PULL_CURSOR));
        }

        String cursor = saved.orElseThrow();
        Set<UUID> projectsToLoad = new LinkedHashSet<>();
        for (int round = 0; round < MAX_ROUNDS; round++) {
            PullResponse page;
            try {
                page = api.pull(cursor);
            } catch (ApiException e) {
                if (e.status() != 422) {
                    throw e;
                }
                // Курсор старше журнала на сервере (API.md, 8.4) — загружаем всё заново
                log.info("Pull cursor is outdated, loading full snapshot");
                fullSnapshot(changes);
                return;
            }

            String next = page.cursor();
            AppliedChanges applied = uow.inTransaction(() -> {
                AppliedChanges result = applier.apply(page.changes());
                syncState.set(SyncStateRepository.PULL_CURSOR, next);
                return result;
            });
            changes.addAll(applied);
            projectsToLoad.addAll(applied.newProjects());
            projectsToLoad.addAll(applied.missingProjects());
            cursor = next;
            if (!page.hasMore()) {
                break;
            }
        }

        projectsToLoad.removeAll(changes.revokedProjects());
        for (UUID projectId : projectsToLoad) {
            projectSnapshot(projectId, changes);
        }
    }

    // ===== snapshot =====

    /** Все доступные данные; курсор сохраняется из первой страницы (API.md, 8.5). */
    private void fullSnapshot(AppliedChanges changes) {
        String cursor = null;
        int page = 1;
        boolean hasMore = true;
        Map<EntityType, Set<UUID>> present = new EnumMap<>(EntityType.class);
        while (hasMore) {
            SnapshotResponse response = api.snapshot(null, page);
            if (cursor == null) {
                cursor = response.cursor();
            }
            response.changes().forEach(entity ->
                    present.computeIfAbsent(entity.entityType(), type -> new HashSet<>()).add(entity.entityId()));
            changes.addAll(uow.inTransaction(() -> applier.apply(response.changes())));
            hasMore = response.hasMore();
            page++;
        }
        String firstCursor = cursor;
        uow.inTransaction(() -> {
            // Snapshot — полная картина: всё, чего в нём нет, на сервере удалено или недоступно
            replica.removeMissing(present, Instant.now()).forEach(changes::projectRevoked);
            syncState.set(SyncStateRepository.PULL_CURSOR, firstCursor);
        });
        log.info("Snapshot loaded: {} pages", page - 1);
    }

    /** Проект и его задачи — курсор не меняется. */
    private void projectSnapshot(UUID projectId, AppliedChanges changes) {
        int page = 1;
        boolean hasMore = true;
        while (hasMore) {
            SnapshotResponse response = api.snapshot(projectId, page);
            changes.addAll(uow.inTransaction(() -> applier.apply(response.changes())));
            hasMore = response.hasMore();
            page++;
        }
        log.info("Project {} loaded", projectId);
    }

    // ===== Вспомогательное =====

    private int[] counts() {
        return uow.read(() -> new int[]{syncLog.countUnsynced(), syncErrors.countUnresolved()});
    }

    private void publish(SyncStatus next) {
        status = next;
        statusListeners.forEach(listener -> listener.accept(next));
    }
}

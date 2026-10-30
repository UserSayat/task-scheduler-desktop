package org.example.taskschedulerdesktop.settings;

import com.fasterxml.jackson.core.type.TypeReference;
import org.example.taskschedulerdesktop.shared.api.ApiClient;
import org.example.taskschedulerdesktop.shared.api.ApiException;
import org.example.taskschedulerdesktop.shared.database.UnitOfWork;
import org.example.taskschedulerdesktop.sync.SyncStep;

import java.util.Map;

/**
 * Системные настройки: загрузка с сервера в конце каждой синхронизации ({@link SyncStep})
 * и изменение администратором (только онлайн, {@code PUT /settings}).
 */
public class SettingsService implements SyncStep {

    private final ApiClient api;
    private final UnitOfWork uow;
    private final SettingsRepository repository;
    private final AppSettings settings;
    private final Runnable onChanged;

    /**
     * @param onChanged настройки изменились — экраны могут перерисовать даты (вызывается в фоновом потоке)
     */
    public SettingsService(ApiClient api, UnitOfWork uow, SettingsRepository repository, AppSettings settings,
                           Runnable onChanged) {
        this.api = api;
        this.uow = uow;
        this.repository = repository;
        this.settings = settings;
        this.onChanged = onChanged;
    }

    /** При запуске: последние известные настройки из локальной базы — работают и офлайн. */
    public void loadLocal() {
        settings.update(uow.read(repository::findAll));
    }

    /** Шаг синхронизации: свежие настройки с сервера. */
    @Override
    public void run() {
        apply(api.get("/settings", new TypeReference<Map<String, String>>() {}));
    }

    /**
     * Изменение настроек администратором.
     *
     * @throws IllegalArgumentException сервер не принял значение (текст ошибки — для пользователя)
     */
    public Map<String, String> save(Map<String, String> changes) {
        try {
            Map<String, String> saved = api.put("/settings", changes, new TypeReference<Map<String, String>>() {});
            apply(saved);
            return saved;
        } catch (ApiException e) {
            if (e.fields() != null && !e.fields().isEmpty()) {
                throw new IllegalArgumentException(String.join("\n",
                        e.fields().values().stream().flatMap(java.util.List::stream).toList()), e);
            }
            throw e;
        }
    }

    public Map<String, String> current() {
        return settings.all();
    }

    private void apply(Map<String, String> fresh) {
        if (fresh == null || fresh.isEmpty() || fresh.equals(settings.all())) {
            return;
        }
        uow.inTransaction(() -> repository.saveAll(fresh));
        settings.update(fresh);
        onChanged.run();
    }
}

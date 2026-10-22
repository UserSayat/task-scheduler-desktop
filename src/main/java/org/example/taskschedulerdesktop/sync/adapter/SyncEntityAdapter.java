package org.example.taskschedulerdesktop.sync.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.taskschedulerdesktop.sync.protocol.EntityType;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Синхронизация одного типа сущностей на клиенте: как прочитать поля для push и как записать
 * пришедшие с сервера. Каждая фича реализует его у себя в пакете — так же, как SyncEntityHandler на сервере.
 * Методы вызываются внутри транзакции {@link org.example.taskschedulerdesktop.shared.database.UnitOfWork}.
 */
public interface SyncEntityAdapter {

    EntityType type();

    boolean exists(UUID id) throws SQLException;

    /**
     * Текущие значения всех синхронизируемых полей (имена — как в API.md, 8.2). Пусто, если сущности нет.
     */
    Optional<Map<String, JsonNode>> values(UUID id) throws SQLException;

    /**
     * Записать поля, выигравшие LWW. Если сущности ещё нет, в {@code fields} — полный набор полей.
     *
     * @param at метка времени изменения — для служебных колонок created_at / updated_at
     * @throws MissingReferenceException сущность ссылается на проект, которого нет локально
     */
    void apply(UUID id, Map<String, JsonNode> fields, Instant at) throws SQLException;
}

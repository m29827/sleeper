/*
 * Copyright 2022-2024 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sleeper.core.table;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

public class InMemoryTableIndex implements TableIndex {

    private final Map<String, TableIdentity> indexByName = new TreeMap<>();
    private final Map<String, TableIdentity> indexById = new HashMap<>();
    private final Set<TableIdentity> onlineTables = new HashSet<>();

    @Override
    public void create(TableIdentity tableId) throws TableAlreadyExistsException {
        if (indexByName.containsKey(tableId.getTableName())) {
            throw new TableAlreadyExistsException(tableId);
        }
        save(tableId);
    }

    public void save(TableIdentity id) {
        indexByName.put(id.getTableName(), id);
        indexById.put(id.getTableUniqueId(), id);
        onlineTables.add(id);
    }

    @Override
    public Stream<TableIdentity> streamAllTables() {
        return new ArrayList<>(indexByName.values()).stream();
    }

    @Override
    public Stream<TableIdentity> streamOnlineTables() {
        return onlineTables.stream();
    }

    @Override
    public Optional<TableIdentity> getTableByName(String tableName) {
        return Optional.ofNullable(indexByName.get(tableName));
    }

    @Override
    public Optional<TableIdentity> getTableByUniqueId(String tableUniqueId) {
        return Optional.ofNullable(indexById.get(tableUniqueId));
    }

    @Override
    public void delete(TableIdentity tableId) {
        if (!indexById.containsKey(tableId.getTableUniqueId())) {
            throw TableNotFoundException.withTableId(tableId.getTableUniqueId());
        }
        TableIdentity latestId = indexById.get(tableId.getTableUniqueId());
        if (!Objects.equals(latestId.getTableName(), tableId.getTableName())) {
            throw TableNotFoundException.withTableName(tableId.getTableName());
        }
        indexByName.remove(latestId.getTableName());
        indexById.remove(latestId.getTableUniqueId());
        onlineTables.remove(tableId);
    }

    @Override
    public void update(TableIdentity tableId) {
        TableIdentity existingTableWithNewName = indexByName.get(tableId.getTableName());
        if (existingTableWithNewName != null && !existingTableWithNewName.getTableUniqueId().equals(tableId.getTableUniqueId())) {
            throw new TableAlreadyExistsException(existingTableWithNewName);
        }
        if (!indexById.containsKey(tableId.getTableUniqueId())) {
            throw TableNotFoundException.withTableId(tableId.getTableUniqueId());
        }
        TableIdentity oldId = indexById.get(tableId.getTableUniqueId());
        indexByName.remove(oldId.getTableName());
        indexByName.put(tableId.getTableName(), tableId);
        indexById.put(tableId.getTableUniqueId(), tableId);
    }

    @Override
    public void takeOffline(TableIdentity tableId) {
        if (!onlineTables.remove(tableId)) {
            if (indexById.containsKey(tableId.getTableUniqueId())) {
                throw new TableAlreadyOfflineException(tableId);
            } else {
                throw TableNotFoundException.withTableIdentity(tableId);
            }
        }
    }

    @Override
    public void putOnline(TableIdentity tableId) {
        if (!indexById.containsKey(tableId.getTableUniqueId())) {
            throw TableNotFoundException.withTableIdentity(tableId);
        }
        if (onlineTables.contains(tableId)) {
            throw new TableAlreadyOnlineException(tableId);
        }
        onlineTables.add(tableId);
    }
}

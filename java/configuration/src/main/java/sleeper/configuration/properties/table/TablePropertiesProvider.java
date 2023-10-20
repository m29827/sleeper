/*
 * Copyright 2022-2023 Crown Copyright
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
package sleeper.configuration.properties.table;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.s3.AmazonS3;

import sleeper.configuration.properties.instance.InstanceProperties;
import sleeper.core.table.TableId;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static sleeper.configuration.properties.instance.CommonProperty.TABLE_PROPERTIES_PROVIDER_TIMEOUT_IN_MINS;
import static sleeper.configuration.properties.table.TableProperty.TABLE_ID;
import static sleeper.configuration.properties.table.TableProperty.TABLE_NAME;

public class TablePropertiesProvider {
    protected final TablePropertiesStore propertiesStore;
    private final int timeoutInMins;
    private final Supplier<Instant> timeSupplier;
    private final Map<String, CacheEntry> cacheById = new HashMap<>();
    private final Map<String, CacheEntry> cacheByName = new HashMap<>();

    public TablePropertiesProvider(InstanceProperties instanceProperties, AmazonS3 s3Client, AmazonDynamoDB dynamoDBClient) {
        this(instanceProperties, s3Client, dynamoDBClient, Instant::now);
    }

    protected TablePropertiesProvider(InstanceProperties instanceProperties, AmazonS3 s3Client, AmazonDynamoDB dynamoDBClient, Supplier<Instant> timeSupplier) {
        this(instanceProperties, S3TableProperties.getStore(instanceProperties, s3Client, dynamoDBClient), timeSupplier);
    }

    public TablePropertiesProvider(InstanceProperties instanceProperties, TablePropertiesStore propertiesStore, Supplier<Instant> timeSupplier) {
        this(propertiesStore, instanceProperties.getInt(TABLE_PROPERTIES_PROVIDER_TIMEOUT_IN_MINS), timeSupplier);
    }

    protected TablePropertiesProvider(TablePropertiesStore propertiesStore,
                                      int timeoutInMins, Supplier<Instant> timeSupplier) {
        this.propertiesStore = propertiesStore;
        this.timeoutInMins = timeoutInMins;
        this.timeSupplier = timeSupplier;
    }

    public TableProperties getByName(String tableName) {
        return get(tableName, cacheByName, propertiesStore::loadByName);
    }

    public TableProperties getById(String tableId) {
        return get(tableId, cacheById, propertiesStore::loadById);
    }

    private TableProperties get(String identifier,
                                Map<String, CacheEntry> cache,
                                Function<String, Optional<TableProperties>> loadProperties) {
        Instant currentTime = timeSupplier.get();
        CacheEntry currentEntry = cache.get(identifier);
        TableProperties properties;
        if (currentEntry == null || currentEntry.isExpired(currentTime)) {
            properties = loadProperties.apply(identifier).orElseThrow();
        } else {
            properties = currentEntry.getTableProperties();
        }
        CacheEntry entry = new CacheEntry(properties, currentTime.plus(Duration.ofMinutes(timeoutInMins)));
        cacheById.put(properties.get(TABLE_ID), entry);
        cacheByName.put(properties.get(TABLE_NAME), entry);
        return properties;
    }

    public TableProperties get(TableId tableId) {
        return getById(tableId.getTableUniqueId());
    }

    public Optional<TableId> lookupByName(String tableName) {
        return propertiesStore.lookupByName(tableName);
    }

    public Stream<TableId> streamAllTableIds() {
        return propertiesStore.streamAllTableIds();
    }

    public Stream<TableProperties> streamAllTables() {
        return propertiesStore.streamAllTableIds()
                .map(id -> getByName(id.getTableName()));
    }

    public List<String> listTableNames() {
        return propertiesStore.listTableNames();
    }

    public List<TableId> listTableIds() {
        return propertiesStore.listTableIds();
    }

    public void clearCache() {
        cacheByName.clear();
        cacheById.clear();
    }

    private static class CacheEntry {
        private final TableProperties tableProperties;
        private final Instant expiryTime;

        public CacheEntry(TableProperties tableProperties, Instant expiryTime) {
            this.tableProperties = tableProperties;
            this.expiryTime = expiryTime;
        }

        public TableProperties getTableProperties() {
            return tableProperties;
        }

        public boolean isExpired(Instant currentTime) {
            return currentTime.isAfter(expiryTime);
        }
    }
}

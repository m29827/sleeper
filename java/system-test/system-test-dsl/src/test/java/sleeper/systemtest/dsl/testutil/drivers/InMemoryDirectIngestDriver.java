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

package sleeper.systemtest.dsl.testutil.drivers;

import sleeper.configuration.jars.ObjectFactory;
import sleeper.configuration.properties.instance.InstanceProperties;
import sleeper.configuration.properties.table.TableProperties;
import sleeper.core.iterator.IteratorException;
import sleeper.core.record.Record;
import sleeper.core.statestore.StateStoreException;
import sleeper.ingest.IngestRecordsFromIterator;
import sleeper.ingest.IngestResult;
import sleeper.ingest.impl.IngestCoordinator;
import sleeper.query.runner.recordretrieval.InMemoryDataStore;
import sleeper.systemtest.dsl.ingest.DirectIngestDriver;
import sleeper.systemtest.dsl.instance.SystemTestInstanceContext;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;

public class InMemoryDirectIngestDriver implements DirectIngestDriver {
    private final SystemTestInstanceContext instance;
    private final InMemoryDataStore data;

    public InMemoryDirectIngestDriver(SystemTestInstanceContext instance, InMemoryDataStore data) {
        this.instance = instance;
        this.data = data;
    }

    @Override
    public void ingest(Path tempDir, Iterator<Record> records) {
        ingest(records);
    }

    public IngestResult ingest(Iterator<Record> records) {
        InstanceProperties instanceProperties = instance.getInstanceProperties();
        TableProperties tableProperties = instance.getTableProperties();
        try (IngestCoordinator<Record> coordinator = IngestCoordinator.builderWith(instanceProperties, tableProperties)
                .objectFactory(ObjectFactory.noUserJars())
                .recordBatchFactory(InMemoryRecordBatch::new)
                .partitionFileWriterFactory(InMemoryPartitionFileWriter.factory(data, instanceProperties, tableProperties))
                .stateStore(instance.getStateStore())
                .build()) {
            return new IngestRecordsFromIterator(coordinator, records).write();
        } catch (StateStoreException | IteratorException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}
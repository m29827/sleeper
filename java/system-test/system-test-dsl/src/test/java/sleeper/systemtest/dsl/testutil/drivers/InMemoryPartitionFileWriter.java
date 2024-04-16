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

import org.apache.datasketches.quantiles.ItemsSketch;

import sleeper.configuration.properties.instance.InstanceProperties;
import sleeper.configuration.properties.table.TableProperties;
import sleeper.core.partition.Partition;
import sleeper.core.record.Record;
import sleeper.core.schema.Schema;
import sleeper.core.statestore.FileReference;
import sleeper.ingest.impl.partitionfilewriter.PartitionFileWriter;
import sleeper.ingest.impl.partitionfilewriter.PartitionFileWriterFactory;
import sleeper.ingest.impl.partitionfilewriter.PartitionFileWriterUtils;
import sleeper.query.runner.recordretrieval.InMemoryDataStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static sleeper.configuration.properties.instance.CdkDefinedInstanceProperty.DATA_BUCKET;
import static sleeper.configuration.properties.instance.CommonProperty.FILE_SYSTEM;
import static sleeper.configuration.properties.table.TableProperty.TABLE_ID;

public class InMemoryPartitionFileWriter implements PartitionFileWriter {

    private final InMemoryDataStore data;
    private final InMemorySketchesStore sketches;
    private final Partition partition;
    private final String filename;
    private final List<Record> records = new ArrayList<>();
    private final Map<String, ItemsSketch> keyFieldToSketchMap;
    private final Schema schema;

    private InMemoryPartitionFileWriter(InMemoryDataStore data, InMemorySketchesStore sketches, Partition partition, String filename, Schema schema) {
        this.data = data;
        this.sketches = sketches;
        this.partition = partition;
        this.filename = filename;
        this.schema = schema;
        this.keyFieldToSketchMap = PartitionFileWriterUtils.createQuantileSketchMap(schema);
    }

    public static PartitionFileWriterFactory factory(
            InMemoryDataStore data, InMemorySketchesStore sketches, InstanceProperties instanceProperties, TableProperties tableProperties) {
        String filePathPrefix = instanceProperties.get(FILE_SYSTEM)
                + instanceProperties.get(DATA_BUCKET) + "/"
                + tableProperties.get(TABLE_ID);
        return partition -> new InMemoryPartitionFileWriter(
                data, sketches, partition, filePathPrefix + "/" + UUID.randomUUID() + ".parquet", tableProperties.getSchema());
    }

    @Override
    public void append(Record record) {
        records.add(record);
        PartitionFileWriterUtils.updateQuantileSketchMap(
                schema,
                keyFieldToSketchMap,
                record);
    }

    @Override
    public CompletableFuture<FileReference> close() {
        data.addFile(filename, records);
        sketches.addSketchForFile(filename, keyFieldToSketchMap);
        return CompletableFuture.completedFuture(FileReference.builder()
                .filename(filename)
                .partitionId(partition.getId())
                .numberOfRecords((long) records.size())
                .countApproximate(false)
                .onlyContainsDataForThisPartition(true)
                .build());
    }

    @Override
    public void abort() {

    }
}

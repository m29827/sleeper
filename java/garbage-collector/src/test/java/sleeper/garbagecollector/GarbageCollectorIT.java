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
package sleeper.garbagecollector;

import org.apache.hadoop.conf.Configuration;
import org.apache.parquet.hadoop.ParquetWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import sleeper.configuration.properties.instance.InstanceProperties;
import sleeper.configuration.properties.table.FixedTablePropertiesProvider;
import sleeper.configuration.properties.table.TableProperties;
import sleeper.core.partition.PartitionTree;
import sleeper.core.partition.PartitionsBuilder;
import sleeper.core.record.Record;
import sleeper.core.schema.Field;
import sleeper.core.schema.Schema;
import sleeper.core.schema.type.IntType;
import sleeper.core.schema.type.StringType;
import sleeper.core.statestore.FileReference;
import sleeper.core.statestore.FileReferenceFactory;
import sleeper.core.statestore.StateStore;
import sleeper.core.table.InvokeForTableRequest;
import sleeper.io.parquet.record.ParquetRecordWriterFactory;
import sleeper.statestore.FixedStateStoreProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toUnmodifiableList;
import static org.assertj.core.api.Assertions.assertThat;
import static sleeper.configuration.properties.InstancePropertiesTestHelper.createTestInstanceProperties;
import static sleeper.configuration.properties.instance.CdkDefinedInstanceProperty.DATA_BUCKET;
import static sleeper.configuration.properties.instance.CommonProperty.FILE_SYSTEM;
import static sleeper.configuration.properties.instance.GarbageCollectionProperty.GARBAGE_COLLECTOR_BATCH_SIZE;
import static sleeper.configuration.properties.table.TablePropertiesTestHelper.createTestTableProperties;
import static sleeper.configuration.properties.table.TableProperty.GARBAGE_COLLECTOR_DELAY_BEFORE_DELETION;
import static sleeper.configuration.properties.table.TableProperty.TABLE_ID;
import static sleeper.configuration.properties.table.TableProperty.TABLE_NAME;
import static sleeper.core.statestore.AllReferencesToAFileTestHelper.fileWithNoReferences;
import static sleeper.core.statestore.AssignJobIdRequest.assignJobOnPartitionToFiles;
import static sleeper.core.statestore.FilesReportTestHelper.activeAndReadyForGCFilesReport;
import static sleeper.core.statestore.FilesReportTestHelper.activeFilesReport;
import static sleeper.core.statestore.inmemory.StateStoreTestHelper.inMemoryStateStoreWithFixedPartitions;

public class GarbageCollectorIT {
    private static final Schema TEST_SCHEMA = getSchema();

    @TempDir
    public Path tempDir;
    private final PartitionTree partitions = new PartitionsBuilder(TEST_SCHEMA).singlePartition("root").buildTree();
    private final List<TableProperties> tables = new ArrayList<>();
    private final Map<String, StateStore> stateStoreByTableName = new HashMap<>();
    private final InstanceProperties instanceProperties = createTestInstanceProperties();

    @BeforeEach
    void setUp() throws Exception {
        instanceProperties.set(FILE_SYSTEM, "file://");
        instanceProperties.set(DATA_BUCKET, tempDir.toString());
    }

    @Nested
    @DisplayName("Collecting from single table")
    class SingleTable {
        private final TableProperties table = createTable();
        private final StateStore stateStore = stateStore(table);

        @Test
        void shouldCollectFileWithNoReferencesAfterSpecifiedDelay() throws Exception {
            // Given
            Instant currentTime = Instant.parse("2023-06-28T13:46:00Z");
            Instant oldEnoughTime = currentTime.minus(Duration.ofMinutes(11));
            stateStore.fixTime(oldEnoughTime);
            table.setNumber(GARBAGE_COLLECTOR_DELAY_BEFORE_DELETION, 10);
            Path oldFile = tempDir.resolve("old-file.parquet");
            Path newFile = tempDir.resolve("new-file.parquet");
            createFileWithNoReferencesByCompaction(stateStore, oldFile, newFile);

            // When
            collectGarbageAtTime(currentTime);

            // Then
            assertThat(Files.exists(oldFile)).isFalse();
            assertThat(stateStore.getAllFilesWithMaxUnreferenced(10))
                    .isEqualTo(activeFilesReport(oldEnoughTime, activeReference(newFile)));
        }

        @Test
        void shouldNotCollectFileMarkedAsActive() throws Exception {
            // Given
            Instant currentTime = Instant.parse("2023-06-28T13:46:00Z");
            Instant oldEnoughTime = currentTime.minus(Duration.ofMinutes(11));
            stateStore.fixTime(oldEnoughTime);
            table.setNumber(GARBAGE_COLLECTOR_DELAY_BEFORE_DELETION, 10);
            Path filePath = tempDir.resolve("test-file.parquet");
            createActiveFile(filePath, stateStore);

            // When
            collectGarbageAtTime(currentTime);

            // Then
            assertThat(Files.exists(filePath)).isTrue();
            assertThat(stateStore.getAllFilesWithMaxUnreferenced(10))
                    .isEqualTo(activeFilesReport(oldEnoughTime, activeReference(filePath)));
        }

        @Test
        void shouldNotCollectFileWithNoReferencesBeforeSpecifiedDelay() throws Exception {
            // Given
            Instant currentTime = Instant.parse("2023-06-28T13:46:00Z");
            Instant notOldEnoughTime = currentTime.minus(Duration.ofMinutes(5));
            stateStore.fixTime(notOldEnoughTime);
            table.setNumber(GARBAGE_COLLECTOR_DELAY_BEFORE_DELETION, 10);
            Path oldFile = tempDir.resolve("old-file.parquet");
            Path newFile = tempDir.resolve("new-file.parquet");
            createFileWithNoReferencesByCompaction(stateStore, oldFile, newFile);

            // When
            collectGarbageAtTime(currentTime);

            // Then
            assertThat(Files.exists(oldFile)).isTrue();
            assertThat(stateStore.getAllFilesWithMaxUnreferenced(10)).isEqualTo(
                    activeAndReadyForGCFilesReport(notOldEnoughTime,
                            List.of(activeReference(newFile)),
                            List.of(oldFile.toString())));
        }

        @Test
        void shouldCollectMultipleFilesInOneRun() throws Exception {
            // Given
            Instant currentTime = Instant.parse("2023-06-28T13:46:00Z");
            Instant oldEnoughTime = currentTime.minus(Duration.ofMinutes(11));
            stateStore.fixTime(oldEnoughTime);
            table.setNumber(GARBAGE_COLLECTOR_DELAY_BEFORE_DELETION, 10);
            Path oldFile1 = tempDir.resolve("old-file-1.parquet");
            Path oldFile2 = tempDir.resolve("old-file-2.parquet");
            Path newFile1 = tempDir.resolve("new-file-1.parquet");
            Path newFile2 = tempDir.resolve("new-file-2.parquet");
            createFileWithNoReferencesByCompaction(stateStore, oldFile1, newFile1);
            createFileWithNoReferencesByCompaction(stateStore, oldFile2, newFile2);

            // When
            collectGarbageAtTime(currentTime);

            // Then
            assertThat(Files.exists(oldFile1)).isFalse();
            assertThat(Files.exists(oldFile2)).isFalse();
            assertThat(Files.exists(newFile1)).isTrue();
            assertThat(Files.exists(newFile2)).isTrue();
            assertThat(stateStore.getAllFilesWithMaxUnreferenced(10))
                    .isEqualTo(activeFilesReport(oldEnoughTime,
                            activeReference(newFile1),
                            activeReference(newFile2)));
        }

        @Test
        void shouldCollectFilesInBatchesIfBatchSizeExceeded() throws Exception {
            // Given
            instanceProperties.setNumber(GARBAGE_COLLECTOR_BATCH_SIZE, 2);
            Instant currentTime = Instant.parse("2023-06-28T13:46:00Z");
            Instant oldEnoughTime = currentTime.minus(Duration.ofMinutes(11));
            stateStore.fixTime(oldEnoughTime);
            table.setNumber(GARBAGE_COLLECTOR_DELAY_BEFORE_DELETION, 10);
            Path oldFile1 = tempDir.resolve("old-file-1.parquet");
            Path oldFile2 = tempDir.resolve("old-file-2.parquet");
            Path newFile1 = tempDir.resolve("new-file-1.parquet");
            Path newFile2 = tempDir.resolve("new-file-2.parquet");
            Path oldFile3 = tempDir.resolve("old-file-3.parquet");
            Path newFile3 = tempDir.resolve("new-file-3.parquet");
            createFileWithNoReferencesByCompaction(stateStore, oldFile1, newFile1);
            createFileWithNoReferencesByCompaction(stateStore, oldFile2, newFile2);
            createFileWithNoReferencesByCompaction(stateStore, oldFile3, newFile3);

            // When
            collectGarbageAtTime(currentTime);

            // Then
            assertThat(Files.exists(oldFile1)).isFalse();
            assertThat(Files.exists(oldFile2)).isFalse();
            assertThat(Files.exists(oldFile3)).isFalse();
            assertThat(Files.exists(newFile1)).isTrue();
            assertThat(Files.exists(newFile2)).isTrue();
            assertThat(Files.exists(newFile3)).isTrue();
            assertThat(stateStore.getAllFilesWithMaxUnreferenced(10)).isEqualTo(
                    activeFilesReport(oldEnoughTime,
                            activeReference(newFile1),
                            activeReference(newFile2),
                            activeReference(newFile3)));
        }

        @Test
        void shouldContinueCollectingFilesIfFileDoesNotExist() throws Exception {
            // Given
            Instant currentTime = Instant.parse("2023-06-28T13:46:00Z");
            Instant oldEnoughTime = currentTime.minus(Duration.ofMinutes(11));
            stateStore.fixTime(oldEnoughTime);
            table.setNumber(GARBAGE_COLLECTOR_DELAY_BEFORE_DELETION, 10);
            stateStore.addFilesWithReferences(List.of(
                    fileWithNoReferences("/tmp/not-a-file.parquet")));
            Path oldFile2 = tempDir.resolve("old-file-2.parquet");
            Path newFile2 = tempDir.resolve("new-file-2.parquet");
            createFileWithNoReferencesByCompaction(stateStore, oldFile2, newFile2);

            // When
            collectGarbageAtTime(currentTime);

            // Then
            assertThat(Files.exists(oldFile2)).isFalse();
            assertThat(Files.exists(newFile2)).isTrue();
            assertThat(stateStore.getAllFilesWithMaxUnreferenced(10))
                    .isEqualTo(activeFilesReport(oldEnoughTime,
                            activeReference(newFile2)));
        }
    }

    @Nested
    @DisplayName("Collecting from multiple tables")
    class MultipleTables {

        @Test
        void shouldCollectOneFileFromEachTable() throws Exception {
            // Given
            instanceProperties.setNumber(GARBAGE_COLLECTOR_BATCH_SIZE, 2);
            TableProperties table1 = createTable();
            TableProperties table2 = createTable();
            table1.setNumber(GARBAGE_COLLECTOR_DELAY_BEFORE_DELETION, 10);
            table2.setNumber(GARBAGE_COLLECTOR_DELAY_BEFORE_DELETION, 10);
            Instant currentTime = Instant.parse("2023-06-28T13:46:00Z");
            Instant oldEnoughTime = currentTime.minus(Duration.ofMinutes(11));
            StateStore stateStore1 = stateStore(table1);
            StateStore stateStore2 = stateStore(table2);
            stateStore1.fixTime(oldEnoughTime);
            stateStore2.fixTime(oldEnoughTime);
            Path oldFile1 = tempDir.resolve("old-file-1.parquet");
            Path oldFile2 = tempDir.resolve("old-file-2.parquet");
            Path newFile1 = tempDir.resolve("new-file-1.parquet");
            Path newFile2 = tempDir.resolve("new-file-2.parquet");
            createFileWithNoReferencesByCompaction(stateStore1, oldFile1, newFile1);
            createFileWithNoReferencesByCompaction(stateStore2, oldFile2, newFile2);

            // When
            collectGarbageAtTime(currentTime);

            // Then
            assertThat(Files.exists(oldFile1)).isFalse();
            assertThat(Files.exists(oldFile2)).isFalse();
            assertThat(stateStore1.getAllFilesWithMaxUnreferenced(10)).isEqualTo(
                    activeFilesReport(oldEnoughTime, activeReference(newFile1)));
            assertThat(stateStore2.getAllFilesWithMaxUnreferenced(10)).isEqualTo(
                    activeFilesReport(oldEnoughTime, activeReference(newFile2)));
        }
    }

    private FileReference createActiveFile(Path filePath, StateStore stateStore) throws Exception {
        String filename = filePath.toString();
        FileReference fileReference = FileReferenceFactory.from(partitions).rootFile(filename, 100L);
        writeFile(filename);
        stateStore.addFile(fileReference);
        return fileReference;
    }

    private void createFileWithNoReferencesByCompaction(StateStore stateStore,
            Path oldFilePath, Path newFilePath) throws Exception {
        FileReference oldFile = createActiveFile(oldFilePath, stateStore);
        writeFile(newFilePath.toString());
        stateStore.assignJobIds(List.of(
                assignJobOnPartitionToFiles("job1", "root", List.of(oldFile.getFilename()))));
        stateStore.atomicallyReplaceFileReferencesWithNewOne("job1", "root", List.of(oldFile.getFilename()),
                FileReferenceFactory.from(partitions).rootFile(newFilePath.toString(), 100));
    }

    private FileReference activeReference(Path filePath) {
        return FileReferenceFactory.from(partitions).rootFile(filePath.toString(), 100);
    }

    private void writeFile(String filename) throws Exception {
        ParquetWriter<Record> writer = ParquetRecordWriterFactory.createParquetRecordWriter(
                new org.apache.hadoop.fs.Path(filename), TEST_SCHEMA);
        for (int i = 0; i < 100; i++) {
            Record record = new Record();
            record.put("key", i);
            record.put("value", "" + i);
            writer.write(record);
        }
        writer.close();
    }

    private TableProperties createTable() {
        TableProperties tableProperties = createTestTableProperties(instanceProperties, TEST_SCHEMA);
        tables.add(tableProperties);
        StateStore stateStore = inMemoryStateStoreWithFixedPartitions(partitions.getAllPartitions());
        stateStoreByTableName.put(tableProperties.get(TABLE_NAME), stateStore);
        return tableProperties;
    }

    private StateStore stateStore(TableProperties table) {
        return stateStoreByTableName.get(table.get(TABLE_NAME));
    }

    private void collectGarbageAtTime(Instant time) throws Exception {
        GarbageCollector collector = new GarbageCollector(new Configuration(), instanceProperties,
                new FixedTablePropertiesProvider(tables),
                new FixedStateStoreProvider(stateStoreByTableName));
        List<String> tableIds = tables.stream()
                .map(table -> table.get(TABLE_ID))
                .collect(toUnmodifiableList());
        collector.runAtTime(time, new InvokeForTableRequest(tableIds));
    }

    private static Schema getSchema() {
        return Schema.builder()
                .rowKeyFields(new Field("key", new IntType()))
                .valueFields(new Field("value", new StringType()))
                .build();
    }
}

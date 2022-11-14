/*
 * Copyright 2022 Crown Copyright
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
package sleeper.bulkimport.job.runner.dataframe;

import com.facebook.collections.ByteArray;
import org.apache.datasketches.quantiles.ItemsSketch;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sleeper.configuration.properties.InstanceProperties;
import sleeper.configuration.properties.UserDefinedInstanceProperty;
import sleeper.configuration.properties.table.TableProperties;
import sleeper.configuration.properties.table.TableProperty;
import sleeper.core.record.Record;
import sleeper.core.schema.Field;
import sleeper.core.schema.Schema;
import sleeper.core.schema.type.ByteArrayType;
import sleeper.core.schema.type.ListType;
import sleeper.core.schema.type.MapType;
import sleeper.io.parquet.record.ParquetRecordWriter;
import sleeper.io.parquet.record.SchemaConverter;
import sleeper.sketches.Sketches;
import sleeper.sketches.s3.SketchesSerDeToS3;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class FileWritingIterator implements Iterator<Row> {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileWritingIterator.class);

    private final Iterator<Row> input;
    private final Schema schema;
    private final List<Field> allSchemaFields;
    private final Configuration conf;
    private final InstanceProperties instanceProperties;
    private final TableProperties tableProperties;
    private String currentPartitionId;
    private ParquetWriter<Record> parquetWriter;
    private Map<String, ItemsSketch> sketches;
    private String path;
    private long numRecords;
    private boolean hasMore = false;
    private Instant startTime = null;

    public FileWritingIterator(Iterator<Row> input, InstanceProperties instanceProperties, TableProperties tableProperties, Configuration conf) {
        this.input = input;
        this.instanceProperties = instanceProperties;
        this.tableProperties = tableProperties;
        this.schema = tableProperties.getSchema();
        this.allSchemaFields = schema.getAllFields();
        this.conf = conf;
        LOGGER.info("Initialised FileWritingIterator");
        LOGGER.info("Schema is {}", schema);
        LOGGER.info("Configuration is {}", conf);
    }

    @Override
    public boolean hasNext() {
        return input.hasNext() || hasMore;
    }

    @Override
    public Row next() {
        if (!hasNext()) {
            return null;
        }
        try {
            while (input.hasNext()) {
                Row row = input.next();
                // Get Partition Id for this row
                String partitionId = getPartitionId(row);

                if (!partitionId.equals(currentPartitionId)) {
                    if (currentPartitionId != null) {
                        // Write file and sketches
                        writeFiles();
                        Row fileInfo = RowFactory.create(currentPartitionId, path, numRecords);
                        initialiseState(partitionId);
                        write(row);
                        // Set flag in case this is the last record in the iterator
                        hasMore = true;
                        return fileInfo;
                    } else {
                        initialiseState(partitionId);
                    }
                }
                write(row);
            }

            // Flush final file
            writeFiles();
            hasMore = false;
            return RowFactory.create(currentPartitionId, path, numRecords);
        } catch (IOException e) {
            throw new RuntimeException("Encountered error while writing files", e);
        }
    }

    private void write(Row row) throws IOException {
        if (null == startTime) {
            startTime = Instant.now();
        }
        // Append to current writer
        Record record = getRecord(row);
        parquetWriter.write(record);
        numRecords++;
        if (numRecords % 1_000_000L == 0) {
            LOGGER.info("Wrote {} records", numRecords);
        }
        updateQuantilesSketch(record, sketches, schema.getRowKeyFields());
    }

    private void initialiseState(String partitionId) throws IOException {
        currentPartitionId = partitionId;
        // Create writer;
        parquetWriter = createWriter(partitionId);
        // Initialise sketches
        sketches = getSketches(schema.getRowKeyFields());
    }

    private void writeFiles() throws IOException {
        LOGGER.info("Flushing files to S3 containing {} records", numRecords);
        if (parquetWriter == null) {
            return;
        }
        parquetWriter.close();
        new SketchesSerDeToS3(schema).saveToHadoopFS(new Path(path.replace(".parquet", ".sketches")), new Sketches(sketches), conf);
        long durationInSeconds = Duration.between(startTime, Instant.now()).getSeconds();
        double rate = numRecords / (double) durationInSeconds;
        LOGGER.info("Overall written {} records in {} seconds (rate was {} per second)",
                numRecords, durationInSeconds, rate);
    }

    private Record getRecord(Row row) {
        Record record = new Record();
        int i = 0;
        for (Field field : allSchemaFields) {
            if (field.getType() instanceof ListType) {
                record.put(field.getName(), row.getList(i));
            } else if (field.getType() instanceof MapType) {
                record.put(field.getName(), row.getJavaMap(i));
            } else {
                record.put(field.getName(), row.get(i));
            }
            i++;
        }
        return record;
    }

    // TODO These methods are copies of the same ones in IngestRecordsFromIterator -
    // move to sketches module
    private Map<String, ItemsSketch> getSketches(List<Field> rowKeyFields) {
        Map<String, ItemsSketch> keyFieldToSketch = new HashMap<>();
        for (Field rowKeyField : rowKeyFields) {
            ItemsSketch<?> sketch = ItemsSketch.getInstance(1024, Comparator.naturalOrder());
            keyFieldToSketch.put(rowKeyField.getName(), sketch);
        }
        return keyFieldToSketch;
    }

    private void updateQuantilesSketch(
            Record record, Map<String, ItemsSketch> keyFieldToSketch, List<Field> rowKeyFields) {
        for (Field rowKeyField : rowKeyFields) {
            if (rowKeyField.getType() instanceof ByteArrayType) {
                byte[] value = (byte[]) record.get(rowKeyField.getName());
                keyFieldToSketch.get(rowKeyField.getName()).update(ByteArray.wrap(value));
            } else {
                Object value = record.get(rowKeyField.getName());
                keyFieldToSketch.get(rowKeyField.getName()).update(value);
            }
        }
    }

    private ParquetWriter<Record> createWriter(String partitionId) throws IOException {
        numRecords = 0L;
        path = instanceProperties.get(UserDefinedInstanceProperty.FILE_SYSTEM)
                + tableProperties.get(TableProperty.DATA_BUCKET) + "/partition_" + partitionId
                + "/" + UUID.randomUUID().toString() + ".parquet";

        int rowGroupSize = tableProperties.getInt(TableProperty.ROW_GROUP_SIZE);
        int pageSize = tableProperties.getInt(TableProperty.PAGE_SIZE);
        CompressionCodecName compressionCodec = CompressionCodecName.valueOf(
                tableProperties.get(TableProperty.COMPRESSION_CODEC).toUpperCase(Locale.ROOT));

        LOGGER.info("Creating writer for partition {} to path {} with row group size {}, page size {}, compression codec {}",
                partitionId, path, rowGroupSize, pageSize, compressionCodec);
        return new ParquetRecordWriter.Builder(new Path(path),
                SchemaConverter.getSchema(schema), schema)
                .withCompressionCodec(compressionCodec)
                .withRowGroupSize(rowGroupSize)
                .withPageSize(pageSize)
                .withConf(conf)
                .build();
    }

    private String getPartitionId(Row row) {
        return row.getString(row.length() - 1);
    }
}

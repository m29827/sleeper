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
package sleeper.statestore;

import sleeper.core.key.Key;
import sleeper.core.partition.Partition;
import sleeper.core.partition.PartitionTree;
import sleeper.core.schema.Schema;

import java.time.Instant;
import java.util.List;

public class FileInfoFactory {
    private final Schema schema;
    private final PartitionTree partitionTree;
    private final Instant lastStateStoreUpdate;

    public FileInfoFactory(Schema schema, List<Partition> partitions, Instant lastStateStoreUpdate) {
        this.schema = schema;
        this.lastStateStoreUpdate = lastStateStoreUpdate;
        this.partitionTree = new PartitionTree(schema, partitions);
    }

    public FileInfo leafFile(long records, Object min, Object max) {
        return fileForPartition(leafPartition(min, max), records, min, max);
    }

    public FileInfo middleFile(long records, Object min, Object max) {
        return fileForPartition(middlePartition(min, max), records, min, max);
    }

    public FileInfo rootFile(long records, Object min, Object max) {
        return fileForPartition(partitionTree.getRootPartition(), records, min, max);
    }

    public FileInfo leafFile(String filename, long records, Object min, Object max) {
        return fileForPartition(leafPartition(min, max), filename, records, min, max);
    }

    public FileInfo rootFile(String filename, long records, Object min, Object max) {
        return fileForPartition(partitionTree.getRootPartition(), filename, records, min, max);
    }

    private Partition leafPartition(Object min, Object max) {
        Partition partition = partitionTree.getLeafPartition(Key.create(min));
        if (!partition.isRowKeyInPartition(schema, Key.create(max))) {
            throw new IllegalArgumentException("Not in same leaf partition: " + min + ", " + max);
        }
        return partition;
    }

    private Partition middlePartition(Object min, Object max) {
        Partition partition = partitionTree.getNearestCommonAncestor(Key.create(min), Key.create(max));
        if (partition.isLeafPartition()) {
            throw new IllegalArgumentException("In same leaf partition: " + min + ", " + max);
        }
        if (partition.getParentPartitionId() == null) {
            throw new IllegalArgumentException("Nearest common ancestor is root partition: " + min + ", " + max);
        }
        return partition;
    }

    private FileInfo fileForPartition(Partition partition, long records, Object min, Object max) {
        return fileForPartition(partition, partition.getId() + ".parquet", records, min, max);
    }

    private FileInfo fileForPartition(Partition partition, String filename, long records, Object min, Object max) {
        FileInfo file = new FileInfo();
        file.setRowKeyTypes(partition.getRowKeyTypes());
        file.setMinRowKey(Key.create(min));
        file.setMaxRowKey(Key.create(max));
        file.setFilename(filename);
        file.setPartitionId(partition.getId());
        file.setNumberOfRecords(records);
        file.setFileStatus(FileInfo.FileStatus.ACTIVE);
        file.setLastStateStoreUpdateTime(lastStateStoreUpdate.toEpochMilli());
        return file;
    }

}

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
package sleeper.statestore.inmemory;

import sleeper.core.partition.Partition;
import sleeper.core.partition.PartitionsFromSplitPoints;
import sleeper.core.schema.Schema;
import sleeper.statestore.PartitionStore;
import sleeper.statestore.StateStoreException;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class InMemoryPartitionStore implements PartitionStore {

    private Map<String, Partition> partitionsById = Map.of();

    public InMemoryPartitionStore(List<Partition> partitions) {
        initialise(partitions);
    }

    public static PartitionStore withSinglePartition(Schema schema) {
        return new InMemoryPartitionStore(new PartitionsFromSplitPoints(schema, Collections.emptyList()).construct());
    }

    @Override
    public List<Partition> getAllPartitions() throws StateStoreException {
        return partitionsById.values().stream()
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public List<Partition> getLeafPartitions() throws StateStoreException {
        return partitionsById.values().stream()
                .filter(Partition::isLeafPartition)
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public void initialise() {
        throw new UnsupportedOperationException("Not supported because schema would be required");
    }

    @Override
    public void initialise(List<Partition> partitions) {
        partitionsById = partitions.stream()
                .collect(Collectors.toMap(Partition::getId, partition -> partition));
    }

    @Override
    public void atomicallyUpdatePartitionAndCreateNewOnes(
            Partition splitPartition, Partition newPartition1, Partition newPartition2) {
    }
}

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
package sleeper.statestore.transactionlog;

import org.apache.hadoop.conf.Configuration;

import sleeper.core.schema.Schema;
import sleeper.core.statestore.transactionlog.StateStoreFiles;
import sleeper.core.statestore.transactionlog.StateStorePartitions;
import sleeper.statestore.StateStoreFileUtils;

import java.io.IOException;

public class TransactionLogSnapshotSerDe {
    private final Schema sleeperSchema;
    private final StateStoreFileUtils stateStoreFileUtils;

    TransactionLogSnapshotSerDe(Schema sleeperSchema, Configuration configuration) {
        this.sleeperSchema = sleeperSchema;
        this.stateStoreFileUtils = new StateStoreFileUtils(configuration);
    }

    void savePartitions(TransactionLogSnapshot snapshot, StateStorePartitions state) throws IOException {
        stateStoreFileUtils.savePartitions(snapshot.getPath(), state, sleeperSchema);
    }

    StateStorePartitions loadPartitions(TransactionLogSnapshot snapshot) throws IOException {
        StateStorePartitions partitions = new StateStorePartitions();
        stateStoreFileUtils.loadPartitions(snapshot.getPath(), sleeperSchema, partitions::put);
        return partitions;
    }

    void saveFiles(TransactionLogSnapshot snapshot, StateStoreFiles state) throws IOException {
        stateStoreFileUtils.saveFiles(snapshot.getPath(), state);
    }

    StateStoreFiles loadFiles(TransactionLogSnapshot snapshot) throws IOException {
        StateStoreFiles files = new StateStoreFiles();
        stateStoreFileUtils.loadFiles(snapshot.getPath(), files::add);
        return files;
    }
}
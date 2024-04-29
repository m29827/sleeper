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

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sleeper.configuration.properties.instance.InstanceProperties;
import sleeper.configuration.properties.table.TableProperties;

import java.io.IOException;
import java.time.Instant;
import java.util.function.Supplier;

public class DeleteTransactionLogSnapshots {
    public static final Logger LOGGER = LoggerFactory.getLogger(DeleteTransactionLogSnapshots.class);
    private final Configuration configuration;
    private final DynamoDBTransactionLogSnapshotStore snapshotStore;

    public DeleteTransactionLogSnapshots(
            InstanceProperties instanceProperties, TableProperties tableProperties,
            AmazonDynamoDB dynamoDB, Configuration configuration, Supplier<Instant> timeSupplier) {
        this.configuration = configuration;
        this.snapshotStore = new DynamoDBTransactionLogSnapshotStore(instanceProperties, tableProperties, dynamoDB, timeSupplier);
    }

    public void deleteSnapshots() {
        try {
            FileSystem fs = FileSystem.get(configuration);
            snapshotStore.getOldestSnapshots()
                    .forEach(snapshot -> {
                        LOGGER.info("Deleting snapshot {}", snapshot);
                        try {
                            fs.delete(new Path(snapshot.getPath()), false);
                        } catch (IOException e) {
                            LOGGER.error("Failed to delete file {}", snapshot.getPath(), e);
                        }
                        snapshotStore.deleteSnapshot(snapshot);
                    });
        } catch (IOException e) {
            LOGGER.error("Failed to initialise file system");
        }
    }
}

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
import com.amazonaws.services.s3.AmazonS3;
import org.apache.hadoop.conf.Configuration;

import sleeper.configuration.properties.instance.InstanceProperties;
import sleeper.configuration.properties.table.TableProperties;
import sleeper.core.statestore.transactionlog.TransactionLogStateStore;

import java.io.IOException;

import static sleeper.configuration.properties.table.TableProperty.TRANSACTION_LOG_LOAD_LATEST_SNAPSHOTS;

public class DynamoDBTransactionLogStateStore {
    public static final String TABLE_ID = "TABLE_ID";
    public static final String TRANSACTION_NUMBER = "TRANSACTION_NUMBER";

    private DynamoDBTransactionLogStateStore() {
    }

    public static TransactionLogStateStore create(
            InstanceProperties instanceProperties, TableProperties tableProperties, AmazonDynamoDB dynamoDB, AmazonS3 s3, Configuration configuration) {
        return builderFrom(instanceProperties, tableProperties, dynamoDB, s3, configuration).build();
    }

    public static TransactionLogStateStore.Builder builderFrom(
            InstanceProperties instanceProperties, TableProperties tableProperties, AmazonDynamoDB dynamoDB, AmazonS3 s3, Configuration configuration) {
        TransactionLogStateStore.Builder builder = DynamoDBTransactionLogStateStoreNoShapshots.builderFrom(instanceProperties, tableProperties, dynamoDB, s3);
        if (tableProperties.getBoolean(TRANSACTION_LOG_LOAD_LATEST_SNAPSHOTS)) {
            loadLatestSnapshots(builder, instanceProperties, tableProperties, dynamoDB, configuration);
        }
        return builder;
    }

    private static void loadLatestSnapshots(
            TransactionLogStateStore.Builder builder, InstanceProperties instanceProperties, TableProperties tableProperties,
            AmazonDynamoDB dynamoDB, Configuration configuration) {
        new DynamoDBTransactionLogSnapshotStore(instanceProperties, tableProperties, dynamoDB).getLatestSnapshots()
                .ifPresent(latestSnapshots -> {
                    TransactionLogSnapshotSerDe snapshotSerDe = new TransactionLogSnapshotSerDe(tableProperties.getSchema(), configuration);
                    try {
                        if (latestSnapshots.getFilesSnapshot().getPath() != null) {
                            builder.filesState(snapshotSerDe.loadFiles(latestSnapshots.getFilesSnapshot()))
                                    .filesTransactionNumber(latestSnapshots.getFilesSnapshot().getTransactionNumber());
                        }
                        if (latestSnapshots.getPartitionsSnapshot().getPath() != null) {
                            builder.partitionsState(snapshotSerDe.loadPartitions(latestSnapshots.getPartitionsSnapshot()))
                                    .partitionsTransactionNumber(latestSnapshots.getPartitionsSnapshot().getTransactionNumber());
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to load latest snapshots", e);
                    }
                });
    }
}

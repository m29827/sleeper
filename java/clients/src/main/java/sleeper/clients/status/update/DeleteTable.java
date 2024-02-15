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

package sleeper.clients.status.update;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sleeper.configuration.properties.instance.InstanceProperties;
import sleeper.configuration.properties.table.S3TableProperties;
import sleeper.configuration.properties.table.TableProperties;
import sleeper.configuration.properties.table.TablePropertiesStore;
import sleeper.statestore.StateStoreProvider;

import static sleeper.configuration.properties.instance.CdkDefinedInstanceProperty.DATA_BUCKET;
import static sleeper.configuration.properties.table.TableProperty.TABLE_ID;
import static sleeper.configuration.utils.AwsV1ClientHelper.buildAwsV1Client;
import static sleeper.io.parquet.utils.HadoopConfigurationProvider.getConfigurationForClient;

public class DeleteTable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeleteTable.class);
    private final AmazonS3 s3Client;
    private final InstanceProperties instanceProperties;
    private final TablePropertiesStore tablePropertiesStore;
    private final StateStoreProvider stateStoreProvider;

    public DeleteTable(AmazonS3 s3Client, AmazonDynamoDB dynamoDB, InstanceProperties instanceProperties) {
        this(instanceProperties, s3Client, S3TableProperties.getStore(instanceProperties, s3Client, dynamoDB),
                new StateStoreProvider(dynamoDB, instanceProperties, getConfigurationForClient()));
    }

    public DeleteTable(InstanceProperties instanceProperties, AmazonS3 s3Client, TablePropertiesStore tablePropertiesStore, StateStoreProvider stateStoreProvider) {
        this.s3Client = s3Client;
        this.instanceProperties = instanceProperties;
        this.tablePropertiesStore = tablePropertiesStore;
        this.stateStoreProvider = stateStoreProvider;
    }

    public void delete(String tableName) {
        TableProperties tableProperties = tablePropertiesStore.loadByName(tableName);
        tablePropertiesStore.deleteByName(tableName);
        stateStoreProvider.getStateStore(tableProperties).clearSleeperTable();
        s3Client.listObjects(instanceProperties.get(DATA_BUCKET)).getObjectSummaries().stream()
                .filter(s3ObjectSummary -> s3ObjectSummary.getKey().startsWith(tableProperties.get(TABLE_ID)))
                .forEach(s3ObjectSummary ->
                        s3Client.deleteObject(instanceProperties.get(DATA_BUCKET), s3ObjectSummary.getKey()));
        LOGGER.info("Successfully deleted table {}", tableName);
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: <instance-id> <table-name>");
        }
        AmazonS3 s3Client = buildAwsV1Client(AmazonS3ClientBuilder.standard());
        AmazonDynamoDB dynamoDBClient = buildAwsV1Client(AmazonDynamoDBClientBuilder.standard());
        try {
            InstanceProperties instanceProperties = new InstanceProperties();
            instanceProperties.loadFromS3GivenInstanceId(s3Client, args[0]);
            new DeleteTable(s3Client, dynamoDBClient, instanceProperties).delete(args[1]);
        } finally {
            dynamoDBClient.shutdown();
            s3Client.shutdown();
        }
    }
}

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
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import org.apache.hadoop.conf.Configuration;

import sleeper.compaction.job.CompactionJobStatusStore;
import sleeper.compaction.job.creation.CreateCompactionJobs;
import sleeper.compaction.job.creation.SendCompactionJobToSqs;
import sleeper.compaction.status.store.job.CompactionJobStatusStoreFactory;
import sleeper.configuration.jars.ObjectFactory;
import sleeper.configuration.jars.ObjectFactoryException;
import sleeper.configuration.properties.instance.InstanceProperties;
import sleeper.configuration.properties.table.TablePropertiesProvider;
import sleeper.core.statestore.StateStoreException;
import sleeper.io.parquet.utils.HadoopConfigurationProvider;
import sleeper.statestore.StateStoreProvider;

import java.io.IOException;
import java.util.Optional;

import static sleeper.clients.util.ClientUtils.optionalArgument;
import static sleeper.configuration.utils.AwsV1ClientHelper.buildAwsV1Client;

public class CreateJobsClient {
    private CreateJobsClient() {
    }

    public static void main(String[] args) throws ObjectFactoryException, StateStoreException, IOException {
        if (args.length < 1 || args.length > 2) {
            System.out.println("Usage: <instance-id> <optional-all-flag>");
            return;
        }
        String instanceId = args[0];
        Optional<String> compactAllFlag = optionalArgument(args, 1);
        boolean compactAll = compactAllFlag.isPresent() && compactAllFlag.get().equals("--all");
        AmazonS3 s3Client = buildAwsV1Client(AmazonS3ClientBuilder.standard());
        AmazonDynamoDB dynamoDBClient = buildAwsV1Client(AmazonDynamoDBClientBuilder.standard());
        AmazonSQS sqsClient = buildAwsV1Client(AmazonSQSClientBuilder.standard());
        try {
            InstanceProperties instanceProperties = new InstanceProperties();
            instanceProperties.loadFromS3GivenInstanceId(s3Client, instanceId);

            TablePropertiesProvider tablePropertiesProvider = new TablePropertiesProvider(instanceProperties, s3Client, dynamoDBClient);
            Configuration conf = HadoopConfigurationProvider.getConfigurationForClient(instanceProperties);
            StateStoreProvider stateStoreProvider = new StateStoreProvider(dynamoDBClient, instanceProperties, conf);
            CompactionJobStatusStore jobStatusStore = CompactionJobStatusStoreFactory.getStatusStore(dynamoDBClient, instanceProperties);
            CreateCompactionJobs jobCreator;
            if (compactAll) {
                jobCreator = CreateCompactionJobs.compactAllFiles(
                        new ObjectFactory(instanceProperties, s3Client, "/tmp"),
                        instanceProperties, tablePropertiesProvider, stateStoreProvider,
                        new SendCompactionJobToSqs(instanceProperties, sqsClient)::send, jobStatusStore);
            } else {
                jobCreator = CreateCompactionJobs.standard(
                        new ObjectFactory(instanceProperties, s3Client, "/tmp"),
                        instanceProperties, tablePropertiesProvider, stateStoreProvider,
                        new SendCompactionJobToSqs(instanceProperties, sqsClient)::send, jobStatusStore);
            }
            jobCreator.createJobs();
        } finally {
            s3Client.shutdown();
            dynamoDBClient.shutdown();
            sqsClient.shutdown();
        }
    }
}
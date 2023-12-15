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
package sleeper.compaction.job.creation;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import org.apache.hadoop.conf.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sleeper.compaction.job.CompactionJobStatusStore;
import sleeper.compaction.status.store.job.CompactionJobStatusStoreFactory;
import sleeper.configuration.jars.ObjectFactory;
import sleeper.configuration.jars.ObjectFactoryException;
import sleeper.configuration.properties.PropertiesReloader;
import sleeper.configuration.properties.instance.InstanceProperties;
import sleeper.configuration.properties.table.TablePropertiesProvider;
import sleeper.core.statestore.StateStoreException;
import sleeper.core.util.LoggedDuration;
import sleeper.io.parquet.utils.HadoopConfigurationProvider;
import sleeper.statestore.StateStoreProvider;

import java.io.IOException;
import java.time.Instant;

import static sleeper.configuration.properties.instance.CdkDefinedInstanceProperty.CONFIG_BUCKET;

/**
 * A lambda function for executing {@link CreateJobs}.
 */
@SuppressWarnings("unused")
public class CreateJobsLambda {
    private final AmazonDynamoDB dynamoDBClient;
    private AmazonSQS sqsClient;
    private final InstanceProperties instanceProperties;
    private final ObjectFactory objectFactory;
    private final TablePropertiesProvider tablePropertiesProvider;
    private final PropertiesReloader propertiesReloader;
    private final StateStoreProvider stateStoreProvider;
    private final CompactionJobStatusStore jobStatusStore;

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateJobsLambda.class);

    /**
     * No-args constructor used by Lambda service. Dynamo file table name will be obtained from an environment variable.
     *
     * @throws ObjectFactoryException if user jars cannot be loaded
     */
    public CreateJobsLambda() throws ObjectFactoryException {
        AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();
        String s3Bucket = System.getenv(CONFIG_BUCKET.toEnvironmentVariable());

        this.instanceProperties = new InstanceProperties();
        this.instanceProperties.loadFromS3(s3Client, s3Bucket);

        this.objectFactory = new ObjectFactory(instanceProperties, s3Client, "/tmp");

        this.dynamoDBClient = AmazonDynamoDBClientBuilder.defaultClient();
        this.sqsClient = AmazonSQSClientBuilder.defaultClient();
        this.tablePropertiesProvider = new TablePropertiesProvider(instanceProperties, s3Client, dynamoDBClient);
        this.propertiesReloader = PropertiesReloader.ifConfigured(s3Client, instanceProperties, tablePropertiesProvider);
        Configuration conf = HadoopConfigurationProvider.getConfigurationForLambdas(instanceProperties);
        this.stateStoreProvider = new StateStoreProvider(dynamoDBClient, instanceProperties, conf);
        this.jobStatusStore = CompactionJobStatusStoreFactory.getStatusStore(dynamoDBClient, instanceProperties);
    }

    /**
     * Constructor used in tests.
     *
     * @param instanceProperties    The SleeperProperties
     * @param endpointConfiguration The configuration of the endpoint for the DynamoDB client
     * @throws ObjectFactoryException if user jars cannot be loaded
     */
    public CreateJobsLambda(InstanceProperties instanceProperties,
                            AwsClientBuilder.EndpointConfiguration endpointConfiguration) throws ObjectFactoryException {
        AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();
        this.instanceProperties = instanceProperties;

        this.objectFactory = new ObjectFactory(instanceProperties, s3Client, "/tmp");

        this.dynamoDBClient = AmazonDynamoDBClientBuilder.standard()
                .withEndpointConfiguration(endpointConfiguration)
                .build();
        this.tablePropertiesProvider = new TablePropertiesProvider(instanceProperties, s3Client, dynamoDBClient);
        this.propertiesReloader = PropertiesReloader.ifConfigured(s3Client, instanceProperties, tablePropertiesProvider);
        this.stateStoreProvider = new StateStoreProvider(dynamoDBClient, instanceProperties,
                HadoopConfigurationProvider.getConfigurationForLambdas(instanceProperties));
        this.jobStatusStore = CompactionJobStatusStoreFactory.getStatusStore(dynamoDBClient, instanceProperties);
    }

    public void eventHandler(ScheduledEvent event, Context context) {
        Instant startTime = Instant.now();
        LOGGER.info("CreateJobsLambda lambda triggered at {}", event.getTime());
        propertiesReloader.reloadIfNeeded();

        CreateJobs createJobs = CreateJobs.standard(objectFactory, instanceProperties, tablePropertiesProvider, stateStoreProvider,
                new SendCompactionJobToSqs(instanceProperties, sqsClient)::send, jobStatusStore);
        try {
            createJobs.createJobs();
        } catch (StateStoreException | IOException | ObjectFactoryException e) {
            LOGGER.error("Exception thrown whilst creating jobs", e);
        }

        Instant finishTime = Instant.now();
        LOGGER.info("CreateJobsLambda lambda finished at {} (ran for {})", finishTime, LoggedDuration.withFullOutput(startTime, finishTime));
    }
}

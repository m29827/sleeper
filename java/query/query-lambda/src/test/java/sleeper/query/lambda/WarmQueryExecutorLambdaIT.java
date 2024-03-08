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
package sleeper.query.lambda;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import org.apache.hadoop.conf.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import sleeper.configuration.jars.ObjectFactoryException;
import sleeper.configuration.properties.instance.InstanceProperties;
import sleeper.configuration.properties.table.S3TableProperties;
import sleeper.configuration.properties.table.TableProperties;
import sleeper.configuration.table.index.DynamoDBTableIndexCreator;
import sleeper.core.CommonTestConstants;
import sleeper.core.partition.PartitionsFromSplitPoints;
import sleeper.core.range.Range;
import sleeper.core.schema.Field;
import sleeper.core.schema.Schema;
import sleeper.core.schema.type.ByteArrayType;
import sleeper.core.schema.type.IntType;
import sleeper.core.schema.type.LongType;
import sleeper.core.schema.type.StringType;
import sleeper.core.statestore.StateStore;
import sleeper.core.statestore.StateStoreException;
import sleeper.query.model.Query;
import sleeper.query.model.QuerySerDe;
import sleeper.query.output.ResultsOutputConstants;
import sleeper.query.runner.tracker.DynamoDBQueryTrackerCreator;
import sleeper.statestore.StateStoreProvider;
import sleeper.statestore.s3.S3StateStoreCreator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;

import static java.nio.file.Files.createTempDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static sleeper.configuration.properties.InstancePropertiesTestHelper.createTestInstanceProperties;
import static sleeper.configuration.properties.instance.CdkDefinedInstanceProperty.CONFIG_BUCKET;
import static sleeper.configuration.properties.instance.CdkDefinedInstanceProperty.DATA_BUCKET;
import static sleeper.configuration.properties.instance.CdkDefinedInstanceProperty.LEAF_PARTITION_QUERY_QUEUE_URL;
import static sleeper.configuration.properties.instance.CdkDefinedInstanceProperty.QUERY_QUEUE_URL;
import static sleeper.configuration.properties.instance.CdkDefinedInstanceProperty.QUERY_RESULTS_BUCKET;
import static sleeper.configuration.properties.instance.CdkDefinedInstanceProperty.QUERY_RESULTS_QUEUE_URL;
import static sleeper.configuration.properties.instance.CommonProperty.FILE_SYSTEM;
import static sleeper.configuration.properties.instance.DefaultProperty.DEFAULT_INGEST_PARTITION_FILE_WRITER_TYPE;
import static sleeper.configuration.properties.table.TablePropertiesTestHelper.createTestTableProperties;
import static sleeper.configuration.properties.table.TableProperty.TABLE_NAME;
import static sleeper.configuration.testutils.LocalStackAwsV1ClientHelper.buildAwsV1Client;
import static sleeper.io.parquet.utils.HadoopConfigurationLocalStackUtils.getHadoopConfiguration;
import static sleeper.query.runner.output.NoResultsOutput.NO_RESULTS_OUTPUT;

@Testcontainers
public class WarmQueryExecutorLambdaIT {
    @Container
    public static LocalStackContainer localStackContainer = new LocalStackContainer(
            DockerImageName.parse(CommonTestConstants.LOCALSTACK_DOCKER_IMAGE))
            .withServices(LocalStackContainer.Service.S3, LocalStackContainer.Service.SQS,
                    LocalStackContainer.Service.DYNAMODB);

    private final AmazonS3 s3Client = buildAwsV1Client(localStackContainer, LocalStackContainer.Service.S3,
            AmazonS3ClientBuilder.standard());
    private final AmazonDynamoDB dynamoClient = buildAwsV1Client(localStackContainer,
            LocalStackContainer.Service.DYNAMODB, AmazonDynamoDBClientBuilder.standard());
    private final AmazonSQS sqsClient = buildAwsV1Client(localStackContainer, LocalStackContainer.Service.SQS,
            AmazonSQSClientBuilder.standard());
    private final Configuration configuration = getHadoopConfiguration(localStackContainer);
    private QuerySerDe querySerDe;

    @TempDir
    public Path tempDir;
    private InstanceProperties instanceProperties;
    protected static WarmQueryExecutorLambda lambda;

    @BeforeEach
    void setUp() throws IOException, ObjectFactoryException {
        String dataDir = createTempDirectory(tempDir, null).toString();
        createInstanceProperties(dataDir);
        lambda = new WarmQueryExecutorLambda(s3Client, sqsClient, dynamoClient, instanceProperties.get(CONFIG_BUCKET));
    }

    @AfterEach
    void tearDown() {
        s3Client.shutdown();
        dynamoClient.shutdown();
        sqsClient.shutdown();
    }

    @Test
    public void shouldCreateAQueryWithKeyTypeOfString() throws Exception {
        // When
        Schema schema = getStringKeySchema();
        TableProperties tableProperties = createTestTableProperties(instanceProperties, schema);
        createTable(tableProperties);
        querySerDe = new QuerySerDe(schema);

        // Given
        lambda.handleRequest(new ScheduledEvent(), null);

        // Then
        ReceiveMessageResult result = sqsClient.receiveMessage(new ReceiveMessageRequest(instanceProperties.get(QUERY_QUEUE_URL)));
        assertThat(result.getMessages()).hasSize(1);

        Query query = querySerDe.fromJson(result.getMessages().get(0).getBody());
        assertThat(query.getTableName().equals(tableProperties.get(TABLE_NAME)));
        assertThat(query.getResultsPublisherConfig().get(ResultsOutputConstants.DESTINATION).equals(NO_RESULTS_OUTPUT));
        query.getRegions().forEach(region -> {
            Range range = region.getRange("key");
            assertThat(range.getField().getType()).isInstanceOf(StringType.class);
            assertThat(range.getMin()).isEqualTo("a");
            assertThat(range.getMax()).isEqualTo("a");
        });
    }

    @Test
    public void shouldCreateAQueryWithKeyTypeOfByteArray() throws Exception {
        // When
        Schema schema = getByteArrayKeySchema();
        TableProperties tableProperties = createTestTableProperties(instanceProperties, schema);
        createTable(tableProperties);
        querySerDe = new QuerySerDe(schema);

        // Given
        lambda.handleRequest(new ScheduledEvent(), null);

        // Then
        ReceiveMessageResult result = sqsClient.receiveMessage(new ReceiveMessageRequest(instanceProperties.get(QUERY_QUEUE_URL)));
        assertThat(result.getMessages()).hasSize(1);

        Query query = querySerDe.fromJson(result.getMessages().get(0).getBody());
        assertThat(query.getTableName().equals(tableProperties.get(TABLE_NAME)));
        assertThat(query.getResultsPublisherConfig().get(ResultsOutputConstants.DESTINATION).equals(NO_RESULTS_OUTPUT));
        query.getRegions().forEach(region -> {
            Range range = region.getRange("key");
            byte[] value = new byte[]{'a'};
            assertThat(range.getField().getType()).isInstanceOf(ByteArrayType.class);
            assertThat(range.getMin()).isEqualTo(value);
            assertThat(range.getMax()).isEqualTo(value);
        });
    }

    @Test
    public void shouldCreateAQueryWithKeyTypeOfInt() throws Exception {
        // When
        Schema schema = getIntKeySchema();
        TableProperties tableProperties = createTestTableProperties(instanceProperties, schema);
        createTable(tableProperties);
        querySerDe = new QuerySerDe(schema);

        // Given
        lambda.handleRequest(new ScheduledEvent(), null);

        // Then
        ReceiveMessageResult result = sqsClient.receiveMessage(new ReceiveMessageRequest(instanceProperties.get(QUERY_QUEUE_URL)));
        assertThat(result.getMessages()).hasSize(1);

        Query query = querySerDe.fromJson(result.getMessages().get(0).getBody());
        assertThat(query.getTableName().equals(tableProperties.get(TABLE_NAME)));
        assertThat(query.getResultsPublisherConfig().get(ResultsOutputConstants.DESTINATION).equals(NO_RESULTS_OUTPUT));
        query.getRegions().forEach(region -> {
            Range range = region.getRange("key");
            assertThat(range.getField().getType()).isInstanceOf(IntType.class);
            assertThat(range.getMin()).isEqualTo(0);
            assertThat(range.getMax()).isEqualTo(1);
        });
    }

    @Test
    public void shouldCreateAQueryWithKeyTypeOfLong() throws Exception {
        // When
        Schema schema = getLongKeySchema();
        TableProperties tableProperties = createTestTableProperties(instanceProperties, schema);
        createTable(tableProperties);
        querySerDe = new QuerySerDe(schema);

        // Given
        lambda.handleRequest(new ScheduledEvent(), null);

        // Then
        ReceiveMessageResult result = sqsClient.receiveMessage(new ReceiveMessageRequest(instanceProperties.get(QUERY_QUEUE_URL)));
        assertThat(result.getMessages()).hasSize(1);

        Query query = querySerDe.fromJson(result.getMessages().get(0).getBody());
        assertThat(query.getTableName().equals(tableProperties.get(TABLE_NAME)));
        assertThat(query.getResultsPublisherConfig().get(ResultsOutputConstants.DESTINATION).equals(NO_RESULTS_OUTPUT));
        query.getRegions().forEach(region -> {
            Range range = region.getRange("key");
            assertThat(range.getField().getType()).isInstanceOf(LongType.class);
            assertThat(range.getMin()).isEqualTo(0L);
            assertThat(range.getMax()).isEqualTo(1L);
        });
    }

    private Schema getStringKeySchema() {
        return Schema.builder()
                .rowKeyFields(new Field("key", new StringType()))
                .valueFields(new Field("value", new StringType()))
                .build();
    }

    private Schema getByteArrayKeySchema() {
        return Schema.builder()
                .rowKeyFields(new Field("key", new ByteArrayType()))
                .valueFields(new Field("value", new ByteArrayType()))
                .build();
    }

    private Schema getIntKeySchema() {
        return Schema.builder()
                .rowKeyFields(new Field("key", new IntType()))
                .valueFields(new Field("value", new IntType()))
                .build();
    }

    private Schema getLongKeySchema() {
        return Schema.builder()
                .rowKeyFields(new Field("key", new LongType()))
                .valueFields(new Field("value", new LongType()))
                .build();
    }

    private void createInstanceProperties(String dir) throws IOException {
        instanceProperties = createTestInstanceProperties();
        instanceProperties.set(DEFAULT_INGEST_PARTITION_FILE_WRITER_TYPE, "direct");
        instanceProperties.set(FILE_SYSTEM, "file://");
        instanceProperties.set(DATA_BUCKET, dir);
        instanceProperties.set(CONFIG_BUCKET, "testing");
        instanceProperties.set(QUERY_QUEUE_URL, sqsClient.createQueue(UUID.randomUUID().toString()).getQueueUrl());
        instanceProperties.set(LEAF_PARTITION_QUERY_QUEUE_URL,
                sqsClient.createQueue(UUID.randomUUID().toString()).getQueueUrl());
        instanceProperties.set(QUERY_RESULTS_QUEUE_URL,
                sqsClient.createQueue(UUID.randomUUID().toString()).getQueueUrl());
        instanceProperties.set(QUERY_RESULTS_BUCKET, dir + "/query-results");

        s3Client.createBucket(instanceProperties.get(CONFIG_BUCKET));
        instanceProperties.saveToS3(s3Client);

        new DynamoDBQueryTrackerCreator(instanceProperties, dynamoClient).create();
        DynamoDBTableIndexCreator.create(dynamoClient, instanceProperties);
        new S3StateStoreCreator(instanceProperties, dynamoClient).create();
    }

    private void createTable(TableProperties tableProperties) {
        S3TableProperties.getStore(instanceProperties, s3Client, dynamoClient).save(tableProperties);
        StateStore stateStore = new StateStoreProvider(dynamoClient, instanceProperties, configuration)
                .getStateStore(tableProperties);
        try {
            stateStore.initialise(new PartitionsFromSplitPoints(tableProperties.getSchema(), new ArrayList<>()).construct());
        } catch (StateStoreException e) {
            throw new RuntimeException(e);
        }
    }
}

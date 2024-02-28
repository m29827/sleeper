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
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sleeper.configuration.jars.ObjectFactoryException;
import sleeper.configuration.properties.instance.InstanceProperties;
import sleeper.configuration.properties.table.S3TableProperties;
import sleeper.configuration.properties.table.TableProperties;
import sleeper.configuration.properties.table.TablePropertiesProvider;
import sleeper.core.range.Range;
import sleeper.core.range.Region;
import sleeper.core.schema.Field;
import sleeper.core.schema.Schema;
import sleeper.query.model.Query;
import sleeper.query.model.QueryProcessingConfig;
import sleeper.query.model.QuerySerDe;
import sleeper.query.output.ResultsOutputConstants;
import sleeper.query.runner.recordretrieval.QueryExecutor;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static sleeper.configuration.properties.instance.CdkDefinedInstanceProperty.CONFIG_BUCKET;
import static sleeper.configuration.properties.instance.CdkDefinedInstanceProperty.QUERY_QUEUE_URL;
import static sleeper.configuration.properties.table.TableProperty.TABLE_NAME;
import static sleeper.query.runner.output.NoResultsOutput.NO_RESULTS_OUTPUT;

/**
 * A lambda that is triggered when a serialised query arrives on an SQS queue. A processor executes the request using a
 * {@link QueryExecutor} and publishes the results to either SQS or S3 based on the configuration of the query.
 * The processor contains a cache that includes mappings from partitions to files in those partitions. This is reused by
 * subsequent calls to the lambda if the AWS runtime chooses to reuse the instance.
 */
@SuppressWarnings("unused")
public class WarmQueryExecutorLambda implements RequestHandler<ScheduledEvent, Void> {
    private static final Logger LOGGER = LoggerFactory.getLogger(WarmQueryExecutorLambda.class);

    private Instant lastUpdateTime;
    private InstanceProperties instanceProperties;
    private TablePropertiesProvider tablePropertiesProvider;
    private final AmazonSQS sqsClient;
    private final AmazonS3 s3Client;
    private final AmazonDynamoDB dynamoClient;
    private QueryMessageHandler messageHandler;
    private SqsQueryProcessor processor;

    public WarmQueryExecutorLambda() throws ObjectFactoryException {
        this(AmazonS3ClientBuilder.defaultClient(), AmazonSQSClientBuilder.defaultClient(),
                AmazonDynamoDBClientBuilder.defaultClient(), System.getenv(CONFIG_BUCKET.toEnvironmentVariable()));
    }

    public WarmQueryExecutorLambda(AmazonS3 s3Client, AmazonSQS sqsClient, AmazonDynamoDB dynamoClient, String configBucket) throws ObjectFactoryException {
        this.s3Client = s3Client;
        this.sqsClient = sqsClient;
        this.dynamoClient = dynamoClient;
        instanceProperties = loadInstanceProperties(s3Client, configBucket);
        tablePropertiesProvider = new TablePropertiesProvider(instanceProperties, s3Client, dynamoClient);
    }

    @Override
    public Void handleRequest(ScheduledEvent event, Context context) {
        List<TableProperties> tableProperties = S3TableProperties.getStore(instanceProperties, s3Client, dynamoClient)
                    .streamAllTables().collect(Collectors.toList());

        LOGGER.info("Starting to build queries for the tables");
        tableProperties.forEach(tableProperty -> {

            Schema schema = tableProperty.getSchema();
            Field field = schema.getRowKeyFields().get(0);
            Region region = new Region(Collections.singletonList(new Range.RangeFactory(schema)
                    .createRange(field, "a", "aa")));

            QuerySerDe querySerDe = new QuerySerDe(schema);
            Query query = Query.builder()
            .queryId(UUID.randomUUID().toString())
            .tableName(tableProperty.get(TABLE_NAME))
            .regions(List.of(region))
            .processingConfig(QueryProcessingConfig.builder()
                .resultsPublisherConfig(Collections.singletonMap(ResultsOutputConstants.DESTINATION, NO_RESULTS_OUTPUT))
                .statusReportDestinations(Collections.emptyList())
                .build())
            .build();

            LOGGER.info("Query to be sent: " + querySerDe.toJson(query));
            SendMessageRequest message = new SendMessageRequest(instanceProperties.get(QUERY_QUEUE_URL), querySerDe.toJson(query));
            LOGGER.debug("Message: {}", message);
            sqsClient.sendMessage(message);
        });
        return null;
    }

    private static InstanceProperties loadInstanceProperties(AmazonS3 s3Client, String configBucket) {
        InstanceProperties properties = new InstanceProperties();
        properties.loadFromS3(s3Client, configBucket);
        return properties;
    }
}

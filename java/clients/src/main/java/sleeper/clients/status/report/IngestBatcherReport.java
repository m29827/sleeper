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

package sleeper.clients.status.report;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

import sleeper.clients.status.report.ingest.batcher.BatcherQuery;
import sleeper.clients.status.report.ingest.batcher.IngestBatcherReporter;
import sleeper.clients.status.report.ingest.batcher.JsonIngestBatcherReporter;
import sleeper.clients.status.report.ingest.batcher.StandardIngestBatcherReporter;
import sleeper.clients.util.ClientUtils;
import sleeper.clients.util.console.ConsoleInput;
import sleeper.configuration.properties.instance.InstanceProperties;
import sleeper.configuration.properties.table.TablePropertiesProvider;
import sleeper.ingest.batcher.IngestBatcherStore;
import sleeper.ingest.batcher.store.DynamoDBIngestBatcherStore;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static sleeper.clients.util.ClientUtils.optionalArgument;

public class IngestBatcherReport {
    private static final String DEFAULT_REPORTER = "STANDARD";
    private static final Map<String, IngestBatcherReporter> REPORTERS = new HashMap<>();
    private static final Map<String, BatcherQuery.Type> QUERY_TYPES = new HashMap<>();

    static {
        REPORTERS.put(DEFAULT_REPORTER, new StandardIngestBatcherReporter());
        REPORTERS.put("JSON", new JsonIngestBatcherReporter());
        QUERY_TYPES.put("-a", BatcherQuery.Type.ALL);
        QUERY_TYPES.put("-p", BatcherQuery.Type.PENDING);
    }

    private final IngestBatcherStore batcherStore;
    private final IngestBatcherReporter reporter;
    private final BatcherQuery.Type queryType;
    private final BatcherQuery query;

    public IngestBatcherReport(IngestBatcherStore batcherStore, IngestBatcherReporter reporter,
                               BatcherQuery.Type queryType) {
        this.batcherStore = batcherStore;
        this.reporter = reporter;
        this.query = BatcherQuery.from(queryType, new ConsoleInput(System.console()));
        this.queryType = query.getType();
    }

    public void run() {
        if (query == null) {
            return;
        }
        reporter.report(query.run(batcherStore), queryType);
    }

    public static void main(String[] args) throws IOException {
        String instanceId = null;
        IngestBatcherReporter reporter = null;
        BatcherQuery.Type queryType = null;
        try {
            if (args.length < 2 || args.length > 3) {
                throw new IllegalArgumentException("Wrong number of arguments");
            }
            instanceId = args[0];
            reporter = getReporter(args, 1);
            queryType = optionalArgument(args, 2)
                    .map(IngestBatcherReport::readQueryType)
                    .orElse(BatcherQuery.Type.PROMPT);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            printUsage();
            System.exit(1);
        }
        AmazonS3 amazonS3 = AmazonS3ClientBuilder.defaultClient();
        InstanceProperties instanceProperties = ClientUtils.getInstanceProperties(amazonS3, instanceId);

        AmazonDynamoDB dynamoDBClient = AmazonDynamoDBClientBuilder.defaultClient();
        IngestBatcherStore statusStore = new DynamoDBIngestBatcherStore(dynamoDBClient, instanceProperties,
                new TablePropertiesProvider(amazonS3, instanceProperties));
        new IngestBatcherReport(statusStore, reporter, queryType).run();

        amazonS3.shutdown();
        dynamoDBClient.shutdown();
    }

    private static BatcherQuery.Type readQueryType(String queryTypeStr) {
        if (!QUERY_TYPES.containsKey(queryTypeStr)) {
            throw new IllegalArgumentException("Invalid query type " + queryTypeStr);
        }
        return QUERY_TYPES.get(queryTypeStr);
    }

    private static void printUsage() {
        System.out.println("" +
                "Usage: <instance-id> <report-type-standard-or-json> <optional-query-type>\n" +
                "Query types are:\n" +
                "-a (All files)\n" +
                "-p (Pending files)");
    }

    private static IngestBatcherReporter getReporter(String[] args, int index) {
        String reporterType = optionalArgument(args, index)
                .map(str -> str.toUpperCase(Locale.ROOT))
                .orElse(DEFAULT_REPORTER);
        if (!REPORTERS.containsKey(reporterType)) {
            throw new IllegalArgumentException("Output type not supported: " + reporterType);
        }
        return REPORTERS.get(reporterType);
    }
}
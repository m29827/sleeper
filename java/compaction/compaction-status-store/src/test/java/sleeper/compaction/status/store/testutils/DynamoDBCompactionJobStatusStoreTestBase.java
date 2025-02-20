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
package sleeper.compaction.status.store.testutils;

import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import sleeper.compaction.job.CompactionJob;
import sleeper.compaction.job.CompactionJobFactory;
import sleeper.compaction.job.CompactionJobStatusStore;
import sleeper.compaction.job.status.CompactionJobStatus;
import sleeper.compaction.status.store.job.CompactionJobStatusStoreFactory;
import sleeper.compaction.status.store.job.DynamoDBCompactionJobStatusStore;
import sleeper.compaction.status.store.job.DynamoDBCompactionJobStatusStoreCreator;
import sleeper.configuration.properties.instance.InstanceProperties;
import sleeper.configuration.properties.table.TableProperties;
import sleeper.core.partition.Partition;
import sleeper.core.partition.PartitionsBuilder;
import sleeper.core.partition.PartitionsFromSplitPoints;
import sleeper.core.record.process.ProcessRunTime;
import sleeper.core.record.process.RecordsProcessed;
import sleeper.core.record.process.RecordsProcessedSummary;
import sleeper.core.record.process.status.ProcessRun;
import sleeper.core.schema.Schema;
import sleeper.core.schema.type.StringType;
import sleeper.core.statestore.FileReferenceFactory;
import sleeper.dynamodb.tools.DynamoDBTestBase;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static sleeper.compaction.job.CompactionJobStatusTestData.compactionCommittedStatus;
import static sleeper.compaction.job.CompactionJobStatusTestData.compactionFinishedStatusUncommitted;
import static sleeper.compaction.job.CompactionJobStatusTestData.compactionStartedStatus;
import static sleeper.compaction.job.CompactionJobStatusTestData.failedCompactionRun;
import static sleeper.compaction.job.CompactionJobStatusTestData.finishedCompactionRun;
import static sleeper.compaction.job.CompactionJobStatusTestData.jobCreated;
import static sleeper.compaction.job.CompactionJobStatusTestData.startedCompactionRun;
import static sleeper.configuration.properties.InstancePropertiesTestHelper.createTestInstanceProperties;
import static sleeper.configuration.properties.instance.CommonProperty.ID;
import static sleeper.configuration.properties.instance.CompactionProperty.COMPACTION_JOB_STATUS_TTL_IN_SECONDS;
import static sleeper.configuration.properties.table.TablePropertiesTestHelper.createTestTableProperties;
import static sleeper.configuration.properties.table.TableProperty.TABLE_ID;
import static sleeper.core.schema.SchemaTestHelper.schemaWithKey;

public class DynamoDBCompactionJobStatusStoreTestBase extends DynamoDBTestBase {

    protected static final RecursiveComparisonConfiguration IGNORE_UPDATE_TIMES = RecursiveComparisonConfiguration.builder()
            .withIgnoredFields("createdStatus.updateTime", "expiryDate")
            .withIgnoredFieldsMatchingRegexes("jobRun.+updateTime").build();
    public static final String DEFAULT_TASK_ID = "task-id";
    public static final String DEFAULT_TASK_ID_2 = "task-id-2";
    private final InstanceProperties instanceProperties = createTestInstanceProperties();
    private final String jobStatusTableName = DynamoDBCompactionJobStatusStore.jobLookupTableName(instanceProperties.get(ID));
    private final Schema schema = schemaWithKey("key", new StringType());
    private final TableProperties tableProperties = createTestTableProperties(instanceProperties, schema);

    protected final String tableId = tableProperties.get(TABLE_ID);
    protected final CompactionJobFactory jobFactory = new CompactionJobFactory(instanceProperties, tableProperties);
    protected final CompactionJobStatusStore store = CompactionJobStatusStoreFactory.getStatusStore(dynamoDBClient, instanceProperties);

    @BeforeEach
    public void setUp() {
        DynamoDBCompactionJobStatusStoreCreator.create(instanceProperties, dynamoDBClient);
    }

    @AfterEach
    public void tearDown() {
        dynamoDBClient.deleteTable(jobStatusTableName);
    }

    protected CompactionJobStatusStore storeWithTimeToLiveAndUpdateTimes(Duration timeToLive, Instant... updateTimes) {
        instanceProperties.set(COMPACTION_JOB_STATUS_TTL_IN_SECONDS, "" + timeToLive.getSeconds());
        return new DynamoDBCompactionJobStatusStore(dynamoDBClient, instanceProperties,
                true, Arrays.stream(updateTimes).iterator()::next);
    }

    protected Partition singlePartition() {
        return new PartitionsFromSplitPoints(schema, Collections.emptyList()).construct().get(0);
    }

    protected FileReferenceFactory fileFactory(Partition singlePartition) {
        return fileFactory(Collections.singletonList(singlePartition));
    }

    protected FileReferenceFactory fileFactoryWithPartitions(Consumer<PartitionsBuilder> partitionConfig) {
        PartitionsBuilder builder = new PartitionsBuilder(schema);
        partitionConfig.accept(builder);
        return fileFactory(builder.buildList());
    }

    private FileReferenceFactory fileFactory(List<Partition> partitions) {
        return FileReferenceFactory.from(partitions);
    }

    protected CompactionJobFactory jobFactoryForOtherTable() {
        TableProperties tableProperties = createTestTableProperties(instanceProperties, schema);
        return new CompactionJobFactory(instanceProperties, tableProperties);
    }

    protected static Instant ignoredUpdateTime() {
        return Instant.now();
    }

    protected static Instant defaultStartTime() {
        return Instant.parse("2022-09-23T10:51:00.001Z");
    }

    protected static Instant defaultCommitTime() {
        return Instant.parse("2022-09-23T10:53:00.001Z");
    }

    protected static RecordsProcessedSummary defaultSummary() {
        return new RecordsProcessedSummary(
                new RecordsProcessed(200L, 100L),
                defaultRunTime());
    }

    protected static ProcessRunTime defaultRunTime() {
        return new ProcessRunTime(
                defaultStartTime(), Instant.parse("2022-09-23T10:52:00.001Z"));
    }

    protected static CompactionJobStatus startedStatusWithDefaults(CompactionJob job) {
        return jobCreated(job, ignoredUpdateTime(),
                startedCompactionRun(DEFAULT_TASK_ID, defaultStartTime()));
    }

    protected static CompactionJobStatus finishedStatusWithDefaults(CompactionJob job) {
        return finishedStatusWithDefaults(job, defaultSummary());
    }

    protected static CompactionJobStatus finishedStatusWithDefaults(CompactionJob job, RecordsProcessedSummary summary) {
        return jobCreated(job, ignoredUpdateTime(),
                finishedCompactionRun(DEFAULT_TASK_ID, summary));
    }

    protected static CompactionJobStatus finishedUncommittedStatusWithDefaults(CompactionJob job) {
        return jobCreated(job, ignoredUpdateTime(),
                ProcessRun.builder().taskId(DEFAULT_TASK_ID)
                        .startedStatus(compactionStartedStatus(defaultStartTime()))
                        .finishedStatus(compactionFinishedStatusUncommitted(defaultSummary()))
                        .build());
    }

    protected static CompactionJobStatus finishedThenCommittedStatusWithDefaults(CompactionJob job) {
        return jobCreated(job, ignoredUpdateTime(),
                ProcessRun.builder().taskId(DEFAULT_TASK_ID)
                        .startedStatus(compactionStartedStatus(defaultStartTime()))
                        .finishedStatus(compactionFinishedStatusUncommitted(defaultSummary()))
                        .statusUpdate(compactionCommittedStatus(defaultCommitTime()))
                        .build());
    }

    protected static CompactionJobStatus failedStatusWithDefaults(CompactionJob job, List<String> failureReasons) {
        return failedStatusWithDefaults(job, defaultRunTime(), failureReasons);
    }

    protected static CompactionJobStatus failedStatusWithDefaults(CompactionJob job, ProcessRunTime runTime, List<String> failureReasons) {
        return jobCreated(job, ignoredUpdateTime(),
                failedCompactionRun(DEFAULT_TASK_ID, runTime, failureReasons));
    }

    protected CompactionJobStatus getJobStatus(String jobId) {
        return getJobStatus(store, jobId);
    }

    protected CompactionJobStatus getJobStatus(CompactionJobStatusStore store, String jobId) {
        return store.getJob(jobId).orElseThrow(() -> new IllegalStateException("Job not found: " + jobId));
    }

    protected List<CompactionJobStatus> getAllJobStatuses() {
        return store.getAllJobs(tableId);
    }
}

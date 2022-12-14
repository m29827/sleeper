/*
 * Copyright 2022 Crown Copyright
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
package sleeper.compaction.status.store.job;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sleeper.compaction.job.CompactionJob;
import sleeper.compaction.job.status.CompactionJobCreatedStatus;
import sleeper.compaction.job.status.CompactionJobStartedStatus;
import sleeper.compaction.job.status.CompactionJobStatus;
import sleeper.core.record.process.RecordsProcessed;
import sleeper.core.record.process.RecordsProcessedSummary;
import sleeper.core.record.process.status.ProcessFinishedStatus;
import sleeper.core.record.process.status.ProcessStatusUpdate;
import sleeper.core.record.process.status.ProcessStatusUpdateRecord;
import sleeper.dynamodb.tools.DynamoDBRecordBuilder;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static sleeper.dynamodb.tools.DynamoDBAttributes.getInstantAttribute;
import static sleeper.dynamodb.tools.DynamoDBAttributes.getIntAttribute;
import static sleeper.dynamodb.tools.DynamoDBAttributes.getLongAttribute;
import static sleeper.dynamodb.tools.DynamoDBAttributes.getStringAttribute;

public class DynamoDBCompactionJobStatusFormat {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamoDBCompactionJobStatusFormat.class);

    private DynamoDBCompactionJobStatusFormat() {
    }

    public static final String JOB_ID = "JobId";
    public static final String UPDATE_TIME = "UpdateTime";
    public static final String UPDATE_TYPE = "UpdateType";
    public static final String TABLE_NAME = "TableName";
    public static final String PARTITION_ID = "PartitionId";
    public static final String INPUT_FILES_COUNT = "InputFilesCount";
    public static final String SPLIT_TO_PARTITION_IDS = "SplitToPartitionIds";
    public static final String START_TIME = "StartTime";
    public static final String FINISH_TIME = "FinishTime";
    public static final String RECORDS_READ = "RecordsRead";
    public static final String RECORDS_WRITTEN = "RecordsWritten";
    public static final String TASK_ID = "TaskId";
    public static final String EXPIRY_DATE = "ExpiryDate";
    public static final String UPDATE_TYPE_CREATED = "created";
    public static final String UPDATE_TYPE_STARTED = "started";
    public static final String UPDATE_TYPE_FINISHED = "finished";

    public static Map<String, AttributeValue> createJobCreatedRecord(CompactionJob job, Long timeToLive) {
        return createJobRecord(job, UPDATE_TYPE_CREATED, timeToLive)
                .string(PARTITION_ID, job.getPartitionId())
                .number(INPUT_FILES_COUNT, job.getInputFiles().size())
                .apply(builder -> {
                    if (job.isSplittingJob()) {
                        builder.string(SPLIT_TO_PARTITION_IDS, String.join(", ", job.getChildPartitions()));
                    }
                }).build();
    }

    public static Map<String, AttributeValue> createJobStartedRecord(CompactionJob job, Instant startTime, String taskId, Long timeToLive) {
        return createJobRecord(job, UPDATE_TYPE_STARTED, timeToLive)
                .number(START_TIME, startTime.toEpochMilli())
                .string(TASK_ID, taskId)
                .build();
    }

    public static Map<String, AttributeValue> createJobFinishedRecord(CompactionJob job, RecordsProcessedSummary summary, String taskId, Long timeToLive) {
        return createJobRecord(job, UPDATE_TYPE_FINISHED, timeToLive)
                .number(START_TIME, summary.getStartTime().toEpochMilli())
                .string(TASK_ID, taskId)
                .number(FINISH_TIME, summary.getFinishTime().toEpochMilli())
                .number(RECORDS_READ, summary.getLinesRead())
                .number(RECORDS_WRITTEN, summary.getLinesWritten())
                .build();
    }

    private static DynamoDBRecordBuilder createJobRecord(CompactionJob job, String updateType, Long timeToLive) {
        Long timeNow = Instant.now().toEpochMilli();
        return new DynamoDBRecordBuilder()
                .string(JOB_ID, job.getId())
                .string(TABLE_NAME, job.getTableName())
                .number(UPDATE_TIME, timeNow)
                .string(UPDATE_TYPE, updateType)
                .number(EXPIRY_DATE, timeNow + timeToLive);
    }

    public static Stream<CompactionJobStatus> streamJobStatuses(List<Map<String, AttributeValue>> items) {
        return CompactionJobStatus.streamFrom(items.stream()
                .map(DynamoDBCompactionJobStatusFormat::getStatusUpdateRecord));
    }

    private static ProcessStatusUpdateRecord getStatusUpdateRecord(Map<String, AttributeValue> item) {
        return new ProcessStatusUpdateRecord(
                getStringAttribute(item, JOB_ID),
                getInstantAttribute(item, EXPIRY_DATE),
                getStatusUpdate(item),
                getStringAttribute(item, TASK_ID));
    }

    private static ProcessStatusUpdate getStatusUpdate(Map<String, AttributeValue> item) {
        switch (getStringAttribute(item, UPDATE_TYPE)) {
            case UPDATE_TYPE_CREATED:
                return CompactionJobCreatedStatus.builder()
                        .updateTime(getInstantAttribute(item, UPDATE_TIME))
                        .partitionId(getStringAttribute(item, PARTITION_ID))
                        .childPartitionIds(getChildPartitionIds(item))
                        .inputFilesCount(getIntAttribute(item, INPUT_FILES_COUNT, 0))
                        .build();
            case UPDATE_TYPE_STARTED:
                return CompactionJobStartedStatus.startAndUpdateTime(
                        getInstantAttribute(item, START_TIME),
                        getInstantAttribute(item, UPDATE_TIME));
            case UPDATE_TYPE_FINISHED:
                return ProcessFinishedStatus.updateTimeAndSummary(
                        getInstantAttribute(item, UPDATE_TIME),
                        new RecordsProcessedSummary(new RecordsProcessed(
                                getLongAttribute(item, RECORDS_READ, 0),
                                getLongAttribute(item, RECORDS_WRITTEN, 0)),
                                getInstantAttribute(item, START_TIME),
                                getInstantAttribute(item, FINISH_TIME)));
            default:
                LOGGER.warn("Found record with unrecognised update type: {}", item);
                throw new IllegalArgumentException("Found record with unrecognised update type");
        }
    }

    private static List<String> getChildPartitionIds(Map<String, AttributeValue> item) {
        String string = getStringAttribute(item, SPLIT_TO_PARTITION_IDS);
        if (string == null) {
            return Collections.emptyList();
        }
        return Arrays.asList(string.split(", "));
    }
}
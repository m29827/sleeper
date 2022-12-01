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
package sleeper.compaction.status.task;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sleeper.compaction.task.CompactionTaskFinishedStatus;
import sleeper.compaction.task.CompactionTaskStatus;
import sleeper.compaction.task.CompactionTaskStatusesBuilder;
import sleeper.dynamodb.tools.DynamoDBRecordBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static sleeper.dynamodb.tools.DynamoDBAttributes.getInstantAttribute;
import static sleeper.dynamodb.tools.DynamoDBAttributes.getIntAttribute;
import static sleeper.dynamodb.tools.DynamoDBAttributes.getLongAttribute;
import static sleeper.dynamodb.tools.DynamoDBAttributes.getNumberAttribute;
import static sleeper.dynamodb.tools.DynamoDBAttributes.getStringAttribute;

public class DynamoDBCompactionTaskStatusFormat {
    private static final Logger LOGGER = LoggerFactory.getLogger(DynamoDBCompactionTaskStatusFormat.class);

    private DynamoDBCompactionTaskStatusFormat() {
    }

    public static final String TASK_ID = "TaskId";
    public static final String UPDATE_TYPE = "UpdateType";
    public static final String START_TIME = "StartTime";
    public static final String UPDATE_TIME = "UpdateTime";
    public static final String FINISH_TIME = "FinishTime";
    public static final String DURATION = "Duration";
    public static final String NUMBER_OF_JOBS = "NumberOfJobs";
    public static final String LINES_READ = "LinesRead";
    public static final String LINES_WRITTEN = "LinesWritten";
    public static final String READ_RATE = "ReadRate";
    public static final String WRITE_RATE = "WriteRate";
    public static final String EXPIRY_DATE = "ExpiryDate";

    public static final String STARTED = "started";
    public static final String FINISHED = "finished";

    public static Map<String, AttributeValue> createTaskStartedRecord(CompactionTaskStatus taskStatus, Long timeToLive) {
        return createTaskRecord(taskStatus, STARTED, timeToLive)
                .number(START_TIME, taskStatus.getStartedStatus().getStartTime().toEpochMilli())
                .build();
    }

    public static Map<String, AttributeValue> createTaskFinishedRecord(CompactionTaskStatus taskStatus, Long timeToLive) {
        return createTaskRecord(taskStatus, FINISHED, timeToLive)
                .number(START_TIME, taskStatus.getStartedStatus().getStartTime().toEpochMilli())
                .number(FINISH_TIME, taskStatus.getFinishedStatus().getFinishTime().toEpochMilli())
                .number(DURATION, taskStatus.getFinishedStatus().getTotalRuntimeInSeconds())
                .number(NUMBER_OF_JOBS, taskStatus.getFinishedStatus().getTotalJobRuns())
                .number(LINES_READ, taskStatus.getFinishedStatus().getTotalRecordsRead())
                .number(LINES_WRITTEN, taskStatus.getFinishedStatus().getTotalRecordsWritten())
                .number(READ_RATE, taskStatus.getFinishedStatus().getRecordsReadPerSecond())
                .number(WRITE_RATE, taskStatus.getFinishedStatus().getRecordsWrittenPerSecond())
                .build();
    }

    private static DynamoDBRecordBuilder createTaskRecord(CompactionTaskStatus taskStatus, String updateType, Long timeToLive) {
        Long timeNow = Instant.now().toEpochMilli();
        return new DynamoDBRecordBuilder()
                .string(TASK_ID, taskStatus.getTaskId())
                .number(UPDATE_TIME, timeNow)
                .string(UPDATE_TYPE, updateType)
                .number(EXPIRY_DATE, timeNow + timeToLive);
    }

    public static Stream<CompactionTaskStatus> streamTaskStatuses(List<Map<String, AttributeValue>> items) {
        CompactionTaskStatusesBuilder builder = new CompactionTaskStatusesBuilder();
        items.forEach(item -> addStatusUpdate(item, builder));
        return builder.stream();
    }

    private static void addStatusUpdate(Map<String, AttributeValue> item, CompactionTaskStatusesBuilder builder) {
        String taskId = getStringAttribute(item, TASK_ID);
        switch (getStringAttribute(item, UPDATE_TYPE)) {
            case STARTED:
                builder.taskStarted(taskId, getInstantAttribute(item, START_TIME))
                        .expiryDate(taskId, getInstantAttribute(item, EXPIRY_DATE));
                break;
            case FINISHED:
                builder.taskFinished(taskId, CompactionTaskFinishedStatus.builder()
                                .finishTime(getInstantAttribute(item, FINISH_TIME))
                                .totalRuntimeInSeconds(Double.parseDouble(getNumberAttribute(item, DURATION)))
                                .totalJobRuns(getIntAttribute(item, NUMBER_OF_JOBS, 0))
                                .totalRecordsRead(getLongAttribute(item, LINES_READ, 0))
                                .totalRecordsWritten(getLongAttribute(item, LINES_WRITTEN, 0))
                                .recordsReadPerSecond(Double.parseDouble(getNumberAttribute(item, READ_RATE)))
                                .recordsWrittenPerSecond(Double.parseDouble(getNumberAttribute(item, WRITE_RATE)))
                                .build())
                        .expiryDate(taskId, getInstantAttribute(item, EXPIRY_DATE));
                break;
            default:
                LOGGER.warn("Found record with unrecognised update type: {}", item);
        }
    }
}

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

package sleeper.ingest.job.status;

import sleeper.ingest.job.IngestJob;

import java.time.Instant;
import java.util.Objects;

public class IngestJobStartedEvent {
    private final String jobId;
    private final String tableName;
    private final int fileCount;
    private final String jobRunId;
    private final String taskId;
    private final Instant startTime;
    private final boolean startOfRun;

    private IngestJobStartedEvent(Builder builder) {
        jobId = builder.jobId;
        tableName = builder.tableName;
        fileCount = builder.fileCount;
        jobRunId = builder.jobRunId;
        taskId = Objects.requireNonNull(builder.taskId, "taskId must not be null");
        startTime = Objects.requireNonNull(builder.startTime, "startTime must not be null");
        startOfRun = builder.startOfRun;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static IngestJobStartedEvent ingestJobStarted(String taskId, IngestJob job, Instant startTime) {
        return ingestJobStarted(job, startTime).taskId(taskId).build();
    }

    public static Builder ingestJobStarted(IngestJob job, Instant startTime) {
        return builder()
                .job(job)
                .startTime(startTime)
                .startOfRun(true);
    }

    public static Builder validatedIngestJobStarted(IngestJob job, Instant startTime) {
        return builder()
                .job(job)
                .startTime(startTime)
                .startOfRun(false);
    }

    public String getJobId() {
        return jobId;
    }

    public String getTableName() {
        return tableName;
    }

    public int getFileCount() {
        return fileCount;
    }

    public String getJobRunId() {
        return jobRunId;
    }

    public String getTaskId() {
        return taskId;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public boolean isStartOfRun() {
        return startOfRun;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        IngestJobStartedEvent that = (IngestJobStartedEvent) o;
        return fileCount == that.fileCount && startOfRun == that.startOfRun && Objects.equals(jobId, that.jobId) && Objects.equals(tableName, that.tableName) && Objects.equals(jobRunId, that.jobRunId) && Objects.equals(taskId, that.taskId) && Objects.equals(startTime, that.startTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobId, tableName, fileCount, jobRunId, taskId, startTime, startOfRun);
    }

    @Override
    public String toString() {
        return "IngestJobStartedEvent{" +
                "jobId='" + jobId + '\'' +
                ", tableName='" + tableName + '\'' +
                ", fileCount=" + fileCount +
                ", jobRunId='" + jobRunId + '\'' +
                ", taskId='" + taskId + '\'' +
                ", startTime=" + startTime +
                ", startOfRun=" + startOfRun +
                '}';
    }

    public static final class Builder {
        private String jobId;
        private String tableName;
        private int fileCount;
        private String jobRunId;
        private String taskId;
        private Instant startTime;
        private boolean startOfRun;

        private Builder() {
        }

        public Builder job(IngestJob job) {
            return jobId(job.getId())
                    .tableName(job.getTableName())
                    .fileCount(job.getFileCount());
        }

        public Builder jobId(String jobId) {
            this.jobId = jobId;
            return this;
        }

        public Builder tableName(String tableName) {
            this.tableName = tableName;
            return this;
        }

        public Builder fileCount(int fileCount) {
            this.fileCount = fileCount;
            return this;
        }

        public Builder jobRunId(String jobRunId) {
            this.jobRunId = jobRunId;
            return this;
        }

        public Builder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        public Builder startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder startOfRun(boolean startOfRun) {
            this.startOfRun = startOfRun;
            return this;
        }

        public IngestJobStartedEvent build() {
            return new IngestJobStartedEvent(this);
        }
    }
}

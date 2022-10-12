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

package sleeper.compaction.task;

import java.time.Instant;
import java.util.Objects;

public class CompactionTaskStatus {
    private final String taskId;
    private final CompactionTaskStartedStatus startedStatus;
    private final CompactionTaskFinishedStatus finishedStatus;
    private final Instant expiryDate; // Set by database (null before status is saved)

    private CompactionTaskStatus(Builder builder) {
        taskId = Objects.requireNonNull(builder.taskId, "taskId must not be null");
        startedStatus = Objects.requireNonNull(builder.startedStatus, "taskId must not be null");
        finishedStatus = builder.finishedStatus;
        expiryDate = builder.expiryDate;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getTaskId() {
        return taskId;
    }

    public CompactionTaskStartedStatus getStartedStatus() {
        return startedStatus;
    }

    public CompactionTaskFinishedStatus getFinishedStatus() {
        return finishedStatus;
    }

    public boolean isInPeriod(Instant startTime, Instant endTime) {
        return startTime.isBefore(getLastTime())
                && endTime.isAfter(getStartTime());
    }

    public Instant getStartTime() {
        return startedStatus.getStartTime();
    }

    public Instant getFinishTime() {
        if (isFinished()) {
            return finishedStatus.getFinishTime();
        } else {
            return null;
        }
    }

    public Instant getLastTime() {
        if (isFinished()) {
            return finishedStatus.getFinishTime();
        } else {
            return startedStatus.getStartTime();
        }
    }

    public boolean isFinished() {
        return finishedStatus != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CompactionTaskStatus that = (CompactionTaskStatus) o;
        return Objects.equals(taskId, that.taskId)
                && Objects.equals(startedStatus, that.startedStatus)
                && Objects.equals(finishedStatus, that.finishedStatus)
                && Objects.equals(expiryDate, that.expiryDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskId, startedStatus, finishedStatus, expiryDate);
    }

    @Override
    public String toString() {
        return "CompactionTaskStatus{" +
                "taskId='" + taskId + '\'' +
                ", startedStatus=" + startedStatus +
                ", finishedStatus=" + finishedStatus +
                ", expiryDate=" + expiryDate +
                '}';
    }

    public static final class Builder {
        private String taskId;
        private CompactionTaskStartedStatus startedStatus;
        private CompactionTaskFinishedStatus finishedStatus;
        private Instant expiryDate;

        private Builder() {
        }

        public Builder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        public Builder startedStatus(CompactionTaskStartedStatus startedStatus) {
            this.startedStatus = startedStatus;
            return this;
        }

        public Builder started(Instant startTime) {
            return startedStatus(CompactionTaskStartedStatus.builder()
                    .startTime(startTime)
                    .build());
        }

        public Builder finishedStatus(CompactionTaskFinishedStatus finishedStatus) {
            this.finishedStatus = finishedStatus;
            return this;
        }

        public Builder expiryDate(Instant expiryDate) {
            this.expiryDate = expiryDate;
            return this;
        }

        public Builder finished(CompactionTaskFinishedStatus.Builder taskFinishedBuilder, long finishTime) {
            return finished(taskFinishedBuilder, Instant.ofEpochMilli(finishTime));
        }

        public Builder finished(CompactionTaskFinishedStatus.Builder taskFinishedBuilder, Instant finishTime) {
            return finishedStatus(taskFinishedBuilder.finish(
                            startedStatus.getStartTime(), finishTime)
                    .build());
        }

        public String getTaskId() {
            return this.taskId;
        }

        public CompactionTaskStatus build() {
            return new CompactionTaskStatus(this);
        }
    }
}
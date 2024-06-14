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
package sleeper.ingest.job.commit;

import sleeper.core.statestore.FileReference;
import sleeper.ingest.job.IngestJob;

import java.util.List;
import java.util.Objects;

/**
 * A request to commit the results of an ingest job to the state store and job status store.
 */
public class IngestJobCommitRequest {
    private final IngestJob ingestJob;
    private final String taskId;
    private final String jobRunId;
    private final List<FileReference> fileReferences;

    public IngestJobCommitRequest(IngestJob job, String taskId, String jobRunId, List<FileReference> fileReferences) {
        this.ingestJob = job;
        this.taskId = taskId;
        this.jobRunId = jobRunId;
        this.fileReferences = fileReferences;
    }

    public IngestJob getJob() {
        return ingestJob;
    }

    public String getTaskId() {
        return taskId;
    }

    public List<FileReference> getFileReferences() {
        return fileReferences;
    }

    @Override
    public int hashCode() {
        return Objects.hash(ingestJob, taskId, jobRunId, fileReferences);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof IngestJobCommitRequest)) {
            return false;
        }
        IngestJobCommitRequest other = (IngestJobCommitRequest) obj;
        return Objects.equals(ingestJob, other.ingestJob)
                && Objects.equals(taskId, other.taskId)
                && Objects.equals(fileReferences, other.fileReferences)
                && Objects.equals(jobRunId, other.jobRunId);
    }

    @Override
    public String toString() {
        return "IngestJobCommitRequest{job=" + ingestJob +
                ", taskId=" + taskId +
                ", fileReferences=" + fileReferences +
                ", jobRunId=" + jobRunId + "}";
    }

}

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

package sleeper.ingest.status.store.job;

import org.junit.jupiter.api.Test;

import sleeper.ingest.job.IngestJob;
import sleeper.ingest.status.store.testutils.DynamoDBIngestJobStatusStoreTestBase;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static sleeper.ingest.job.status.IngestJobStatusTestData.acceptedRunWhichStarted;
import static sleeper.ingest.job.status.IngestJobStatusTestData.jobStatus;
import static sleeper.ingest.job.status.IngestJobStatusTestData.rejectedRun;

public class StoreIngestJobValidatedIT extends DynamoDBIngestJobStatusStoreTestBase {
    @Test
    void shouldReportIngestJobStartedWithValidation() {
        // Given
        IngestJob job = jobWithFiles("file1");
        Instant validationTime = Instant.parse("2022-12-14T13:50:12.001Z");
        Instant startedTime = Instant.parse("2022-12-14T13:51:12.001Z");

        // When
        store.jobAccepted(DEFAULT_RUN_ID, job, validationTime);
        store.jobStartedWithValidation(DEFAULT_RUN_ID, DEFAULT_TASK_ID, job, startedTime);

        // Then
        assertThat(getAllJobStatuses())
                .usingRecursiveFieldByFieldElementComparator(IGNORE_UPDATE_TIMES)
                .containsExactly(jobStatus(job,
                        acceptedRunWhichStarted(job, DEFAULT_TASK_ID,
                                validationTime, startedTime)));
    }

    @Test
    void shouldReportIngestJobWithOneValidationFailure() {
        // Given
        IngestJob job = jobWithFiles("file1");
        Instant validationTime = Instant.parse("2022-12-14T13:50:12.001Z");

        // When
        store.jobRejected(DEFAULT_RUN_ID, job, validationTime, List.of("Test failure"));

        // Then
        assertThat(getAllJobStatuses())
                .usingRecursiveFieldByFieldElementComparator(IGNORE_UPDATE_TIMES)
                .containsExactly(jobStatus(job,
                        rejectedRun(validationTime, "Test failure")));
    }

    @Test
    void shouldReportJobWithMultipleValidationFailures() {
        // Given
        IngestJob job = jobWithFiles("file1");
        Instant validationTime = Instant.parse("2022-12-14T13:50:12.001Z");

        // When
        store.jobRejected(DEFAULT_TASK_ID, job, validationTime,
                List.of("Test validation reason 1", "Test validation reason 2"));

        // Then
        assertThat(store.getAllJobs(tableName))
                .usingRecursiveFieldByFieldElementComparator(IGNORE_UPDATE_TIMES)
                .containsExactly(jobStatus(job,
                        rejectedRun(validationTime,
                                List.of("Test validation reason 1", "Test validation reason 2"))));
    }
}

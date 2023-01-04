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
package sleeper.ingest.status.store.job;

import org.junit.Test;
import sleeper.ingest.job.IngestJob;
import sleeper.ingest.status.store.testutils.DynamoDBIngestJobStatusStoreTestBase;

import java.time.Instant;
import java.time.Period;

import static org.assertj.core.api.Assertions.assertThat;
import static sleeper.ingest.job.status.IngestJobStatusTestData.finishedIngestJob;
import static sleeper.ingest.job.status.IngestJobStatusTestData.startedIngestJob;

public class QueryIngestJobStatusByPeriodIT extends DynamoDBIngestJobStatusStoreTestBase {

    @Test
    public void shouldReturnIngestJobsInPeriod() {
        // Given
        IngestJob job1 = jobWithFiles("file1");
        IngestJob job2 = jobWithFiles("file2");
        Instant startedTime1 = Instant.now();
        Instant startedTime2 = Instant.now();

        // When
        store.jobStarted(DEFAULT_TASK_ID, job1, startedTime1);
        store.jobStarted(DEFAULT_TASK_ID, job2, startedTime2);

        // Then
        Instant epochStart = Instant.ofEpochMilli(0);
        Instant farFuture = epochStart.plus(Period.ofDays(999999999));
        assertThat(store.getJobsInTimePeriod(tableName, epochStart, farFuture))
                .usingRecursiveFieldByFieldElementComparator(IGNORE_UPDATE_TIMES)
                .containsExactly(
                        startedIngestJob(job2, DEFAULT_TASK_ID, startedTime2),
                        startedIngestJob(job1, DEFAULT_TASK_ID, startedTime1));
    }

    @Test
    public void shouldExcludeIngestJobOutsidePeriod() {
        // Given
        IngestJob job = jobWithFiles("file");
        Instant startedTime = Instant.now();

        // When
        store.jobStarted(DEFAULT_TASK_ID, job, startedTime);

        // Then
        Instant periodStart = Instant.now().plus(Period.ofDays(1));
        Instant periodEnd = periodStart.plus(Period.ofDays(1));
        assertThat(store.getJobsInTimePeriod(tableName, periodStart, periodEnd)).isEmpty();
    }

    @Test
    public void shouldExcludeIngestJobInOtherTable() {
        // Given
        IngestJob job1 = jobWithFiles("file1");
        IngestJob job2 = jobWithTableAndFiles("other-table", "file2");
        Instant startedTime1 = Instant.now();
        Instant startedTime2 = Instant.now();

        // When
        store.jobStarted(DEFAULT_TASK_ID, job1, startedTime1);
        store.jobStarted(DEFAULT_TASK_ID, job2, startedTime2);

        // Then
        Instant epochStart = Instant.ofEpochMilli(0);
        Instant farFuture = epochStart.plus(Period.ofDays(999999999));
        assertThat(store.getJobsInTimePeriod(tableName, epochStart, farFuture))
                .usingRecursiveFieldByFieldElementComparator(IGNORE_UPDATE_TIMES)
                .containsExactly(startedIngestJob(job1, DEFAULT_TASK_ID, startedTime1));
    }

    @Test
    public void shouldIncludeFinishedStatusUpdateOutsidePeriod() throws Exception {
        // Given
        IngestJob job = jobWithFiles("file");
        Instant periodStart = Instant.now().minus(Period.ofDays(1));
        Instant startedTime = Instant.now();

        // When
        store.jobStarted(DEFAULT_TASK_ID, job, startedTime);
        Thread.sleep(1);
        Instant periodEnd = Instant.now();
        Thread.sleep(1);
        Instant finishedTime = Instant.now();
        store.jobFinished(DEFAULT_TASK_ID, job, defaultSummary(startedTime, finishedTime));

        // Then
        assertThat(store.getJobsInTimePeriod(tableName, periodStart, periodEnd))
                .usingRecursiveFieldByFieldElementComparator(IGNORE_UPDATE_TIMES)
                .containsExactly(finishedIngestJob(job, DEFAULT_TASK_ID, defaultSummary(startedTime, finishedTime)));
    }

}
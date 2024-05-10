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
package sleeper.compaction.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sleeper.compaction.job.commit.TimedOutWaitingForFileAssignmentsException;
import sleeper.core.statestore.FileReference;
import sleeper.core.statestore.StateStore;
import sleeper.core.statestore.StateStoreException;
import sleeper.core.statestore.exception.FileReferenceNotAssignedToJobException;
import sleeper.core.util.ExponentialBackoffWithJitter;
import sleeper.core.util.ExponentialBackoffWithJitter.WaitRange;

public class StateStoreUpdate {
    public static final int JOB_ASSIGNMENT_WAIT_ATTEMPTS = 10;
    public static final WaitRange JOB_ASSIGNMENT_WAIT_RANGE = WaitRange.firstAndMaxWaitCeilingSecs(2, 60);

    private StateStoreUpdate() {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(CompactionJobStatusStore.class);

    public static void updateStateStoreSuccess(
            CompactionJob job,
            long recordsWritten,
            StateStore stateStore) throws StateStoreException, InterruptedException {
        updateStateStoreSuccess(job, recordsWritten, stateStore,
                JOB_ASSIGNMENT_WAIT_ATTEMPTS,
                new ExponentialBackoffWithJitter(JOB_ASSIGNMENT_WAIT_RANGE));
    }

    public static void updateStateStoreSuccess(
            CompactionJob job,
            long recordsWritten,
            StateStore stateStore,
            int jobAssignmentWaitAttempts,
            ExponentialBackoffWithJitter jobAssignmentWaitBackoff) throws StateStoreException, InterruptedException {
        FileReference fileReference = FileReference.builder()
                .filename(job.getOutputFile())
                .partitionId(job.getPartitionId())
                .numberOfRecords(recordsWritten)
                .countApproximate(false)
                .onlyContainsDataForThisPartition(true)
                .build();

        // Compaction jobs are sent for execution before updating the state store to assign the input files to the job.
        // Sometimes the compaction can finish before the job assignment is finished. We wait for the job assignment
        // rather than immediately failing the job run.
        FileReferenceNotAssignedToJobException failure = null;
        for (int attempts = 0; attempts < jobAssignmentWaitAttempts; attempts++) {
            jobAssignmentWaitBackoff.waitBeforeAttempt(attempts);
            try {
                stateStore.atomicallyReplaceFileReferencesWithNewOne(job.getId(), job.getPartitionId(), job.getInputFiles(), fileReference);
                LOGGER.debug("Updated file references in state store");
                return;
            } catch (FileReferenceNotAssignedToJobException e) {
                LOGGER.warn("Job not yet assigned to input files on attempt {} of {}: {}",
                        attempts + 1, jobAssignmentWaitAttempts, e.getMessage());
                failure = e;
            }
        }
        throw new TimedOutWaitingForFileAssignmentsException(failure);
    }
}

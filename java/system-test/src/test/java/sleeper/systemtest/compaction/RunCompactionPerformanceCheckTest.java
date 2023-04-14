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

package sleeper.systemtest.compaction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

public class RunCompactionPerformanceCheckTest {
    private final CompactionPerformanceChecker checker = createCheckerInMemory();

    @DisplayName("Compaction job count assertions")
    @Nested
    class CheckCompactionJobCount {
        @Test
        void shouldNotThrowExceptionWhenCompactionJobCountWasExpected() {
            // Given
            RunCompactionPerformanceCheck runCheck = runCheckBuilder()
                    .expectedNumOfJobs(1)
                    .expectedNumOfRecordsInRoot(100)
                    .previousReadRate(0.1)
                    .previousWriteRate(0.1)
                    .build();

            // When/Then
            assertThatCode(runCheck::run)
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldThrowExceptionWhenCompactionJobCountWasNotExpected() {
            // Given
            RunCompactionPerformanceCheck runCheck = runCheckBuilder()
                    .expectedNumOfJobs(2)
                    .expectedNumOfRecordsInRoot(100)
                    .previousReadRate(0.1)
                    .previousWriteRate(0.1)
                    .build();

            // When/Then
            assertThatThrownBy(runCheck::run)
                    .isInstanceOf(CompactionPerformanceChecker.CheckFailedException.class)
                    .hasMessageContaining("Actual number of compaction jobs");
        }
    }

    @DisplayName("Records in root partition assertions")
    @Nested
    class CheckRecordsInRootPartition {
        @Test
        void shouldNotThrowExceptionWhenRecordsInRootPartitionIsExpected() {
            // Given
            RunCompactionPerformanceCheck runCheck = runCheckBuilder()
                    .expectedNumOfJobs(1)
                    .expectedNumOfRecordsInRoot(100)
                    .previousReadRate(0.1)
                    .previousWriteRate(0.1)
                    .build();

            // When/Then
            assertThatCode(runCheck::run)
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldThrowExceptionWhenRecordsInRootPartitionIsNotExpected() {
            // Given
            RunCompactionPerformanceCheck runCheck = runCheckBuilder()
                    .expectedNumOfJobs(1)
                    .expectedNumOfRecordsInRoot(101)
                    .previousReadRate(0.1)
                    .previousWriteRate(0.1)
                    .build();

            // When/Then
            assertThatThrownBy(runCheck::run)
                    .isInstanceOf(CompactionPerformanceChecker.CheckFailedException.class)
                    .hasMessageContaining("Actual number of records in root partition");
        }
    }

    @DisplayName("Average read/write rate assertions")
    @Nested
    class CheckAverageRates {
        @Test
        void shouldNotThrowExceptionWhenReadRateIsBetterThanPreviousRate() {
            // Given
            RunCompactionPerformanceCheck runCheck = runCheckBuilder()
                    .expectedNumOfJobs(1)
                    .expectedNumOfRecordsInRoot(100)
                    .previousReadRate(0.1)
                    .previousWriteRate(0.1)
                    .build();

            // When/Then
            assertThatCode(runCheck::run)
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldThrowExceptionWhenReadRateIsWorseThanPreviousRate() {
            // Given
            RunCompactionPerformanceCheck runCheck = runCheckBuilder()
                    .expectedNumOfJobs(1)
                    .expectedNumOfRecordsInRoot(100)
                    .previousReadRate(0.5)
                    .previousWriteRate(0.1)
                    .build();

            // When/Then
            assertThatThrownBy(runCheck::run)
                    .isInstanceOf(CompactionPerformanceChecker.CheckFailedException.class)
                    .hasMessageContaining("Read rate");
        }

        @Test
        void shouldNotThrowExceptionWhenWriteRateIsBetterThanPreviousRate() {
            // Given
            RunCompactionPerformanceCheck runCheck = runCheckBuilder()
                    .expectedNumOfJobs(1)
                    .expectedNumOfRecordsInRoot(100)
                    .previousReadRate(0.1)
                    .previousWriteRate(0.1)
                    .build();

            // When/Then
            assertThatCode(runCheck::run)
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldThrowExceptionWhenWriteRateIsWorseThanPreviousRate() {
            // Given
            RunCompactionPerformanceCheck runCheck = runCheckBuilder()
                    .expectedNumOfJobs(1)
                    .expectedNumOfRecordsInRoot(100)
                    .previousReadRate(0.1)
                    .previousWriteRate(0.5)
                    .build();

            // When/Then
            assertThatThrownBy(runCheck::run)
                    .isInstanceOf(CompactionPerformanceChecker.CheckFailedException.class)
                    .hasMessageContaining("Write rate");
        }
    }

    private CompactionPerformanceChecker createCheckerInMemory() {
        return CompactionPerformanceCheckerInMemory.builder()
                .actualNumOfJobs(1)
                .actualNumOfRecordsInRoot(100)
                .actualReadRate(0.2)
                .actualWriteRate(0.2)
                .build();
    }

    private RunCompactionPerformanceCheck.Builder runCheckBuilder() {
        return RunCompactionPerformanceCheck.builder()
                .checker(checker);
    }
}

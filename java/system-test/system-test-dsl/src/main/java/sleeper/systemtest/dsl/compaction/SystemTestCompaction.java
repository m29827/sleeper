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

package sleeper.systemtest.dsl.compaction;

import sleeper.core.util.PollWithRetries;
import sleeper.systemtest.dsl.SystemTestContext;
import sleeper.systemtest.dsl.SystemTestDrivers;
import sleeper.systemtest.dsl.util.WaitForJobs;

import java.time.Duration;
import java.util.List;

public class SystemTestCompaction {

    private final CompactionDriver driver;
    private final WaitForJobs waitForJobs;
    private List<String> lastJobIds;

    public SystemTestCompaction(SystemTestContext context, SystemTestDrivers drivers) {
        this.driver = drivers.compaction(context);
        this.waitForJobs = drivers.waitForCompaction(context);
    }

    public SystemTestCompaction createJobs() {
        lastJobIds = driver.createJobsGetIds();
        return this;
    }

    public SystemTestCompaction forceCreateJobs() {
        lastJobIds = driver.forceCreateJobsGetIds();
        return this;
    }

    public SystemTestCompaction splitAndCompactFiles() {
        forceCreateJobs().invokeTasks(1).waitForJobs(
                PollWithRetries.intervalAndPollingTimeout(Duration.ofSeconds(5), Duration.ofMinutes(30)));
        return this;
    }

    public SystemTestCompaction invokeTasks(int expectedTasks) {
        driver.invokeTasks(expectedTasks);
        return this;
    }

    public SystemTestCompaction waitForJobs() {
        waitForJobs.waitForJobs(lastJobIds);
        return this;
    }

    public SystemTestCompaction waitForJobs(PollWithRetries poll) {
        waitForJobs.waitForJobs(lastJobIds, poll);
        return this;
    }
}
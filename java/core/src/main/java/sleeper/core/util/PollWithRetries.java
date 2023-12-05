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
package sleeper.core.util;

import com.google.common.math.LongMath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.RoundingMode;
import java.time.Duration;
import java.util.Objects;
import java.util.function.BooleanSupplier;

public class PollWithRetries {
    private static final Logger LOGGER = LoggerFactory.getLogger(PollWithRetries.class);

    private final long pollIntervalMillis;
    private final int maxPolls;

    private PollWithRetries(long pollIntervalMillis, int maxPolls) {
        this.pollIntervalMillis = pollIntervalMillis;
        this.maxPolls = maxPolls;
    }

    public static PollWithRetries intervalAndMaxPolls(long pollIntervalMillis, int maxPolls) {
        return new PollWithRetries(pollIntervalMillis, maxPolls);
    }

    public static PollWithRetries intervalAndPollingTimeout(Duration pollInterval, Duration timeout) {
        long pollIntervalMillis = pollInterval.toMillis();
        long timeoutMillis = timeout.toMillis();
        return intervalAndMaxPolls(pollIntervalMillis,
                (int) LongMath.divide(timeoutMillis, pollIntervalMillis, RoundingMode.CEILING));
    }

    public static PollWithRetries noRetries() {
        return new PollWithRetries(0, 1);
    }

    public static PollWithRetries immediateRetries(int retries) {
        return new PollWithRetries(0, retries + 1);
    }

    public void pollUntil(String description, BooleanSupplier checkFinished) throws InterruptedException {
        int polls = 0;
        while (!checkFinished.getAsBoolean()) {
            polls++;
            if (polls >= maxPolls) {
                LOGGER.error("Timed out after {} tries waiting until {}", polls, description);
                throw new TimedOutException("Timed out after " + polls + " tries waiting until " + description);
            }
            Thread.sleep(pollIntervalMillis);
        }
    }

    public static class TimedOutException extends RuntimeException {
        private TimedOutException(String message) {
            super(message);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PollWithRetries that = (PollWithRetries) o;
        return pollIntervalMillis == that.pollIntervalMillis && maxPolls == that.maxPolls;
    }

    @Override
    public int hashCode() {
        return Objects.hash(pollIntervalMillis, maxPolls);
    }

    @Override
    public String toString() {
        return "PollWithRetries{" +
                "pollIntervalMillis=" + pollIntervalMillis +
                ", maxPolls=" + maxPolls +
                '}';
    }
}

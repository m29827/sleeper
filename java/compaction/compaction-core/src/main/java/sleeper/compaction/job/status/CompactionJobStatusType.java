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
package sleeper.compaction.job.status;

import sleeper.core.record.process.status.ProcessFailedStatus;
import sleeper.core.record.process.status.ProcessFinishedStatus;
import sleeper.core.record.process.status.ProcessStatusUpdate;

import java.util.stream.Stream;

public enum CompactionJobStatusType {
    PENDING(CompactionJobCreatedStatus.class, 1),
    FAILED(ProcessFailedStatus.class, 2),
    IN_PROGRESS(CompactionJobStartedStatus.class, 3),
    FINISHED(ProcessFinishedStatus.class, 4);

    private final Class<?> statusUpdateClass;
    private final int order;

    CompactionJobStatusType(Class<?> statusUpdateClass, int order) {
        this.statusUpdateClass = statusUpdateClass;
        this.order = order;
    }

    public int getOrder() {
        return order;
    }

    public static CompactionJobStatusType of(ProcessStatusUpdate update) {
        return Stream.of(values())
                .filter(type -> type.statusUpdateClass.isInstance(update))
                .findFirst().orElseThrow();
    }
}
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

package sleeper.ingest.batcher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class IngestBatcherStateStoreInMemory implements IngestBatcherStateStore {

    private final Map<IngestBatcherStateStoreKeyFields, FileIngestRequest> requests = new LinkedHashMap<>();

    @Override
    public void addFile(FileIngestRequest fileIngestRequest) {
        requests.put(new IngestBatcherStateStoreKeyFields(fileIngestRequest), fileIngestRequest);
    }

    @Override
    public void assignJob(String jobId, List<FileIngestRequest> filesInJob) {
        filesInJob.forEach(file -> {
            requests.remove(new IngestBatcherStateStoreKeyFields(file));
            requests.put(new IngestBatcherStateStoreKeyFields(file, jobId), file);
        });
    }

    @Override
    public List<FileIngestRequest> getAllFiles() {
        return new ArrayList<>(requests.values());
    }

    @Override
    public List<FileIngestRequest> getPendingFiles() {
        return requests.entrySet().stream()
                .filter(entry -> !entry.getKey().isAssignedToJob())
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }
}

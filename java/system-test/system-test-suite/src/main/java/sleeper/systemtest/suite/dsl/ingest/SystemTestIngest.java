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

package sleeper.systemtest.suite.dsl.ingest;

import sleeper.systemtest.drivers.ingest.AwsDirectIngestDriver;
import sleeper.systemtest.drivers.ingest.AwsIngestBatcherDriver;
import sleeper.systemtest.drivers.ingest.AwsIngestByQueueDriver;
import sleeper.systemtest.drivers.ingest.DirectEmrServerlessDriver;
import sleeper.systemtest.drivers.util.AwsWaitForJobs;
import sleeper.systemtest.drivers.util.SystemTestClients;
import sleeper.systemtest.dsl.ingest.DirectIngestDriver;
import sleeper.systemtest.dsl.ingest.IngestBatcherDriver;
import sleeper.systemtest.dsl.ingest.IngestByQueue;
import sleeper.systemtest.dsl.ingest.SystemTestDirectIngest;
import sleeper.systemtest.dsl.ingest.SystemTestIngestByQueue;
import sleeper.systemtest.dsl.ingest.SystemTestIngestToStateStore;
import sleeper.systemtest.dsl.ingest.SystemTestIngestType;
import sleeper.systemtest.dsl.instance.SleeperInstanceContext;
import sleeper.systemtest.dsl.sourcedata.IngestSourceFilesContext;
import sleeper.systemtest.dsl.util.WaitForJobs;

import java.nio.file.Path;

public class SystemTestIngest {
    private final SystemTestClients clients;
    private final SleeperInstanceContext instance;
    private final IngestSourceFilesContext sourceFiles;
    private final DirectIngestDriver directDriver;
    private final IngestByQueue byQueue;
    private final IngestBatcherDriver batcherDriver;
    private final WaitForJobs waitForIngest;
    private final WaitForJobs waitForBulkImport;

    public SystemTestIngest(
            SystemTestClients clients,
            SleeperInstanceContext instance,
            IngestSourceFilesContext sourceFiles) {
        this.clients = clients;
        this.instance = instance;
        this.sourceFiles = sourceFiles;
        this.directDriver = new AwsDirectIngestDriver(instance);
        this.byQueue = new IngestByQueue(instance, new AwsIngestByQueueDriver(clients));
        this.batcherDriver = new AwsIngestBatcherDriver(instance, sourceFiles, clients);
        this.waitForIngest = AwsWaitForJobs.forIngest(instance, clients.getDynamoDB());
        this.waitForBulkImport = AwsWaitForJobs.forBulkImport(instance, clients.getDynamoDB());
    }

    public SystemTestIngest setType(SystemTestIngestType type) {
        type.applyTo(instance);
        return this;
    }

    public SystemTestIngestBatcher batcher() {
        return new SystemTestIngestBatcher(this, batcherDriver);
    }

    public SystemTestDirectIngest direct(Path tempDir) {
        return new SystemTestDirectIngest(instance, directDriver, tempDir);
    }

    public SystemTestIngestToStateStore toStateStore() {
        return new SystemTestIngestToStateStore(instance, sourceFiles);
    }

    public SystemTestIngestByQueue byQueue() {
        return new SystemTestIngestByQueue(sourceFiles, byQueue, waitForIngest);
    }

    public SystemTestIngestByQueue bulkImportByQueue() {
        return new SystemTestIngestByQueue(sourceFiles, byQueue, waitForBulkImport);
    }

    public SystemTestDirectEmrServerless directEmrServerless() {
        return new SystemTestDirectEmrServerless(instance, sourceFiles,
                new DirectEmrServerlessDriver(instance,
                        clients.getS3(), clients.getDynamoDB(), clients.getEmrServerless()),
                waitForBulkImport);
    }
}

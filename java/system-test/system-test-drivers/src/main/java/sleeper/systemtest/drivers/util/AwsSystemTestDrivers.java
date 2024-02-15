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

package sleeper.systemtest.drivers.util;

import sleeper.systemtest.drivers.compaction.AwsCompactionDriver;
import sleeper.systemtest.drivers.compaction.AwsCompactionReportsDriver;
import sleeper.systemtest.drivers.ingest.AwsDataGenerationTasksDriver;
import sleeper.systemtest.drivers.ingest.AwsDirectIngestDriver;
import sleeper.systemtest.drivers.ingest.AwsIngestBatcherDriver;
import sleeper.systemtest.drivers.ingest.AwsIngestByQueueDriver;
import sleeper.systemtest.drivers.ingest.AwsIngestReportsDriver;
import sleeper.systemtest.drivers.ingest.AwsInvokeIngestTasksDriver;
import sleeper.systemtest.drivers.ingest.AwsPurgeQueueDriver;
import sleeper.systemtest.drivers.ingest.DirectEmrServerlessDriver;
import sleeper.systemtest.drivers.instance.AwsSleeperInstanceDriver;
import sleeper.systemtest.drivers.instance.AwsSleeperInstanceTablesDriver;
import sleeper.systemtest.drivers.instance.AwsSystemTestDeploymentDriver;
import sleeper.systemtest.drivers.metrics.AwsTableMetricsDriver;
import sleeper.systemtest.drivers.partitioning.AwsPartitionReportDriver;
import sleeper.systemtest.drivers.partitioning.AwsPartitionSplittingDriver;
import sleeper.systemtest.drivers.python.PythonBulkImportDriver;
import sleeper.systemtest.drivers.python.PythonIngestDriver;
import sleeper.systemtest.drivers.python.PythonIngestLocalFileDriver;
import sleeper.systemtest.drivers.python.PythonQueryDriver;
import sleeper.systemtest.drivers.query.DirectQueryDriver;
import sleeper.systemtest.drivers.query.S3ResultsDriver;
import sleeper.systemtest.drivers.query.SQSQueryDriver;
import sleeper.systemtest.drivers.sourcedata.AwsGeneratedIngestSourceFilesDriver;
import sleeper.systemtest.drivers.sourcedata.AwsIngestSourceFilesDriver;
import sleeper.systemtest.dsl.SystemTestContext;
import sleeper.systemtest.dsl.compaction.SystemTestCompaction;
import sleeper.systemtest.dsl.ingest.IngestByQueue;
import sleeper.systemtest.dsl.ingest.SystemTestIngest;
import sleeper.systemtest.dsl.instance.DeployedSystemTestResources;
import sleeper.systemtest.dsl.instance.SleeperInstanceDriver;
import sleeper.systemtest.dsl.instance.SleeperInstanceTablesDriver;
import sleeper.systemtest.dsl.instance.SystemTestDeploymentDriver;
import sleeper.systemtest.dsl.instance.SystemTestParameters;
import sleeper.systemtest.dsl.metrics.SystemTestMetrics;
import sleeper.systemtest.dsl.partitioning.PartitionSplittingDriver;
import sleeper.systemtest.dsl.python.SystemTestPythonApi;
import sleeper.systemtest.dsl.query.SystemTestQuery;
import sleeper.systemtest.dsl.reporting.SystemTestReporting;
import sleeper.systemtest.dsl.reporting.SystemTestReports;
import sleeper.systemtest.dsl.sourcedata.GeneratedIngestSourceFilesDriver;
import sleeper.systemtest.dsl.sourcedata.IngestSourceFilesDriver;
import sleeper.systemtest.dsl.sourcedata.SystemTestCluster;
import sleeper.systemtest.dsl.util.PurgeQueueDriver;
import sleeper.systemtest.dsl.util.SystemTestDrivers;

import java.nio.file.Path;

public class AwsSystemTestDrivers implements SystemTestDrivers {
    private final SystemTestClients clients = new SystemTestClients();

    @Override
    public SystemTestDeploymentDriver systemTestDeployment(SystemTestParameters parameters) {
        return new AwsSystemTestDeploymentDriver(parameters, clients);
    }

    @Override
    public SleeperInstanceDriver instance(SystemTestParameters parameters) {
        return new AwsSleeperInstanceDriver(parameters, clients);
    }

    @Override
    public SleeperInstanceTablesDriver tables(SystemTestParameters parameters) {
        return new AwsSleeperInstanceTablesDriver(clients);
    }

    @Override
    public GeneratedIngestSourceFilesDriver generatedSourceFiles(SystemTestParameters parameters, DeployedSystemTestResources systemTest) {
        return new AwsGeneratedIngestSourceFilesDriver(systemTest, clients.getS3V2());
    }

    @Override
    public IngestSourceFilesDriver sourceFilesDriver(SystemTestContext context) {
        return new AwsIngestSourceFilesDriver(context.sourceFiles());
    }

    @Override
    public PartitionSplittingDriver partitionSplitting(SystemTestContext context) {
        return new AwsPartitionSplittingDriver(context.instance(), clients.getLambda());
    }

    @Override
    public SystemTestIngest ingest(SystemTestContext context) {
        return new SystemTestIngest(context.instance(), context.sourceFiles(),
                new AwsDirectIngestDriver(context.instance()),
                new IngestByQueue(context.instance(), new AwsIngestByQueueDriver(clients)),
                new DirectEmrServerlessDriver(context.instance(), clients),
                new AwsIngestBatcherDriver(context.instance(), context.sourceFiles(), clients),
                new AwsInvokeIngestTasksDriver(context.instance(), clients),
                AwsWaitForJobs.forIngest(context.instance(), clients.getDynamoDB()),
                AwsWaitForJobs.forBulkImport(context.instance(), clients.getDynamoDB()));
    }

    @Override
    public SystemTestQuery query(SystemTestContext context) {
        return new SystemTestQuery(context.instance(),
                SQSQueryDriver.allTablesDriver(context.instance(), clients),
                DirectQueryDriver.allTablesDriver(context.instance()),
                new S3ResultsDriver(context.instance(), clients.getS3()));
    }

    @Override
    public SystemTestCompaction compaction(SystemTestContext context) {
        return new SystemTestCompaction(
                new AwsCompactionDriver(context.instance(), clients),
                AwsWaitForJobs.forCompaction(context.instance(), clients.getDynamoDB()));
    }

    @Override
    public SystemTestReporting reporting(SystemTestContext context) {
        return new SystemTestReporting(context.reporting(),
                new AwsIngestReportsDriver(context.instance(), clients),
                new AwsCompactionReportsDriver(context.instance(), clients.getDynamoDB()));
    }

    @Override
    public SystemTestMetrics metrics(SystemTestContext context) {
        return new SystemTestMetrics(new AwsTableMetricsDriver(context.instance(), context.reporting(), clients));
    }

    @Override
    public SystemTestReports.SystemTestBuilder reportsForExtension(SystemTestContext context) {
        return SystemTestReports.builder(context.reporting(),
                new AwsPartitionReportDriver(context.instance()),
                new AwsIngestReportsDriver(context.instance(), clients),
                new AwsCompactionReportsDriver(context.instance(), clients.getDynamoDB()));
    }

    @Override
    public SystemTestCluster systemTestCluster(SystemTestContext context) {
        return new SystemTestCluster(context.systemTest(),
                new AwsDataGenerationTasksDriver(context.systemTest(), context.instance(), clients.getEcs()),
                new IngestByQueue(context.instance(), new AwsIngestByQueueDriver(clients)),
                new AwsGeneratedIngestSourceFilesDriver(context.systemTest(), clients.getS3V2()),
                new AwsInvokeIngestTasksDriver(context.instance(), clients),
                AwsWaitForJobs.forIngest(context.instance(), clients.getDynamoDB()),
                AwsWaitForJobs.forBulkImport(context.instance(), clients.getDynamoDB()));
    }

    @Override
    public SystemTestPythonApi pythonApi(SystemTestContext context) {
        Path pythonDir = context.parameters().getPythonDirectory();
        return new SystemTestPythonApi(context.instance(),
                new PythonIngestDriver(context.instance(), pythonDir),
                new PythonIngestLocalFileDriver(context.instance(), pythonDir),
                new PythonBulkImportDriver(context.instance(), pythonDir),
                new AwsInvokeIngestTasksDriver(context.instance(), clients),
                AwsWaitForJobs.forIngest(context.instance(), clients.getDynamoDB()),
                AwsWaitForJobs.forBulkImport(context.instance(), clients.getDynamoDB()),
                new PythonQueryDriver(context.instance(), pythonDir));
    }

    @Override
    public PurgeQueueDriver purgeQueueDriver(SystemTestContext context) {
        return new AwsPurgeQueueDriver(context.instance(), clients.getSqs());
    }
}

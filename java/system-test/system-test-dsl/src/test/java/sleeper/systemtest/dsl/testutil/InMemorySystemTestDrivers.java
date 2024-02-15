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

package sleeper.systemtest.dsl.testutil;

import sleeper.query.runner.recordretrieval.InMemoryDataStore;
import sleeper.systemtest.dsl.SystemTestContext;
import sleeper.systemtest.dsl.ingest.DirectIngestDriver;
import sleeper.systemtest.dsl.instance.DeployedSystemTestResources;
import sleeper.systemtest.dsl.instance.SleeperInstanceDriver;
import sleeper.systemtest.dsl.instance.SleeperTablesDriver;
import sleeper.systemtest.dsl.instance.SystemTestDeploymentDriver;
import sleeper.systemtest.dsl.instance.SystemTestParameters;
import sleeper.systemtest.dsl.query.QueryAllTablesDriver;
import sleeper.systemtest.dsl.sourcedata.GeneratedIngestSourceFilesDriver;
import sleeper.systemtest.dsl.testutil.drivers.InMemoryDirectIngestDriver;
import sleeper.systemtest.dsl.testutil.drivers.InMemoryDirectQueryDriver;
import sleeper.systemtest.dsl.testutil.drivers.InMemoryGeneratedIngestSourceFilesDriver;
import sleeper.systemtest.dsl.testutil.drivers.InMemoryQueryByQueueDriver;
import sleeper.systemtest.dsl.testutil.drivers.InMemorySleeperInstanceDriver;
import sleeper.systemtest.dsl.testutil.drivers.InMemorySleeperTablesDriver;
import sleeper.systemtest.dsl.testutil.drivers.InMemorySystemTestDeploymentDriver;
import sleeper.systemtest.dsl.util.PurgeQueueDriver;
import sleeper.systemtest.dsl.util.SystemTestDriversBase;

public class InMemorySystemTestDrivers extends SystemTestDriversBase {

    private final SystemTestDeploymentDriver systemTestDeploymentDriver = new InMemorySystemTestDeploymentDriver();
    private final InMemorySleeperTablesDriver tablesDriver = new InMemorySleeperTablesDriver();
    private final SleeperInstanceDriver instanceDriver = new InMemorySleeperInstanceDriver(tablesDriver);
    private final InMemoryDataStore data = new InMemoryDataStore();

    @Override
    public SystemTestDeploymentDriver systemTestDeployment(SystemTestParameters parameters) {
        return systemTestDeploymentDriver;
    }

    @Override
    public SleeperInstanceDriver instance(SystemTestParameters parameters) {
        return instanceDriver;
    }

    @Override
    public SleeperTablesDriver tables(SystemTestParameters parameters) {
        return tablesDriver;
    }

    @Override
    public GeneratedIngestSourceFilesDriver generatedSourceFiles(SystemTestParameters parameters, DeployedSystemTestResources systemTest) {
        return new InMemoryGeneratedIngestSourceFilesDriver();
    }

    @Override
    public DirectIngestDriver directIngest(SystemTestContext context) {
        return new InMemoryDirectIngestDriver(context.instance(), data);
    }

    @Override
    public QueryAllTablesDriver directQuery(SystemTestContext context) {
        return InMemoryDirectQueryDriver.allTablesDriver(context.instance(), data);
    }

    @Override
    public QueryAllTablesDriver queryByQueue(SystemTestContext context) {
        return InMemoryQueryByQueueDriver.allTablesDriver(context.instance(), data);
    }

    @Override
    public PurgeQueueDriver purgeQueueDriver(SystemTestContext context) {
        return properties -> {
        };
    }
}

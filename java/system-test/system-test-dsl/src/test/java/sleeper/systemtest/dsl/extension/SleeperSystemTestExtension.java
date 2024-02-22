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

package sleeper.systemtest.dsl.extension;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import sleeper.systemtest.dsl.SleeperSystemTest;
import sleeper.systemtest.dsl.SystemTestContext;
import sleeper.systemtest.dsl.SystemTestDrivers;
import sleeper.systemtest.dsl.instance.DeployedSleeperInstances;
import sleeper.systemtest.dsl.instance.DeployedSystemTestResources;
import sleeper.systemtest.dsl.instance.SystemTestParameters;

import java.util.Set;

import static sleeper.systemtest.dsl.extension.TestContextFactory.testContext;

public class SleeperSystemTestExtension implements ParameterResolver, BeforeAllCallback, BeforeEachCallback, AfterEachCallback {

    private static final Set<Class<?>> SUPPORTED_PARAMETER_TYPES = Set.of(
            SleeperSystemTest.class, AfterTestReports.class, AfterTestPurgeQueues.class,
            SystemTestParameters.class, SystemTestDrivers.class,
            DeployedSystemTestResources.class, DeployedSleeperInstances.class,
            SystemTestContext.class);

    private final SystemTestParameters parameters;
    private final SystemTestDrivers drivers;
    private final DeployedSystemTestResources deployedResources;
    private final DeployedSleeperInstances deployedInstances;
    private SystemTestContext testContext = null;
    private SleeperSystemTest dsl = null;
    private AfterTestReports reporting = null;
    private AfterTestPurgeQueues queuePurging = null;

    protected SleeperSystemTestExtension(SystemTestParameters parameters, SystemTestDrivers drivers) {
        this.parameters = parameters;
        this.drivers = drivers;
        deployedResources = new DeployedSystemTestResources(parameters, drivers.systemTestDeployment(parameters));
        deployedInstances = new DeployedSleeperInstances(
                parameters, deployedResources, drivers.instance(parameters), drivers.tables(parameters));
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return SUPPORTED_PARAMETER_TYPES.contains(parameterContext.getParameter().getType());
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        Class<?> type = parameterContext.getParameter().getType();
        if (type == SleeperSystemTest.class) {
            return dsl;
        } else if (type == AfterTestReports.class) {
            return reporting;
        } else if (type == AfterTestPurgeQueues.class) {
            return queuePurging;
        } else if (type == SystemTestParameters.class) {
            return parameters;
        } else if (type == SystemTestDrivers.class) {
            return drivers;
        } else if (type == DeployedSystemTestResources.class) {
            return deployedResources;
        } else if (type == DeployedSleeperInstances.class) {
            return deployedInstances;
        } else if (type == SystemTestContext.class) {
            return testContext;
        } else {
            throw new IllegalStateException("Unsupported parameter type: " + type);
        }
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        deployedResources.deployIfMissing();
        deployedResources.resetProperties();
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        drivers.generatedSourceFiles(parameters, deployedResources).emptyBucket();
        testContext = new SystemTestContext(parameters, drivers, deployedResources, deployedInstances);
        dsl = new SleeperSystemTest(parameters, drivers, testContext);
        reporting = new AfterTestReports(drivers, testContext);
        queuePurging = new AfterTestPurgeQueues(drivers.purgeQueues(testContext));
    }

    @Override
    public void afterEach(ExtensionContext context) {
        if (context.getExecutionException().isPresent()) {
            reporting.afterTestFailed(testContext(context));
            queuePurging.testFailed();
        } else {
            reporting.afterTestPassed(testContext(context));
            queuePurging.testPassed();
        }
        dsl = null;
        reporting = null;
        queuePurging = null;
    }
}
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
package sleeper.clients.admin.deploy;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import sleeper.configuration.properties.InstanceProperties;
import sleeper.configuration.properties.local.SaveLocalProperties;
import sleeper.configuration.properties.table.TableProperties;
import sleeper.core.CommonTestConstants;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static sleeper.core.schema.SchemaTestHelper.schemaWithKey;

@Testcontainers
public class GeneratePropertiesIT {

    @Container
    public static LocalStackContainer localStackContainer = new LocalStackContainer(DockerImageName.parse(CommonTestConstants.LOCALSTACK_DOCKER_IMAGE))
            .withServices(LocalStackContainer.Service.S3, LocalStackContainer.Service.STS);

    protected final AmazonS3 s3 = AmazonS3ClientBuilder.standard()
            .withEndpointConfiguration(localStackContainer.getEndpointConfiguration(LocalStackContainer.Service.S3))
            .withCredentials(localStackContainer.getDefaultCredentialsProvider())
            .build();
    private final AWSSecurityTokenService sts = AWSSecurityTokenServiceClientBuilder.standard()
            .withEndpointConfiguration(localStackContainer.getEndpointConfiguration(LocalStackContainer.Service.STS))
            .withCredentials(localStackContainer.getDefaultCredentialsProvider())
            .build();

    @TempDir
    private Path tempDir;

    @Test
    void generatedPropertiesIncludeBucketNamesForLocalDirectory() throws IOException {
        InstanceProperties instanceProperties = GenerateInstanceProperties.builder()
                .s3(s3).sts(sts)
                .instanceId("test-instance")
                .sleeperVersion("1.0.0")
                .vpcId("some-vpc").subnetId("some-subnet")
                .build().generate();
        TableProperties tableProperties = GenerateTableProperties.from(
                instanceProperties, schemaWithKey("key"), "test-table");

        SaveLocalProperties.saveToDirectory(tempDir, instanceProperties, Stream.of(tableProperties));
        assertThat(tempDir.resolve("configBucket.txt")).exists();
        assertThat(tempDir.resolve("queryResultsBucket.txt")).exists();
        assertThat(tempDir.resolve("tables/test-table/tableBucket.txt")).exists();
    }
}

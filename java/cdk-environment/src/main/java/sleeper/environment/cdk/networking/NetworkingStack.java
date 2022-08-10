/*
 * Copyright 2022 Crown Copyright
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
package sleeper.environment.cdk.networking;

import sleeper.environment.cdk.util.AppContext;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.*;
import software.constructs.Construct;

import java.util.Arrays;
import java.util.Collections;

public class NetworkingStack extends Stack {

    private final Vpc vpc;

    public NetworkingStack(Construct scope, StackProps props, AppContext context) {
        super(scope, context.getInstanceId() + "-Networking", props);

        vpc = Vpc.Builder.create(this, "Vpc")
                .cidr("10.0.0.0/16")
                .maxAzs(1)
                .subnetConfiguration(Arrays.asList(
                        SubnetConfiguration.builder().name("public")
                                .subnetType(SubnetType.PUBLIC)
                                .cidrMask(26).build(),
                        SubnetConfiguration.builder().name("private")
                                .subnetType(SubnetType.PRIVATE_WITH_NAT)
                                .cidrMask(19).build()))
                .build();

        GatewayVpcEndpoint.Builder.create(this, "S3").vpc(vpc)
                .service(GatewayVpcEndpointAwsService.S3)
                .subnets(Collections.singletonList(SubnetSelection.builder()
                        .subnetType(SubnetType.PRIVATE_WITH_NAT).build()))
                .build();

        GatewayVpcEndpoint.Builder.create(this, "DynamoDB").vpc(vpc)
                .service(GatewayVpcEndpointAwsService.DYNAMODB)
                .subnets(Collections.singletonList(SubnetSelection.builder()
                        .subnetType(SubnetType.PRIVATE_WITH_NAT).build()))
                .build();
    }

    public IVpc getVpc() {
        return vpc;
    }
}

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
package sleeper.compaction.jobexecution;

import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class SafeTerminationTest {
    /**
     * Custom termination Lambda input test from
     * https://docs.aws.amazon.com/autoscaling/ec2/userguide/lambda-custom-termination-policy.html.
     */
    public static final String IN_TEST = "{\n"
                    + "  \"AutoScalingGroupARN\": \"arn:aws:autoscaling:us-east-1:<account-id>:autoScalingGroup:d4738357-2d40-4038-ae7e-b00ae0227003:autoScalingGroupName/my-asg\",\n"
                    + "  \"AutoScalingGroupName\": \"my-asg\",\n"
                    + "  \"CapacityToTerminate\": [\n"
                    + "    {\n"
                    + "      \"AvailabilityZone\": \"us-east-1c\",\n"
                    + "      \"Capacity\": 3,\n"
                    + "      \"InstanceMarketOption\": \"on-demand\"\n"
                    + "    }\n"
                    + "  ],\n"
                    + "  \"Instances\": [\n"
                    + "    {\n"
                    + "      \"AvailabilityZone\": \"us-east-1c\",\n"
                    + "      \"InstanceId\": \"i-0056faf8da3e1f75d\",\n"
                    + "      \"InstanceType\": \"t2.nano\",\n"
                    + "      \"InstanceMarketOption\": \"on-demand\"\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"AvailabilityZone\": \"us-east-1c\",\n"
                    + "      \"InstanceId\": \"i-02e1c69383a3ed501\",\n"
                    + "      \"InstanceType\": \"t2.nano\",\n"
                    + "      \"InstanceMarketOption\": \"on-demand\"\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"AvailabilityZone\": \"us-east-1c\",\n"
                    + "      \"InstanceId\": \"i-036bc44b6092c01c7\",\n"
                    + "      \"InstanceType\": \"t2.nano\",\n"
                    + "      \"InstanceMarketOption\": \"on-demand\"\n"
                    + "    }\n"
                    + "  ],\n"
                    + "  \"Cause\": \"SCALE_IN\"\n"
                    + "}";

    public static final String IN_TEST_MULTI = "{\n"
                    + "  \"AutoScalingGroupARN\": \"arn:aws:autoscaling:us-east-1:<account-id>:autoScalingGroup:d4738357-2d40-4038-ae7e-b00ae0227003:autoScalingGroupName/my-asg\",\n"
                    + "  \"AutoScalingGroupName\": \"my-asg\",\n"
                    + "  \"CapacityToTerminate\": [\n"
                    + "    {\n"
                    + "      \"AvailabilityZone\": \"us-east-1c\",\n"
                    + "      \"Capacity\": 3,\n"
                    + "      \"InstanceMarketOption\": \"on-demand\"\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"AvailabilityZone\": \"us-east-1b\",\n"
                    + "      \"Capacity\": 4,\n"
                    + "      \"InstanceMarketOption\": \"on-demand\"\n"
                    + "    }\n"
                    + "  ],\n"
                    + "  \"Instances\": [\n"
                    + "    {\n"
                    + "      \"AvailabilityZone\": \"us-east-1c\",\n"
                    + "      \"InstanceId\": \"i-0056faf8da3e1f75d\",\n"
                    + "      \"InstanceType\": \"t2.nano\",\n"
                    + "      \"InstanceMarketOption\": \"on-demand\"\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"AvailabilityZone\": \"us-east-1c\",\n"
                    + "      \"InstanceId\": \"i-02e1c69383a3ed501\",\n"
                    + "      \"InstanceType\": \"t2.nano\",\n"
                    + "      \"InstanceMarketOption\": \"on-demand\"\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"AvailabilityZone\": \"us-east-1c\",\n"
                    + "      \"InstanceId\": \"i-036bc44b6092c01c7\",\n"
                    + "      \"InstanceType\": \"t2.nano\",\n"
                    + "      \"InstanceMarketOption\": \"on-demand\"\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"AvailabilityZone\": \"us-east-1b\",\n"
                    + "      \"InstanceId\": \"i-0056faf8da3e11234\",\n"
                    + "      \"InstanceType\": \"t2.nano\",\n"
                    + "      \"InstanceMarketOption\": \"on-demand\"\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"AvailabilityZone\": \"us-east-1b\",\n"
                    + "      \"InstanceId\": \"i-02e1c69383a34567\",\n"
                    + "      \"InstanceType\": \"t2.nano\",\n"
                    + "      \"InstanceMarketOption\": \"on-demand\"\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"AvailabilityZone\": \"us-east-1b\",\n"
                    + "      \"InstanceId\": \"i-036bc44b6092cbcdd\",\n"
                    + "      \"InstanceType\": \"t2.nano\",\n"
                    + "      \"InstanceMarketOption\": \"on-demand\"\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"AvailabilityZone\": \"us-east-1b\",\n"
                    + "      \"InstanceId\": \"i-036bc44b6092cabcd\",\n"
                    + "      \"InstanceType\": \"t2.nano\",\n"
                    + "      \"InstanceMarketOption\": \"on-demand\"\n"
                    + "    }\n"
                    + "  ],\n"
                    + "  \"Cause\": \"SCALE_IN\"\n"
                    + "}";

    public static final String IN_TEST_ZERO = "{\n"
                    + "  \"AutoScalingGroupARN\": \"arn:aws:autoscaling:us-east-1:<account-id>:autoScalingGroup:d4738357-2d40-4038-ae7e-b00ae0227003:autoScalingGroupName/my-asg\",\n"
                    + "  \"AutoScalingGroupName\": \"my-asg\",\n"
                    + "  \"CapacityToTerminate\": [\n"
                    + "  ],\n"
                    + "  \"Instances\": [\n"
                    + "  ],\n"
                    + "  \"Cause\": \"SCALE_IN\"\n"
                    + "}";

    /**
     * Custom termination Lambda input test from
     * https://docs.aws.amazon.com/autoscaling/ec2/userguide/lambda-custom-termination-policy.html.
     */
    public static final String IN_TEST_LIMIT = "{\n"
                    + "  \"AutoScalingGroupARN\": \"arn:aws:autoscaling:us-east-1:<account-id>:autoScalingGroup:d4738357-2d40-4038-ae7e-b00ae0227003:autoScalingGroupName/my-asg\",\n"
                    + "  \"AutoScalingGroupName\": \"my-asg\",\n"
                    + "  \"CapacityToTerminate\": [\n"
                    + "    {\n"
                    + "      \"AvailabilityZone\": \"us-east-1b\",\n"
                    + "      \"Capacity\": 1,\n"
                    + "      \"InstanceMarketOption\": \"on-demand\"\n"
                    + "    }"
                    + "  ],\n"
                    + "  \"Instances\": [\n"
                    + "    {\n"
                    + "      \"AvailabilityZone\": \"us-east-1c\",\n"
                    + "      \"InstanceId\": \"i-0056faf8da3e1f75d\",\n"
                    + "      \"InstanceType\": \"t2.nano\",\n"
                    + "      \"InstanceMarketOption\": \"on-demand\"\n"
                    + "    }\n"
                    + "  ],\n"
                    + "  \"Cause\": \"SCALE_IN\"\n"
                    + "}";

    public static final Set<String> EXPECTED_SET = new HashSet<>(Arrays.asList("i-0056faf8da3e1f75d", "i-02e1c69383a3ed501", "i-036bc44b6092c01c7"));

    public static final String JSON_RESPONSE = "{\"InstanceIDs\":[\"i-02e1c69383a3ed501\",\"i-0056faf8da3e1f75d\",\"i-036bc44b6092c01c7\"]}";
    public static final String JSON_RESPONSE_LIMIT = "{\"InstanceIDs\":[\"i-02e1c69383a3ed501\"]}";

    public static final Map<String, InstanceDetails> INSTANCE_MAP = new HashMap<>();
    public static final Map<String, InstanceDetails> INSTANCE_MAP_LIMIT = new HashMap<>();

    static {
        INSTANCE_MAP.put("i-0056faf8da3e1f75d", new InstanceDetails("arn", Instant.now(), 1, 1, 1, 1, 0, 0));
        INSTANCE_MAP.put("i-02e1c69383a3ed501", new InstanceDetails("arn", Instant.now(), 1, 1, 1, 1, 0, 0));
        INSTANCE_MAP.put("i-036bc44b6092c01c7", new InstanceDetails("arn", Instant.now(), 1, 1, 1, 1, 0, 0));

        INSTANCE_MAP_LIMIT.put("i-02e1c69383a3ed501", new InstanceDetails("arn", Instant.now(), 1, 1, 1, 1, 0, 0));
    }

    @Test(expected = NullPointerException.class)
    public void shouldThrowOnNullReader() {
        SafeTerminationLambda.extractSuggestedIDs(null);
    }

    @Test
    public void shouldReturnInstanceList() {
        // Given input string
        StringReader input = new StringReader(IN_TEST);

        // When
        Set<String> actual = SafeTerminationLambda.extractSuggestedIDs(input);

        // Then
        assertThat(actual).containsExactlyInAnyOrderElementsOf(EXPECTED_SET);
    }

    @Test(expected = NullPointerException.class)
    public void throwOnNullDetails() {
        SafeTerminationLambda.findEmptyInstances(null, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void throwOnNegativeSize() {
        SafeTerminationLambda.findEmptyInstances(new HashMap<>(), -1);
    }

    @Test
    public void shouldFindZeroEmptyInstances() {
        // Given
        Map<String, InstanceDetails> details = new HashMap<>();
        details.put("i-111", new InstanceDetails("someARN", Instant.now(), 1, 1, 1, 1, 1, 1));
        details.put("i-222", new InstanceDetails("someARN", Instant.now(), 1, 1, 1, 1, 0, 1));
        details.put("i-333", new InstanceDetails("someARN", Instant.now(), 1, 1, 1, 1, 1, 0));

        // When
        Set<String> empties = SafeTerminationLambda.findEmptyInstances(details, 3);

        // Then
        assertThat(empties).isEmpty();
    }

    @Test
    public void shouldFindOneEmptyInstances() {
        // Given
        Map<String, InstanceDetails> details = new HashMap<>();
        details.put("i-111", new InstanceDetails("someARN", Instant.now(), 1, 1, 1, 1, 0, 0));
        details.put("i-222", new InstanceDetails("someARN", Instant.now(), 1, 1, 1, 1, 0, 1));
        details.put("i-333", new InstanceDetails("someARN", Instant.now(), 1, 1, 1, 1, 1, 0));

        // When
        Set<String> single = SafeTerminationLambda.findEmptyInstances(details, 3);

        // Then
        assertThat(single).containsExactly("i-111");
    }

    @Test
    public void shouldFindMultiEmptyInstances() {
        // Given
        Map<String, InstanceDetails> details = new HashMap<>();
        details.put("i-111", new InstanceDetails("someARN", Instant.now(), 1, 1, 1, 1, 0, 0));
        details.put("i-222", new InstanceDetails("someARN", Instant.now(), 1, 1, 1, 1, 0, 0));
        details.put("i-333", new InstanceDetails("someARN", Instant.now(), 1, 1, 1, 1, 1, 1));

        // When
        Set<String> multi = SafeTerminationLambda.findEmptyInstances(details, 3);

        // Then
        assertThat(multi).containsExactlyInAnyOrder("i-111", "i-222");
    }

    @Test
    public void shouldFindAndLimitMultiEmptyInstances() {
        // Given
        Map<String, InstanceDetails> details = new HashMap<>();
        details.put("i-111", new InstanceDetails("someARN", Instant.now(), 1, 1, 1, 1, 0, 0));
        details.put("i-222", new InstanceDetails("someARN", Instant.now(), 1, 1, 1, 1, 0, 0));
        details.put("i-333", new InstanceDetails("someARN", Instant.now(), 1, 1, 1, 1, 1, 1));

        // When
        Set<String> multi = SafeTerminationLambda.findEmptyInstances(details, 1);

        // Then
        assertThat(multi).hasSize(1);
        assertThat(multi).containsAnyOf("i-111", "i-222");
    }

    @Test(expected = NullPointerException.class)
    public void throwOnNullReader() throws IOException {
        SafeTerminationLambda.suggestIDsToTerminate(null, new StringWriter(), INSTANCE_MAP);
    }

    @Test(expected = NullPointerException.class)
    public void throwOnNullWriter() throws IOException {
        SafeTerminationLambda.suggestIDsToTerminate(new StringReader(""), null, INSTANCE_MAP);
    }

    @Test(expected = NullPointerException.class)
    public void throwOnNullInstances() throws IOException {
        SafeTerminationLambda.suggestIDsToTerminate(new StringReader(""), new StringWriter(), null);
    }

    @Test
    public void shouldWriteCorrectJSONOutputNoLimit() throws IOException {
        // Given
        StringReader input = new StringReader(IN_TEST);
        StringWriter output = new StringWriter();

        // When
        SafeTerminationLambda.suggestIDsToTerminate(input, output, INSTANCE_MAP);

        // Then
        assertThat(output.toString()).isEqualToIgnoringWhitespace(JSON_RESPONSE);
    }

    @Test
    public void shouldWriteCorrectJSONOutputWithLimit() throws IOException {
        // Given
        StringReader input = new StringReader(IN_TEST_LIMIT);
        StringWriter output = new StringWriter();

        // When
        SafeTerminationLambda.suggestIDsToTerminate(input, output, INSTANCE_MAP_LIMIT);

        // Then
        assertThat(output.toString()).isEqualToIgnoringWhitespace(JSON_RESPONSE_LIMIT);
    }

    @Test(expected = NullPointerException.class)
    public void throwOnNullReaderTotalTerminations() {
        SafeTerminationLambda.totalTerminations(null);
    }

    @Test
    public void shouldReturnZeroTotalTerminations() {
        // Given
        StringReader input = new StringReader(IN_TEST_ZERO);

        // When
        int totalCapacity = SafeTerminationLambda.totalTerminations(input);

        // Then
        assertThat(totalCapacity).isEqualTo(0);
    }

    @Test
    public void shouldReturnSingleTotalTerminations() {
        // Given
        StringReader input = new StringReader(IN_TEST);

        // When
        int totalCapacity = SafeTerminationLambda.totalTerminations(input);

        // Then
        assertThat(totalCapacity).isEqualTo(3);
    }

    @Test
    public void shouldReturnMultipleTotalTerminations() {
        // Given
        StringReader input = new StringReader(IN_TEST_MULTI);

        // When
        int totalCapacity = SafeTerminationLambda.totalTerminations(input);

        // Then
        assertThat(totalCapacity).isEqualTo(7);
    }
}

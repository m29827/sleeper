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
package sleeper.bulkimport.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import sleeper.configuration.properties.instance.InstanceProperties;
import sleeper.configuration.properties.table.TableProperties;
import sleeper.configuration.properties.validation.EmrInstanceArchitecture;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sleeper.configuration.properties.instance.PersistentEMRProperty.BULK_IMPORT_PERSISTENT_EMR_INSTANCE_ARCHITECTURE;
import static sleeper.configuration.properties.instance.PersistentEMRProperty.BULK_IMPORT_PERSISTENT_EMR_MASTER_ARM_INSTANCE_TYPES;
import static sleeper.configuration.properties.instance.PersistentEMRProperty.BULK_IMPORT_PERSISTENT_EMR_MASTER_X86_INSTANCE_TYPES;
import static sleeper.configuration.properties.table.TableProperty.BULK_IMPORT_EMR_INSTANCE_ARCHITECTURE;
import static sleeper.configuration.properties.table.TableProperty.BULK_IMPORT_EMR_MASTER_ARM_INSTANCE_TYPES;
import static sleeper.configuration.properties.table.TableProperty.BULK_IMPORT_EMR_MASTER_X86_INSTANCE_TYPES;

public class EmrInstanceTypeConfigTest {

    @Nested
    @DisplayName("Read instance types")
    class ReadInstanceTypes {

        @Test
        void shouldReadSingleInstanceType() {
            assertThat(readInstanceTypesProperty(List.of("some-type")))
                    .containsExactly(
                            instanceType("some-type"));
        }

        @Test
        void shouldReadMultipleInstanceTypes() {
            assertThat(readInstanceTypesProperty(List.of("some-type", "other-type", "another-type")))
                    .containsExactly(
                            instanceType("some-type"),
                            instanceType("other-type"),
                            instanceType("another-type"));
        }

        @Test
        void shouldReadNoInstanceTypes() {
            assertThat(readInstanceTypesProperty(List.of()))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("Read instance weights")
    class ReadInstanceWeights {

        @Test
        void shouldReadSingleInstanceTypeWithWeight() {
            assertThat(readInstanceTypesProperty(List.of("some-type", "12")))
                    .containsExactly(
                            instanceTypeWithWeight("some-type", 12));
        }

        @Test
        void shouldReadMultipleInstanceTypesWhereMiddleOneHasWeight() {
            assertThat(readInstanceTypesProperty(List.of("some-type", "other-type", "42", "another-type")))
                    .containsExactly(
                            instanceType("some-type"),
                            instanceTypeWithWeight("other-type", 42),
                            instanceType("another-type"));
        }

        @Test
        void shouldReadMultipleInstanceTypesWhereAllHaveWeight() {
            assertThat(readInstanceTypesProperty(List.of("type-a", "1", "type-b", "2", "type-c", "3")))
                    .containsExactly(
                            instanceTypeWithWeight("type-a", 1),
                            instanceTypeWithWeight("type-b", 2),
                            instanceTypeWithWeight("type-c", 3));
        }

        @Test
        void shouldFailWhenWeightSpecifiedBeforeType() {
            assertThatThrownBy(() -> readInstanceTypesProperty(List.of("12", "some-type")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Filter by architecture")
    class FilterByArchitecture {
        private final InstanceProperties instanceProperties = new InstanceProperties();

        @Test
        void shouldReturnX86InstanceTypes() {
            // Given
            instanceProperties.set(BULK_IMPORT_PERSISTENT_EMR_INSTANCE_ARCHITECTURE, "x86");
            instanceProperties.set(BULK_IMPORT_PERSISTENT_EMR_MASTER_X86_INSTANCE_TYPES, "type-a,type-b");
            instanceProperties.set(BULK_IMPORT_PERSISTENT_EMR_MASTER_ARM_INSTANCE_TYPES, "type-c,type-d");

            // When / Then
            assertThat(EmrInstanceTypeConfig.readInstanceTypes(instanceProperties,
                    BULK_IMPORT_PERSISTENT_EMR_INSTANCE_ARCHITECTURE,
                    BULK_IMPORT_PERSISTENT_EMR_MASTER_X86_INSTANCE_TYPES,
                    BULK_IMPORT_PERSISTENT_EMR_MASTER_ARM_INSTANCE_TYPES))
                    .containsExactly(
                            x86Instance("type-a"), x86Instance("type-b"));
        }

        @Test
        void shouldReturnArmInstanceTypes() {
            // Given
            instanceProperties.set(BULK_IMPORT_PERSISTENT_EMR_INSTANCE_ARCHITECTURE, "arm64");
            instanceProperties.set(BULK_IMPORT_PERSISTENT_EMR_MASTER_X86_INSTANCE_TYPES, "type-a,type-b");
            instanceProperties.set(BULK_IMPORT_PERSISTENT_EMR_MASTER_ARM_INSTANCE_TYPES, "type-c,type-d");

            // When / Then
            assertThat(EmrInstanceTypeConfig.readInstanceTypes(instanceProperties,
                    BULK_IMPORT_PERSISTENT_EMR_INSTANCE_ARCHITECTURE,
                    BULK_IMPORT_PERSISTENT_EMR_MASTER_X86_INSTANCE_TYPES,
                    BULK_IMPORT_PERSISTENT_EMR_MASTER_ARM_INSTANCE_TYPES))
                    .containsExactly(
                            armInstance("type-c"), armInstance("type-d"));
        }

        @Test
        void shouldReturnX86AndArmInstanceTypes() {
            // Given
            instanceProperties.set(BULK_IMPORT_PERSISTENT_EMR_INSTANCE_ARCHITECTURE, "x86,arm64");
            instanceProperties.set(BULK_IMPORT_PERSISTENT_EMR_MASTER_X86_INSTANCE_TYPES, "type-a,type-b");
            instanceProperties.set(BULK_IMPORT_PERSISTENT_EMR_MASTER_ARM_INSTANCE_TYPES, "type-c,type-d");

            // When / Then
            assertThat(EmrInstanceTypeConfig.readInstanceTypes(instanceProperties,
                    BULK_IMPORT_PERSISTENT_EMR_INSTANCE_ARCHITECTURE,
                    BULK_IMPORT_PERSISTENT_EMR_MASTER_X86_INSTANCE_TYPES,
                    BULK_IMPORT_PERSISTENT_EMR_MASTER_ARM_INSTANCE_TYPES))
                    .containsExactly(
                            x86Instance("type-a"), x86Instance("type-b"),
                            armInstance("type-c"), armInstance("type-d"));
        }

        @Test
        void shouldReturnInstanceTypesByTableProperties() {
            // Given
            TableProperties properties = new TableProperties(instanceProperties);
            properties.set(BULK_IMPORT_EMR_INSTANCE_ARCHITECTURE, "x86,arm64");
            properties.set(BULK_IMPORT_EMR_MASTER_X86_INSTANCE_TYPES, "type-a,type-b");
            properties.set(BULK_IMPORT_EMR_MASTER_ARM_INSTANCE_TYPES, "type-c,type-d");

            // When / Then
            assertThat(EmrInstanceTypeConfig.readInstanceTypes(properties,
                    BULK_IMPORT_EMR_INSTANCE_ARCHITECTURE,
                    BULK_IMPORT_EMR_MASTER_X86_INSTANCE_TYPES,
                    BULK_IMPORT_EMR_MASTER_ARM_INSTANCE_TYPES))
                    .containsExactly(
                            x86Instance("type-a"), x86Instance("type-b"),
                            armInstance("type-c"), armInstance("type-d"));
        }
    }

    public static Stream<EmrInstanceTypeConfig> readInstanceTypesProperty(List<String> instanceTypeEntries) {
        return EmrInstanceTypeConfig.readInstanceTypesProperty(instanceTypeEntries, EmrInstanceArchitecture.X86_64);
    }

    private EmrInstanceTypeConfig instanceType(String instanceType) {
        return EmrInstanceTypeConfig.builder()
                .instanceType(instanceType)
                .architecture(EmrInstanceArchitecture.X86_64)
                .build();
    }

    private EmrInstanceTypeConfig instanceTypeWithWeight(String instanceType, int weightedCapacity) {
        return EmrInstanceTypeConfig.builder()
                .instanceType(instanceType)
                .architecture(EmrInstanceArchitecture.X86_64)
                .weightedCapacity(weightedCapacity)
                .build();
    }

    private EmrInstanceTypeConfig x86Instance(String instanceType) {
        return instanceTypeWithArchitecture(instanceType, EmrInstanceArchitecture.X86_64);
    }

    private EmrInstanceTypeConfig armInstance(String instanceType) {
        return instanceTypeWithArchitecture(instanceType, EmrInstanceArchitecture.ARM64);
    }

    private EmrInstanceTypeConfig instanceTypeWithArchitecture(String instanceType, EmrInstanceArchitecture architecture) {
        return EmrInstanceTypeConfig.builder()
                .instanceType(instanceType)
                .architecture(architecture)
                .build();
    }
}

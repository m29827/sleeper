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

package sleeper.systemtest.drivers.instance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

public class OutputInstanceIds {

    private OutputInstanceIds() {
    }

    public static void addInstanceIdToOutput(String instanceId, SystemTestParameters parameters) {
        Path outputDirectory = parameters.getOutputDirectory();
        if (outputDirectory == null) {
            return;
        }
        try {
            Files.writeString(outputDirectory.resolve("instanceIds.txt"),
                    instanceId + "\n", CREATE, APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
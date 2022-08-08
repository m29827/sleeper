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
package sleeper.environment.cdk.buildec2;

import org.apache.commons.io.IOUtils;

import java.net.URL;
import java.nio.charset.Charset;
import java.util.Objects;

class LoadUserDataUtil {

    private LoadUserDataUtil() {
        // Prevent instantiation
    }

    static String userData(BuildEC2Params params) {
        return params.fillUserDataTemplate(templateString());
    }

    private static String templateString() {
        try {
            URL resource = Objects.requireNonNull(LoadUserDataUtil.class.getClassLoader().getResource("user_data"));
            return IOUtils.toString(resource, Charset.defaultCharset());
        } catch (Exception e) {
            throw new RuntimeException("Failed to load user data template", e);
        }
    }
}

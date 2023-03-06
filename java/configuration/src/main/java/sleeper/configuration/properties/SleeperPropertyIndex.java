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
package sleeper.configuration.properties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SleeperPropertyIndex<T extends SleeperProperty> {

    private final Map<String, T> allMap = new HashMap<>();
    private final List<T> all = new ArrayList<>();
    private final List<T> userDefined = new ArrayList<>();
    private final List<T> systemDefined = new ArrayList<>();

    public void add(T property) {
        allMap.put(property.getPropertyName(), property);
        all.add(property);
        if (property.isSystemDefined()) {
            systemDefined.add(property);
        } else {
            userDefined.add(property);
        }
    }

    public List<T> getAll() {
        return Collections.unmodifiableList(all);
    }

    public List<T> getUserDefined() {
        return Collections.unmodifiableList(userDefined);
    }

    public List<T> getSystemDefined() {
        return Collections.unmodifiableList(systemDefined);
    }

    public Optional<T> getByName(String propertyName) {
        return Optional.ofNullable(allMap.get(propertyName));
    }
}

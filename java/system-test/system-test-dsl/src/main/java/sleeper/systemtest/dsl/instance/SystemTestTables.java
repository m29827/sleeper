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

package sleeper.systemtest.dsl.instance;

import sleeper.configuration.properties.table.TableProperty;
import sleeper.core.schema.Schema;
import sleeper.core.table.TableIdentity;

import java.util.List;
import java.util.Map;

public class SystemTestTables {

    private final SystemTestInstanceContext instance;

    public SystemTestTables(SystemTestInstanceContext instance) {
        this.instance = instance;
    }

    public void createMany(int numberOfTables, Schema schema) {
        createManyWithProperties(numberOfTables, schema, Map.of());
    }

    public SystemTestTables create(String name, Schema schema) {
        instance.createTable(name, schema);
        return this;
    }

    public void activate(String name) {
        instance.setCurrentTable(name);
    }

    public void createManyWithProperties(int numberOfTables, Schema schema, Map<TableProperty, String> setProperties) {
        instance.createTables(numberOfTables, schema, setProperties);
    }

    public List<TableIdentity> loadIdentities() {
        return instance.loadTableIdentities();
    }
}

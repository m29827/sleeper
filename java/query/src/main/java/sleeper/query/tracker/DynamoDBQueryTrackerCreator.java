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

package sleeper.query.tracker;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.google.common.collect.Lists;

import sleeper.configuration.properties.instance.InstanceProperties;

import java.util.Collection;
import java.util.List;

import static sleeper.configuration.properties.instance.CdkDefinedInstanceProperty.QUERY_TRACKER_TABLE_NAME;

public class DynamoDBQueryTrackerCreator {
    private final InstanceProperties instanceProperties;
    private final AmazonDynamoDB dynamoDBClient;

    public DynamoDBQueryTrackerCreator(InstanceProperties instanceProperties, AmazonDynamoDB dynamoDBClient) {
        this.instanceProperties = instanceProperties;
        this.dynamoDBClient = dynamoDBClient;
    }

    public void create() {
        String tableName = instanceProperties.get(QUERY_TRACKER_TABLE_NAME);
        dynamoDBClient.createTable(new CreateTableRequest(tableName, createKeySchema())
                .withAttributeDefinitions(createAttributeDefinitions())
                .withBillingMode(BillingMode.PAY_PER_REQUEST)
        );
        instanceProperties.set(QUERY_TRACKER_TABLE_NAME, tableName);
    }

    private Collection<AttributeDefinition> createAttributeDefinitions() {
        return Lists.newArrayList(
                new AttributeDefinition(DynamoDBQueryTracker.QUERY_ID, ScalarAttributeType.S),
                new AttributeDefinition(DynamoDBQueryTracker.SUB_QUERY_ID, ScalarAttributeType.S)
        );
    }

    private List<KeySchemaElement> createKeySchema() {
        return Lists.newArrayList(
                new KeySchemaElement()
                        .withAttributeName(DynamoDBQueryTracker.QUERY_ID)
                        .withKeyType(KeyType.HASH),
                new KeySchemaElement()
                        .withAttributeName(DynamoDBQueryTracker.SUB_QUERY_ID)
                        .withKeyType(KeyType.RANGE)
        );
    }
}
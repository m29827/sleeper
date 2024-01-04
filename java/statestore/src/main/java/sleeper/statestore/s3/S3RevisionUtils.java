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
package sleeper.statestore.s3;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.DeleteItemRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sleeper.configuration.properties.instance.CdkDefinedInstanceProperty;
import sleeper.configuration.properties.instance.InstanceProperties;
import sleeper.configuration.properties.table.TableProperties;
import sleeper.configuration.properties.table.TableProperty;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static sleeper.statestore.s3.S3StateStore.CURRENT_FILES_REVISION_ID_KEY;
import static sleeper.statestore.s3.S3StateStore.CURRENT_PARTITIONS_REVISION_ID_KEY;
import static sleeper.statestore.s3.S3StateStore.CURRENT_REVISION;
import static sleeper.statestore.s3.S3StateStore.CURRENT_UUID;
import static sleeper.statestore.s3.S3StateStore.REVISION_ID_KEY;
import static sleeper.statestore.s3.S3StateStore.TABLE_ID;

class S3RevisionUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(S3RevisionUtils.class);

    private final AmazonDynamoDB dynamoDB;
    private final String dynamoRevisionIdTable;
    private final String sleeperTableId;
    private final boolean stronglyConsistentReads;

    S3RevisionUtils(AmazonDynamoDB dynamoDB, InstanceProperties instanceProperties, TableProperties tableProperties) {
        this.dynamoDB = dynamoDB;
        this.dynamoRevisionIdTable = instanceProperties.get(CdkDefinedInstanceProperty.REVISION_TABLENAME);
        this.sleeperTableId = tableProperties.get(TableProperty.TABLE_ID);
        this.stronglyConsistentReads = tableProperties.getBoolean(TableProperty.DYNAMODB_STRONGLY_CONSISTENT_READS);
    }

    RevisionId getCurrentPartitionsRevisionId() {
        return getCurrentRevisionId(CURRENT_PARTITIONS_REVISION_ID_KEY);
    }

    RevisionId getCurrentFilesRevisionId() {
        return getCurrentRevisionId(CURRENT_FILES_REVISION_ID_KEY);
    }

    private RevisionId getCurrentRevisionId(String revisionIdKey) {
        GetItemResult result = dynamoDB.getItem(new GetItemRequest()
                .withTableName(dynamoRevisionIdTable)
                .withConsistentRead(stronglyConsistentReads)
                .withKey(Map.of(
                        TABLE_ID, new AttributeValue(sleeperTableId),
                        REVISION_ID_KEY, new AttributeValue(revisionIdKey))));
        if (null == result || null == result.getItem() || result.getItem().isEmpty()) {
            return null;
        }
        Map<String, AttributeValue> map = result.getItem();
        String revision = map.get(CURRENT_REVISION).getS();
        String uuid = map.get(CURRENT_UUID).getS();
        return new RevisionId(revision, uuid);
    }

    void saveFirstPartitionRevision(RevisionId revisionId) {
        saveFirstRevision(CURRENT_PARTITIONS_REVISION_ID_KEY, revisionId);
    }

    void saveFirstFilesRevision(RevisionId revisionId) {
        saveFirstRevision(CURRENT_FILES_REVISION_ID_KEY, revisionId);
    }

    private void saveFirstRevision(String revisionIdKey, RevisionId revisionId) {
        Map<String, AttributeValue> item = createRevisionIdItem(revisionIdKey, revisionId);
        dynamoDB.putItem(new PutItemRequest()
                .withTableName(dynamoRevisionIdTable)
                .withItem(item));
        LOGGER.debug("Put item to DynamoDB (item = {}, table = {})", item, dynamoRevisionIdTable);
    }

    void deletePartitionsRevision() {
        deleteRevision(CURRENT_PARTITIONS_REVISION_ID_KEY);
    }

    void deleteFilesRevision() {
        deleteRevision(CURRENT_FILES_REVISION_ID_KEY);
    }

    private void deleteRevision(String revisionIdValue) {
        dynamoDB.deleteItem(new DeleteItemRequest()
                .withTableName(dynamoRevisionIdTable)
                .withKey(Map.of(
                        TABLE_ID, new AttributeValue().withS(sleeperTableId),
                        REVISION_ID_KEY, new AttributeValue().withS(revisionIdValue))));
    }

    void conditionalUpdateOfPartitionRevisionId(RevisionId currentRevisionId, RevisionId newRevisionId) {
        LOGGER.debug("Attempting conditional update of partition information from revision id {} to {}", currentRevisionId, newRevisionId);
        conditionalUpdateOfRevisionId(CURRENT_PARTITIONS_REVISION_ID_KEY, currentRevisionId, newRevisionId);
    }

    void conditionalUpdateOfFileInfoRevisionId(RevisionId currentRevisionId, RevisionId newRevisionId) {
        LOGGER.debug("Attempting conditional update of file information from revision id {} to {}", currentRevisionId, newRevisionId);
        conditionalUpdateOfRevisionId(CURRENT_FILES_REVISION_ID_KEY, currentRevisionId, newRevisionId);
    }

    private void conditionalUpdateOfRevisionId(String revisionIdKey, RevisionId currentRevisionId, RevisionId newRevisionId) {
        dynamoDB.putItem(new PutItemRequest()
                .withTableName(dynamoRevisionIdTable)
                .withItem(createRevisionIdItem(revisionIdKey, newRevisionId))
                .withConditionExpression("#CurrentRevision = :currentrevision and #CurrentUUID = :currentuuid")
                .withExpressionAttributeNames(Map.of(
                        "#CurrentRevision", CURRENT_REVISION,
                        "#CurrentUUID", CURRENT_UUID))
                .withExpressionAttributeValues(Map.of(
                        ":currentrevision", new AttributeValue(currentRevisionId.getRevision()),
                        ":currentuuid", new AttributeValue(currentRevisionId.getUuid()))));
    }

    private Map<String, AttributeValue> createRevisionIdItem(String revisionIdKey, RevisionId revisionId) {
        return Map.of(
                TABLE_ID, new AttributeValue().withS(sleeperTableId),
                REVISION_ID_KEY, new AttributeValue().withS(revisionIdKey),
                CURRENT_REVISION, new AttributeValue().withS(revisionId.getRevision()),
                CURRENT_UUID, new AttributeValue().withS(revisionId.getUuid()));
    }

    RevisionId getNextRevisionId(RevisionId currentRevisionId) {
        String revision = currentRevisionId.getRevision();
        while (revision.startsWith("0")) {
            revision = revision.substring(1);
        }
        long revisionNumber = Long.parseLong(revision);
        long nextRevisionNumber = revisionNumber + 1;
        StringBuilder nextRevision = new StringBuilder("" + nextRevisionNumber);
        while (nextRevision.length() < 12) {
            nextRevision.insert(0, "0");
        }
        return new RevisionId(nextRevision.toString(), UUID.randomUUID().toString());
    }

    static class RevisionId {
        private final String revision;
        private final String uuid;

        RevisionId(String revision, String uuid) {
            this.revision = revision;
            this.uuid = uuid;
        }

        String getRevision() {
            return revision;
        }

        String getUuid() {
            return uuid;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof RevisionId)) {
                return false;
            }
            RevisionId that = (RevisionId) o;
            return Objects.equals(revision, that.revision) && Objects.equals(uuid, that.uuid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(revision, uuid);
        }

        @Override
        public String toString() {
            return "RevisionId{" +
                    "revision='" + revision + '\'' +
                    ", uuid='" + uuid + '\'' +
                    '}';
        }
    }
}

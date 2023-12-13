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

package sleeper.systemtest.suite.testutil;

import org.junit.jupiter.api.Test;

import sleeper.core.partition.PartitionTree;
import sleeper.core.partition.PartitionsBuilder;
import sleeper.core.schema.Schema;
import sleeper.core.schema.type.StringType;
import sleeper.core.statestore.FileInfo;
import sleeper.core.statestore.FileInfoFactory;
import sleeper.core.table.TableIdentity;

import java.util.List;
import java.util.Map;

import static org.approvaltests.Approvals.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static sleeper.core.schema.SchemaTestHelper.schemaWithKey;
import static sleeper.core.statestore.SplitFileInfo.referenceForChildPartition;

public class TableFileInfoPrinterTest {

    private final Schema schema = schemaWithKey("key", new StringType());
    private final PartitionsBuilder partitions = new PartitionsBuilder(schema);

    @Test
    void shouldPrintMultipleFilesInPartition() {
        partitions.rootFirst("root")
                .splitToNewChildren("root", "L", "R", "row-50");

        FileInfoFactory fileInfoFactory = fileInfoFactory();
        verify(TableFileInfoPrinter.printFiles(partitions.buildTree(), List.of(
                fileInfoFactory.partitionFile("L", 10),
                fileInfoFactory.partitionFile("L", 20),
                fileInfoFactory.partitionFile("R", 30),
                fileInfoFactory.partitionFile("R", 40))));
    }

    @Test
    void shouldPrintPartialFiles() {
        partitions.rootFirst("root")
                .splitToNewChildren("root", "L", "R", "row-50");

        FileInfo file1 = fileInfoFactory().rootFile("a.parquet", 100);
        FileInfo file2 = fileInfoFactory().rootFile("a.parquet", 200);
        referenceForChildPartition(file2, "L");
        verify(TableFileInfoPrinter.printFiles(partitions.buildTree(), List.of(
                referenceForChildPartition(file1, "L"),
                referenceForChildPartition(file2, "L"),
                referenceForChildPartition(file1, "R"),
                referenceForChildPartition(file2, "R"))));
    }

    @Test
    void shouldPrintFilesOnLeaves() {
        partitions.rootFirst("root")
                .splitToNewChildren("root", "L", "R", "row-50")
                .splitToNewChildren("L", "LL", "LR", "row-25")
                .splitToNewChildren("R", "RL", "RR", "row-75")
                .splitToNewChildren("LL", "LLL", "LLR", "row-12")
                .splitToNewChildren("LR", "LRL", "LRR", "row-37")
                .splitToNewChildren("RL", "RLL", "RLR", "row-62")
                .splitToNewChildren("RR", "RRL", "RRR", "row-87");

        FileInfoFactory fileInfoFactory = fileInfoFactory();
        verify(TableFileInfoPrinter.printFiles(partitions.buildTree(), List.of(
                fileInfoFactory.partitionFile("LLL", 12),
                fileInfoFactory.partitionFile("LLR", 13),
                fileInfoFactory.partitionFile("LRL", 12),
                fileInfoFactory.partitionFile("LRR", 13),
                fileInfoFactory.partitionFile("RLL", 12),
                fileInfoFactory.partitionFile("RLR", 13),
                fileInfoFactory.partitionFile("RRL", 12),
                fileInfoFactory.partitionFile("RRR", 13))));
    }

    @Test
    void shouldOrderFilesByPartitionLocationInTree() {
        partitions.rootFirst("root")
                .splitToNewChildren("root", "L", "R", "row-50")
                .splitToNewChildren("L", "LL", "LR", "row-25")
                .splitToNewChildren("R", "RL", "RR", "row-75")
                .splitToNewChildren("LL", "LLL", "LLR", "row-12")
                .splitToNewChildren("LR", "LRL", "LRR", "row-37")
                .splitToNewChildren("RL", "RLL", "RLR", "row-62")
                .splitToNewChildren("RR", "RRL", "RRR", "row-87");

        FileInfoFactory fileInfoFactory = fileInfoFactory();
        verify(TableFileInfoPrinter.printFiles(partitions.buildTree(), List.of(
                fileInfoFactory.partitionFile("L", 50),
                fileInfoFactory.partitionFile("LRL", 12),
                fileInfoFactory.partitionFile("root", 100),
                fileInfoFactory.partitionFile("RLL", 12),
                fileInfoFactory.partitionFile("RR", 12),
                fileInfoFactory.partitionFile("LLL", 13),
                fileInfoFactory.partitionFile("R", 12),
                fileInfoFactory.partitionFile("RRL", 13),
                fileInfoFactory.partitionFile("LLR", 25),
                fileInfoFactory.partitionFile("RLR", 50),
                fileInfoFactory.partitionFile("RRR", 100))));
    }

    @Test
    void shouldRenamePartitionsByLocation() {
        partitions.rootFirst("base")
                .splitToNewChildren("base", "l", "r", "row-50")
                .splitToNewChildren("l", "ll", "lr", "row-25")
                .splitToNewChildren("r", "rl", "rr", "row-75")
                .splitToNewChildren("ll", "1", "2", "row-12")
                .splitToNewChildren("lr", "3", "4", "row-37")
                .splitToNewChildren("rl", "5", "6", "row-62")
                .splitToNewChildren("rr", "7", "8", "row-87");

        FileInfoFactory fileInfoFactory = fileInfoFactory();
        verify(TableFileInfoPrinter.printFiles(partitions.buildTree(), List.of(
                fileInfoFactory.partitionFile("1", 12),
                fileInfoFactory.partitionFile("2", 13),
                fileInfoFactory.partitionFile("3", 12),
                fileInfoFactory.partitionFile("4", 13),
                fileInfoFactory.partitionFile("5", 12),
                fileInfoFactory.partitionFile("6", 13),
                fileInfoFactory.partitionFile("7", 12),
                fileInfoFactory.partitionFile("8", 13),
                fileInfoFactory.partitionFile("ll", 25),
                fileInfoFactory.partitionFile("l", 50),
                fileInfoFactory.partitionFile("base", 100))));
    }

    @Test
    void shouldPrintFilesOnceWhenTwoTablesAreIdentical() {
        partitions.rootFirst("root");
        List<FileInfo> files = List.of(fileInfoFactory().partitionFile("root", 10));

        verify(TableFileInfoPrinter.printTableFilesExpectingIdentical(
                Map.of("table-1", partitions.buildTree(), "table-2", partitions.buildTree()),
                Map.of("table-1", files, "table-2", files)));
    }

    @Test
    void shouldPrintDifferentFilesForOneTable() {
        partitions.rootFirst("root");
        List<FileInfo> files1 = List.of(fileInfoFactory().partitionFile("root", 10));
        List<FileInfo> files2 = List.of(fileInfoFactory().partitionFile("root", 20));

        verify(TableFileInfoPrinter.printTableFilesExpectingIdentical(
                Map.of("table-1", partitions.buildTree(), "table-2", partitions.buildTree(), "table-3", partitions.buildTree()),
                Map.of("table-1", files1, "table-2", files2, "table-3", files1)));
    }

    @Test
    void shouldPrintOnlyOneTable() {
        partitions.rootFirst("root");
        List<FileInfo> files = List.of(fileInfoFactory().partitionFile("root", 10));

        verify(TableFileInfoPrinter.printTableFilesExpectingIdentical(
                Map.of("table-1", partitions.buildTree()),
                Map.of("table-1", files)));
    }

    @Test
    void shouldPrintExpectedForTables() {
        partitions.rootFirst("root");
        Map<String, PartitionTree> partitionsByTable = Map.of(
                "table-1", partitions.buildTree(), "table-2", partitions.buildTree());
        List<FileInfo> files = List.of(fileInfoFactory().partitionFile("root", 10));

        assertThat(TableFileInfoPrinter.printExpectedFilesForAllTables(
                List.of(table("table-1"), table("table-2")), partitions.buildTree(), files))
                .isEqualTo(TableFileInfoPrinter.printTableFilesExpectingIdentical(partitionsByTable,
                        Map.of("table-1", files, "table-2", files)));
    }

    private TableIdentity table(String name) {
        return TableIdentity.uniqueIdAndName(name, name);
    }

    private FileInfoFactory fileInfoFactory() {
        return FileInfoFactory.from(partitions.buildTree());
    }
}

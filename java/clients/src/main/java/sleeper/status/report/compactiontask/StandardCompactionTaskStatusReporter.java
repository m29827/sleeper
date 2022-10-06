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
package sleeper.status.report.compactiontask;

import sleeper.compaction.task.CompactionTaskStatus;
import sleeper.status.report.table.TableField;
import sleeper.status.report.table.TableRow;
import sleeper.status.report.table.TableWriterFactory;

import java.io.PrintStream;
import java.util.List;

public class StandardCompactionTaskStatusReporter implements CompactionTaskStatusReporter {

    private static final TableWriterFactory.Builder tableFactoryBuilder = TableWriterFactory.builder();

    private static final TableField STATE = tableFactoryBuilder.addField("STATE");
    private static final TableField START_TIME = tableFactoryBuilder.addField("START_TIME");
    private static final TableField TASK_ID = tableFactoryBuilder.addField("TASK_ID");

    private static final TableWriterFactory tableFactory = tableFactoryBuilder.build();

    private final PrintStream out;

    public StandardCompactionTaskStatusReporter(PrintStream out) {
        this.out = out;
    }

    @Override
    public void report(List<CompactionTaskStatus> tasks) {
        out.println();
        out.println("Compaction Task Status Report");
        out.println("-----------------------------");
        out.printf("Total unfinished tasks: %s%n", tasks.size());

        tableFactory.tableBuilder()
                .itemsAndWriter(tasks, this::writeRow)
                .build().write(out);
    }

    private void writeRow(CompactionTaskStatus task, TableRow.Builder builder) {
        builder.value(STATE, task.isFinished() ? "FINISHED" : "RUNNING")
                .value(START_TIME, task.getStartTime())
                .value(TASK_ID, task.getTaskId());
    }
}

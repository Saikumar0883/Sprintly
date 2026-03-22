package com.sprintly.cli.command.task;

import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * Parent command: sprintly task
 *
 * Subcommands:
 *   task list         → list all tasks (table)
 *   task board        → Kanban board view         ← NEW
 *   task create       → create a new task
 *   task get <id>     → full task details
 *   task update <id>  → edit title / description
 *   task status <id>  → change status (assignee only)
 *   task bulk-status  → change multiple statuses at once
 */
@Command(
        name = "task",
        description = "Task management commands",
        footer = {
                "",
                "Quick reference:",
                "  task list                 list all tasks",
                "  task board                Kanban board view  (/board)",
                "  task create               create a new task",
                "  task get <id>             full task details",
                "  task update <id>          edit title/description",
                "  task status <id>          change status (interactive)",
                "  task status <id> DONE     change status directly",
                "  task bulk-status          update multiple tasks at once"
        },
        subcommands = {
                ListTasksCommand.class,
                BoardCommand.class,              // ← NEW
                CreateTaskCommand.class,
                GetTaskCommand.class,
                UpdateTaskCommand.class,
                UpdateTaskStatusCommand.class,
                BulkStatusCommand.class
        }
)
public class TaskCommand implements Callable<Integer> {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() {
        spec.commandLine().usage(System.out);
        return 0;
    }
}
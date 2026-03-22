package com.sprintly.cli.command.task;

import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * Parent command: sprintly task
 *
 * Subcommands:
 *   task list           → list all tasks (ID, Title, Status, Reporter, Assignee)
 *   task create         → create a new task (you become Reporter)
 *   task get <id>       → full details of a task
 *   task update <id>    → update title or description
 *   task status <id>    → change status (assignee only)
 *   task bulk-status    → change status of multiple tasks at once (assignee only)
 *
 * Type 'help' in the REPL for full examples.
 */
@Command(
        name = "task",
        description = "Task management commands",
        footer = {
                "",
                "Quick reference:",
                "  task list                      list all tasks",
                "  task list --status TODO        filter by status",
                "  task create                    create a new task",
                "  task get 3                     details of task #3",
                "  task update 3                  update title/description of task #3",
                "  task status 3                  change status of task #3 (assignee only)",
                "  task status 3 DONE             set task #3 directly to DONE",
                "  task bulk-status               update multiple tasks at once"
        },
        subcommands = {
                CreateTaskCommand.class,
                ListTasksCommand.class,
                GetTaskCommand.class,
                UpdateTaskCommand.class,           // ← NEW: update title/description
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
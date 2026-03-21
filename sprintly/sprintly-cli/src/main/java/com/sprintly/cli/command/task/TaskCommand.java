package com.sprintly.cli.command.task;

import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * Parent command: sprintly task
 *
 * Groups all task subcommands.
 * Shows help if called with no subcommand.
 *
 * Subcommands:
 *   sprintly task list           → list all tasks
 *   sprintly task create         → create a new task
 *   sprintly task get <id>       → get task details
 *   sprintly task status <id>    → change task status (TODO→IN_PROGRESS→DONE etc)
 */
@Command(
        name = "task",
        description = "Task management commands",
        subcommands = {
                CreateTaskCommand.class,
                ListTasksCommand.class,
                GetTaskCommand.class,
                com.sprintly.cli.command.task.UpdateTaskStatusCommand.class    // ← NEW
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
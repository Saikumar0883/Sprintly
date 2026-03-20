package com.sprintly.cli.command.task;

import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(
    name = "task",
    description = "Task management commands",
    subcommands = {
        CreateTaskCommand.class,
        ListTasksCommand.class,
        GetTaskCommand.class
    }
)
public class TaskCommand implements Callable<Integer> {

    @picocli.CommandLine.Spec
    picocli.CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() {
        spec.commandLine().usage(System.out);
        return 0;
    }
}

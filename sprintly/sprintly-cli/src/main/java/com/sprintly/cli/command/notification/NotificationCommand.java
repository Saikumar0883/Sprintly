package com.sprintly.cli.command.notification;

import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * Parent command: sprintly notification
 *
 * Groups all notification subcommands together.
 * Same pattern as TaskCommand — just shows help if called alone.
 *
 * Subcommands:
 *   sprintly notification list        → all notifications (read + unread)
 *   sprintly notification unread      → only unread notifications
 *   sprintly notification read <id>   → mark one as read
 *   sprintly notification read-all    → mark all as read
 */
@Command(
        name = "notification",
        description = "Manage your notifications",
        subcommands = {
                ListNotificationsCommand.class,
                UnreadNotificationsCommand.class,
                MarkReadCommand.class,
                MarkAllReadCommand.class
        }
)
public class NotificationCommand implements Callable<Integer> {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() {
        spec.commandLine().usage(System.out);
        return 0;
    }
}
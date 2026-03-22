package com.sprintly.cli.command.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sprintly.cli.client.SprintlyClient;
import com.sprintly.common.dto.ApiResponse;
import com.sprintly.task.dto.TaskDTO;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * CLI command: sprintly task list
 *
 * Lists all tasks with: ID, Title, Status, Reporter, Assignee
 *
 * Usage:
 *   task list
 *   task list --status TODO
 *   task list --status IN_PROGRESS
 *
 * Reporter = person who created the task (auto-set).
 * Assignee = person the task is assigned to (null = Unassigned).
 */
@Command(
        name = "list",
        description = "List all tasks with reporter and assignee",
        footer = {
                "",
                "Examples:",
                "  task list",
                "  task list --status TODO",
                "  task list --status IN_PROGRESS",
                "  task list --status DONE"
        }
)
public class ListTasksCommand implements Callable<Integer> {

    @Option(names = {"-s", "--status"},
            description = "Filter by status: TODO, IN_PROGRESS, IN_REVIEW, DONE, CANCELLED")
    private String status;

    private final SprintlyClient client = new SprintlyClient();

    @Override
    public Integer call() throws Exception {

        if (!client.isLoggedIn()) {
            System.err.println();
            System.err.println("  ✖  You are not logged in. Run: sprintly login");
            System.err.println();
            return 1;
        }

        ApiResponse<List<TaskDTO>> response =
                client.get("/tasks", new TypeReference<>() {}, true);

        if (response == null || !response.isSuccess()) {
            String msg = response != null ? response.getMessage() : "No response from server";
            // Specific hint for expired token
            if (msg != null && (msg.contains("expired") || msg.contains("Session"))) {
                System.err.println("  ✖  " + msg);
                System.err.println("     Run: refresh    (to get a new token)");
                System.err.println("     Or:  logout → login");
            } else {
                System.err.println("  ✖  Failed to fetch tasks: " + msg);
            }
            return 1;
        }

        List<TaskDTO> tasks = response.getData();

        if (tasks == null || tasks.isEmpty()) {
            System.out.println();
            System.out.println("  No tasks found.");
            System.out.println();
            return 0;
        }

        // Client-side status filter
        if (status != null && !status.isBlank()) {
            final String s = status.toUpperCase();
            tasks = tasks.stream().filter(t -> s.equals(t.getStatus())).toList();
            if (tasks.isEmpty()) {
                System.out.println("  No tasks found with status: " + s);
                return 0;
            }
        }

        // ── Print table with Reporter column ─────────────────────────────────
        System.out.println();
        System.out.printf("  %-5s  %-28s  %-13s  %-16s  %-16s%n",
                "ID", "Title", "Status", "Reporter", "Assignee");
        System.out.println("  " + "─".repeat(86));

        for (TaskDTO task : tasks) {
            String reporter = task.getReporterName() != null
                    ? task.getReporterName()
                    : (task.getReporterId() != null ? "#" + task.getReporterId() : "Unknown");

            String assignee = task.getAssigneeName() != null
                    ? task.getAssigneeName()
                    : "Unassigned";

            System.out.printf("  %-5d  %-28s  %-13s  %-16s  %-16s%n",
                    task.getId(),
                    truncate(task.getTitle(), 28),
                    task.getStatus(),
                    truncate(reporter, 16),
                    truncate(assignee, 16));
        }

        System.out.println("  " + "─".repeat(86));
        System.out.printf("  Total: %d task(s)%n%n", tasks.size());

        return 0;
    }

    private String truncate(String s, int len) {
        if (s == null) return "";
        return s.length() > len ? s.substring(0, len - 3) + "..." : s;
    }
}
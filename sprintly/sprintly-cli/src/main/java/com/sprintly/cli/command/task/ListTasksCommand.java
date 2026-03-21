package com.sprintly.cli.command.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sprintly.cli.client.SprintlyClient;
import com.sprintly.common.dto.ApiResponse;
import com.sprintly.task.dto.TaskDTO;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "list", description = "List all tasks")
public class ListTasksCommand implements Callable<Integer> {

    @Option(names = {"-s", "--status"}, description = "Filter by status (TODO, IN_PROGRESS, IN_REVIEW, DONE)")
    private String status;

    private final SprintlyClient client = new SprintlyClient();

    @Override
    public Integer call() throws Exception {

        // ── P2 FIX 1: Login guard ─────────────────────────────────────────────
        if (!client.isLoggedIn()) {
            System.err.println();
            System.err.println("  ✖  You are not logged in.");
            System.err.println("     Run:  sprintly login");
            System.err.println();
            return 1;
        }
        // ─────────────────────────────────────────────────────────────────────

        ApiResponse<List<TaskDTO>> response =
                client.get("/tasks", new TypeReference<>() {}, true);

        if (response == null || !response.isSuccess()) {
            System.err.println("  ✖  Failed to fetch tasks: "
                    + (response != null ? response.getMessage() : "No response from server"));
            return 1;
        }

        List<TaskDTO> tasks = response.getData();

        if (tasks == null || tasks.isEmpty()) {
            System.out.println("  No tasks found.");
            return 0;
        }

        // Optional client-side filter by status flag
        if (status != null && !status.isBlank()) {
            tasks = tasks.stream()
                    .filter(t -> status.equalsIgnoreCase(t.getStatus()))
                    .toList();
            if (tasks.isEmpty()) {
                System.out.println("  No tasks found with status: " + status.toUpperCase());
                return 0;
            }
        }

        // ── P2 FIX 2: Print assigneeName instead of raw ID ───────────────────
        // Previously: "Assignee" column showed "5" (a meaningless number)
        // Now:        "Assignee" column shows "Ravi Kumar" or "Unassigned"
        System.out.println();
        System.out.printf("  %-5s  %-30s  %-14s  %-20s%n",
                "ID", "Title", "Status", "Assignee");
        System.out.println("  " + "─".repeat(75));

        for (TaskDTO task : tasks) {
            // ── P2 FIX: Use assigneeName, fall back to "Unassigned" if null ──
            String assigneeDisplay = (task.getAssigneeName() != null && !task.getAssigneeName().isBlank())
                    ? task.getAssigneeName()
                    : "Unassigned";

            System.out.printf("  %-5d  %-30s  %-14s  %-20s%n",
                    task.getId(),
                    truncate(task.getTitle(), 30),
                    task.getStatus(),
                    truncate(assigneeDisplay, 20));
        }

        System.out.println("  " + "─".repeat(75));
        System.out.printf("  Total: %d task(s)%n%n", tasks.size());

        return 0;
    }

    private String truncate(String s, int len) {
        if (s == null) return "";
        return s.length() > len ? s.substring(0, len - 3) + "..." : s;
    }
}

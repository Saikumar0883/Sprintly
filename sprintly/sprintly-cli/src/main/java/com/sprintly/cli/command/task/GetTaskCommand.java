package com.sprintly.cli.command.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sprintly.cli.client.SprintlyClient;
import com.sprintly.cli.util.CliPrompt;
import com.sprintly.common.dto.ApiResponse;
import com.sprintly.task.dto.TaskDTO;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

/**
 * CLI command: sprintly task get <id>
 *
 * P2 Fixes applied:
 *   1. Login guard at top — exits immediately if not logged in
 *   2. Shows assigneeName instead of raw assignedTo ID
 *   3. Shows createdByName instead of raw createdBy ID
 *   4. Null check on API response
 */
@Command(name = "get", description = "Get full details of a task by ID")
public class GetTaskCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Task ID", arity = "0..1")
    private Long id;

    private final SprintlyClient client = new SprintlyClient();

    @Override
    public Integer call() throws Exception {

        // ── GUARD: Must be logged in ─────────────────────────────────────
        if (!client.isLoggedIn()) {
            System.err.println();
            System.err.println("  ✖  You are not logged in.");
            System.err.println("     Run:  sprintly login");
            System.err.println();
            return 1;
        }
        // ─────────────────────────────────────────────────────────────────

        // Prompt for ID if not passed as argument
        if (id == null) {
            id = CliPrompt.promptLong("Enter task ID: ");
        }

        ApiResponse<TaskDTO> response =
                client.get("/tasks/" + id, new TypeReference<>() {}, true);

        // Null check
        if (response == null || !response.isSuccess()) {
            System.err.println("  ✖  Failed to fetch task: "
                    + (response != null ? response.getMessage() : "No response from server"));
            return 1;
        }

        TaskDTO task = response.getData();

        // ── Print task detail ─────────────────────────────────────────────
        System.out.println();
        System.out.println("  ┌─────────────────────────────────────────────────┐");
        System.out.printf ("  │  Task #%-41d│%n", task.getId());
        System.out.println("  ├─────────────────────────────────────────────────┤");
        System.out.printf ("  │  Title      : %-34s│%n", truncate(task.getTitle(), 34));
        System.out.printf ("  │  Status     : %-34s│%n", task.getStatus());
        System.out.printf ("  │  Description: %-34s│%n",
                task.getDescription() != null ? truncate(task.getDescription(), 34) : "(none)");

        // Show assignee name — not raw ID
        String assigneeDisplay = (task.getAssigneeName() != null)
                ? task.getAssigneeName()
                : "Unassigned";
        System.out.printf ("  │  Assignee   : %-34s│%n", assigneeDisplay);

        // Show creator name — not raw ID
        String creatorDisplay = (task.getCreatedByName() != null)
                ? task.getCreatedByName()
                : String.valueOf(task.getCreatedBy());
        System.out.printf ("  │  Created By : %-34s│%n", creatorDisplay);

        System.out.printf ("  │  Created At : %-34s│%n",
                task.getCreatedAt() != null ? task.getCreatedAt().toString() : "");
        System.out.printf ("  │  Updated At : %-34s│%n",
                task.getUpdatedAt() != null ? task.getUpdatedAt().toString() : "");
        System.out.println("  └─────────────────────────────────────────────────┘");
        System.out.println();

        return 0;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen - 3) + "..." : s;
    }
}

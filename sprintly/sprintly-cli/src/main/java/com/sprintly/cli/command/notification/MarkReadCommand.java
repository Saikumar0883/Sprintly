package com.sprintly.cli.command.notification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sprintly.cli.client.SprintlyClient;
import com.sprintly.cli.util.CliPrompt;
import com.sprintly.common.dto.ApiResponse;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

/**
 * CLI command: sprintly notification read <id>
 *
 * Marks a single notification as read by its ID.
 * Calls PUT /notifications/{id}/read on the backend.
 *
 * Usage:
 *   sprintly notification read 5     → marks notification #5 as read
 *   sprintly notification read       → prompts for the ID interactively
 */
@Command(name = "read", description = "Mark a specific notification as read by ID")
public class MarkReadCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Notification ID to mark as read", arity = "0..1")
    private Long id;

    private final SprintlyClient client = new SprintlyClient();

    @Override
    public Integer call() throws Exception {

        // ── Login guard ───────────────────────────────────────────────
        if (!client.isLoggedIn()) {
            System.out.println();
            System.out.println("  ✖  You are not logged in.");
            System.out.println("     Run:  sprintly login");
            System.out.println();
            return 1;
        }

        // Prompt for ID if not passed as argument
        if (id == null) {
            id = CliPrompt.promptLong("Enter notification ID to mark as read: ");
        }

        ApiResponse<Boolean> response =
                client.put("/notifications/" + id + "/read", null, new TypeReference<>() {}, true);

        System.out.println();
        if (response != null && response.isSuccess()) {
            System.out.println("  ✔  Notification #" + id + " marked as read.");
        } else {
            System.err.println("  ✖  Failed: "
                    + (response != null ? response.getMessage() : "No response from server"));
            System.err.println("     Make sure notification #" + id + " exists and belongs to you.");
            return 1;
        }
        System.out.println();
        return 0;
    }
}
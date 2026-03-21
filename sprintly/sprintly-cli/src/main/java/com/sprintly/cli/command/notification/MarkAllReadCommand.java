package com.sprintly.cli.command.notification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sprintly.cli.client.SprintlyClient;
import com.sprintly.common.dto.ApiResponse;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * CLI command: sprintly notification read-all
 *
 * Marks ALL unread notifications as read in one call.
 * Calls PUT /notifications/read-all on the backend.
 *
 * This is the bulk version of MarkReadCommand.
 * Most useful after running 'sprintly notification list'
 * to acknowledge all pending notifications at once.
 */
@Command(name = "read-all", description = "Mark all notifications as read")
public class MarkAllReadCommand implements Callable<Integer> {

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

        ApiResponse<Integer> response =
                client.put("/notifications/read-all", null, new TypeReference<>() {}, true);

        System.out.println();
        if (response != null && response.isSuccess()) {
            Integer count = response.getData();
            if (count != null && count > 0) {
                System.out.println("  ✔  Marked " + count + " notification"
                        + (count == 1 ? "" : "s") + " as read.");
            } else {
                System.out.println("  ✓  No unread notifications to mark.");
            }
        } else {
            System.err.println("  ✖  Failed: "
                    + (response != null ? response.getMessage() : "No response from server"));
            return 1;
        }
        System.out.println();
        return 0;
    }
}
package com.sprintly.cli.command.notification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sprintly.cli.client.SprintlyClient;
import com.sprintly.cli.util.CliPrompt;
import com.sprintly.common.dto.ApiResponse;
import com.sprintly.notification.dto.NotificationDTO;
import picocli.CommandLine.Command;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * CLI command: sprintly notification unread
 *
 * Flow:
 *   1. Fetch all unread notifications from GET /notifications/unread
 *   2. Display them with 🔔 badge
 *   3. Ask user: "Mark all as read? [Y/n]"
 *   4. If yes → PUT /notifications/read-all → notifications move to read
 *
 * This is the primary command users will run to check new notifications.
 * It combines "see" and "acknowledge" in one step — just like an inbox.
 */
@Command(name = "unread", description = "Show unread notifications and optionally mark them as read")
public class UnreadNotificationsCommand implements Callable<Integer> {

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

        // ── Fetch unread notifications ────────────────────────────────
        ApiResponse<List<NotificationDTO>> response =
                client.get("/notifications/unread", new TypeReference<>() {}, true);

        if (!response.isSuccess()) {
            System.err.println("  ✖  Failed to fetch notifications: " + response.getMessage());
            return 1;
        }

        List<NotificationDTO> unread = response.getData();

        System.out.println();

        // ── No unread notifications ───────────────────────────────────
        if (unread == null || unread.isEmpty()) {
            System.out.println("  ✓  No unread notifications. You're all caught up!");
            System.out.println();
            System.out.println("     Run 'sprintly notification list' to see all past notifications.");
            System.out.println();
            return 0;
        }

        // ── Display unread notifications ──────────────────────────────
        System.out.printf("  🔔  You have %d unread notification%s%n",
                unread.size(), unread.size() == 1 ? "" : "s");
        System.out.println();

        for (NotificationDTO n : unread) {
            ListNotificationsCommand.printNotification(n);
        }

        // ── Ask to mark as read ───────────────────────────────────────
        System.out.println();
        String answer = CliPrompt.prompt("  Mark all as read? [Y/n]: ");

        // Default to yes if user just hits Enter
        if (answer == null || answer.isBlank() || answer.trim().equalsIgnoreCase("y")) {

            ApiResponse<Integer> markResponse =
                    client.put("/notifications/read-all", null, new TypeReference<>() {}, true);

            if (markResponse != null && markResponse.isSuccess()) {
                System.out.println();
                System.out.println("  ✔  All notifications marked as read.");
            } else {
                System.out.println();
                System.out.println("  ⚠  Could not mark as read: "
                        + (markResponse != null ? markResponse.getMessage() : "No response"));
            }
        } else {
            System.out.println();
            System.out.println("  Kept as unread. Run this command again to see them.");
        }

        System.out.println();
        return 0;
    }
}
package com.sprintly.cli.command.notification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sprintly.cli.client.SprintlyClient;
import com.sprintly.common.dto.ApiResponse;
import com.sprintly.notification.dto.NotificationDTO;
import picocli.CommandLine.Command;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * CLI command: sprintly notification list
 *
 * Shows ALL notifications (both read and unread) for the logged-in user.
 * Unread ones are highlighted with a 🔔 badge.
 * Read ones show ✓ to distinguish them.
 *
 * After viewing, this does NOT auto-mark as read.
 * User must run: sprintly notification read-all   (or read <id>)
 */
@Command(name = "list", description = "List all notifications (read and unread)")
public class ListNotificationsCommand implements Callable<Integer> {

    private final SprintlyClient client = new SprintlyClient();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

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

        ApiResponse<List<NotificationDTO>> response =
                client.get("/notifications", new TypeReference<>() {}, true);

        if (!response.isSuccess()) {
            System.err.println("  ✖  Failed to fetch notifications: " + response.getMessage());
            return 1;
        }

        List<NotificationDTO> notifications = response.getData();

        if (notifications == null || notifications.isEmpty()) {
            System.out.println();
            System.out.println("  You have no notifications.");
            System.out.println();
            return 0;
        }

        // Count unread for summary line
        long unreadCount = notifications.stream().filter(n -> !n.isRead()).count();

        System.out.println();
        System.out.println("  ╔══════════════════════════════════════════════════════════╗");
        System.out.printf ("  ║  All Notifications  (%d total, %d unread)%s║%n",
                notifications.size(), unreadCount,
                " ".repeat(Math.max(0, 19 - String.valueOf(notifications.size()).length()
                        - String.valueOf(unreadCount).length())));
        System.out.println("  ╚══════════════════════════════════════════════════════════╝");
        System.out.println();

        for (NotificationDTO n : notifications) {
            printNotification(n);
        }

        if (unreadCount > 0) {
            System.out.println();
            System.out.println("  Tip: Run 'sprintly notification read-all' to mark all as read.");
            System.out.println("       Run 'sprintly notification read <id>' to mark one as read.");
        }
        System.out.println();

        return 0;
    }

    static void printNotification(NotificationDTO n) {
        // 🔔 = unread,  ✓ = already read
        String badge = n.isRead() ? "  ✓ " : " 🔔 ";
        String readStatus = n.isRead() ? "[READ]  " : "[UNREAD]";

        System.out.println("  ┌─────────────────────────────────────────────────────────┐");
        System.out.printf ("  │ %s #%-4d  %-8s  %-12s                    │%n",
                badge, n.getId(), readStatus, n.getType());
        System.out.printf ("  │     Title  : %-43s│%n", truncate(n.getTitle(), 43));
        System.out.printf ("  │     Message: %-43s│%n", truncate(n.getMessage(), 43));
        System.out.printf ("  │     Time   : %-43s│%n",
                n.getCreatedAt() != null ? n.getCreatedAt().format(FMT) : "");
        System.out.println("  └─────────────────────────────────────────────────────────┘");
    }

    static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 3) + "..." : s;
    }
}
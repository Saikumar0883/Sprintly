package com.sprintly.cli.command.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sprintly.cli.client.SprintlyClient;
import com.sprintly.common.dto.ApiResponse;
import com.sprintly.task.dto.TaskDTO;
import picocli.CommandLine.Command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * CLI command: sprintly task board  (or /board)
 *
 * Displays all tasks as a Kanban board with columns:
 *   TODO | IN_PROGRESS | IN_REVIEW | DONE | CANCELLED
 *
 * Each card shows:
 *   Task ID and title (truncated to fit column)
 *   Assignee name
 *
 * Uses only the existing GET /api/tasks endpoint — no backend changes.
 */
@Command(
        name = "board",
        description = "Show tasks as a Kanban board",
        footer = {
                "",
                "Examples:",
                "  task board",
                "  /board"
        }
)
public class BoardCommand implements Callable<Integer> {

    private final SprintlyClient client = new SprintlyClient();

    // Column order — CANCELLED shown last, only if tasks exist in it
    private static final List<String> COLUMN_ORDER =
            List.of("TODO", "IN_PROGRESS", "IN_REVIEW", "DONE", "CANCELLED");

    // Visual column labels
    private static final Map<String, String> COLUMN_LABELS = Map.of(
            "TODO",        "  TODO       ",
            "IN_PROGRESS", "  IN PROGRESS",
            "IN_REVIEW",   "  IN REVIEW  ",
            "DONE",        "  DONE       ",
            "CANCELLED",   "  CANCELLED  "
    );

    // Width of each card (content area)
    private static final int CARD_WIDTH = 22;

    @Override
    public Integer call() throws Exception {

        if (!client.isLoggedIn()) {
            System.out.println();
            System.out.println("  X  Not logged in. Run: /login");
            System.out.println();
            return 1;
        }

        ApiResponse<List<TaskDTO>> response =
                client.get("/tasks", new TypeReference<>() {}, true);

        if (response == null || !response.isSuccess()) {
            System.err.println("  X  " + (response != null
                    ? response.getMessage() : "No response from server"));
            return 1;
        }

        List<TaskDTO> tasks = response.getData();

        if (tasks == null || tasks.isEmpty()) {
            System.out.println();
            System.out.println("  No tasks found. Create one with: task create");
            System.out.println();
            return 0;
        }

        // Group tasks by status
        Map<String, List<TaskDTO>> grouped = new LinkedHashMap<>();
        for (String col : COLUMN_ORDER) {
            grouped.put(col, new ArrayList<>());
        }
        for (TaskDTO t : tasks) {
            String status = t.getStatus() != null ? t.getStatus().toUpperCase() : "TODO";
            grouped.computeIfAbsent(status, k -> new ArrayList<>()).add(t);
        }

        // Decide which columns to show (always show TODO/IN_PROGRESS/IN_REVIEW/DONE,
        // show CANCELLED only if it has tasks)
        List<String> visibleColumns = new ArrayList<>();
        for (String col : COLUMN_ORDER) {
            if (!col.equals("CANCELLED") || !grouped.get(col).isEmpty()) {
                visibleColumns.add(col);
            }
        }

        int numCols = visibleColumns.size();

        // Print the board
        System.out.println();
        printBoard(grouped, visibleColumns, numCols);
        System.out.println();

        // Summary line
        System.out.println("  " + tasks.size() + " task(s) total  |"
                + visibleColumns.stream()
                .map(c -> "  " + abbrev(c) + ": " + grouped.get(c).size())
                .collect(Collectors.joining("  |"))
                + "  |");
        System.out.println();

        return 0;
    }

    // ── Board rendering ───────────────────────────────────────────────────────

    private void printBoard(Map<String, List<TaskDTO>> grouped,
                            List<String> columns, int numCols) {

        // ── Header row ────────────────────────────────────────────────────────
        printHeaderRow(columns);

        // ── Cards ─────────────────────────────────────────────────────────────
        // Find max tasks in any column to know how many rows to print
        int maxCards = columns.stream()
                .mapToInt(c -> grouped.get(c).size())
                .max().orElse(0);

        if (maxCards == 0) {
            System.out.println("  (no tasks)");
            return;
        }

        for (int row = 0; row < maxCards; row++) {
            // Each card is 4 lines tall: top border, title, assignee, bottom border
            printCardRow(columns, grouped, row);
        }
    }

    private void printHeaderRow(List<String> columns) {
        // Top border of header
        StringBuilder topBorder = new StringBuilder("  ");
        for (int i = 0; i < columns.size(); i++) {
            topBorder.append("┌").append("─".repeat(CARD_WIDTH + 2)).append("┐");
            if (i < columns.size() - 1) topBorder.append(" ");
        }
        System.out.println(topBorder);

        // Column labels
        StringBuilder labels = new StringBuilder("  ");
        for (int i = 0; i < columns.size(); i++) {
            String label = COLUMN_LABELS.getOrDefault(columns.get(i), columns.get(i));
            // Pad or truncate label to CARD_WIDTH
            String padded = centerPad(label.trim(), CARD_WIDTH);
            labels.append("│ ").append(padded).append(" │");
            if (i < columns.size() - 1) labels.append(" ");
        }
        System.out.println(labels);

        // Bottom border of header / top border for cards
        StringBuilder divider = new StringBuilder("  ");
        for (int i = 0; i < columns.size(); i++) {
            divider.append("├").append("─".repeat(CARD_WIDTH + 2)).append("┤");
            if (i < columns.size() - 1) divider.append(" ");
        }
        System.out.println(divider);
    }

    /**
     * Prints one row of cards (one card per column, for the same row index).
     * If a column has fewer tasks than the row index, prints an empty cell.
     *
     * Card layout (4 lines):
     *   │ #ID title truncated      │
     *   │   assignee name          │
     *   │                          │
     *   └──────────────────────────┘
     *
     * Between cards there is a space separator.
     */
    private void printCardRow(List<String> columns,
                              Map<String, List<TaskDTO>> grouped, int row) {

        List<TaskDTO> cards = new ArrayList<>();
        for (String col : columns) {
            List<TaskDTO> colTasks = grouped.get(col);
            cards.add(row < colTasks.size() ? colTasks.get(row) : null);
        }

        // Line 1: #ID + title
        printCardLine(cards, columns.size(), card -> {
            if (card == null) return " ".repeat(CARD_WIDTH);
            String idTitle = "#" + card.getId() + " " + card.getTitle();
            return padRight(truncate(idTitle, CARD_WIDTH), CARD_WIDTH);
        });

        // Line 2: assignee name (indented slightly)
        printCardLine(cards, columns.size(), card -> {
            if (card == null) return " ".repeat(CARD_WIDTH);
            String assignee = card.getAssigneeName() != null
                    ? card.getAssigneeName() : "Unassigned";
            return padRight("  " + truncate(assignee, CARD_WIDTH - 2), CARD_WIDTH);
        });

        // Line 3: reporter (if different from assignee, shows "by Reporter")
        printCardLine(cards, columns.size(), card -> {
            if (card == null) return " ".repeat(CARD_WIDTH);
            String reporter = card.getReporterName() != null ? card.getReporterName() : "";
            return padRight("  by " + truncate(reporter, CARD_WIDTH - 5), CARD_WIDTH);
        });

        // Bottom border of this row of cards
        StringBuilder bottom = new StringBuilder("  ");
        for (int i = 0; i < columns.size(); i++) {
            bottom.append("└").append("─".repeat(CARD_WIDTH + 2)).append("┘");
            if (i < columns.size() - 1) bottom.append(" ");
        }
        System.out.println(bottom);
    }

    @FunctionalInterface
    interface CardLineRenderer { String render(TaskDTO card); }

    private void printCardLine(List<TaskDTO> cards, int numCols, CardLineRenderer renderer) {
        StringBuilder line = new StringBuilder("  ");
        for (int i = 0; i < numCols; i++) {
            String content = renderer.render(cards.get(i));
            line.append("│ ").append(content).append(" │");
            if (i < numCols - 1) line.append(" ");
        }
        System.out.println(line);
    }

    // ── String helpers ────────────────────────────────────────────────────────

    private String truncate(String s, int max) {
        if (s == null || s.isEmpty()) return "";
        return s.length() > max ? s.substring(0, max - 2) + ".." : s;
    }

    private String padRight(String s, int width) {
        if (s == null) s = "";
        if (s.length() >= width) return s.substring(0, width);
        return s + " ".repeat(width - s.length());
    }

    private String centerPad(String s, int width) {
        if (s == null) s = "";
        if (s.length() >= width) return s.substring(0, width);
        int total = width - s.length();
        int left  = total / 2;
        int right = total - left;
        return " ".repeat(left) + s + " ".repeat(right);
    }

    private String abbrev(String status) {
        return switch (status) {
            case "TODO"        -> "Todo";
            case "IN_PROGRESS" -> "InProgress";
            case "IN_REVIEW"   -> "InReview";
            case "DONE"        -> "Done";
            case "CANCELLED"   -> "Cancelled";
            default            -> status;
        };
    }
}
package com.sprintly.cli;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sprintly.cli.client.SprintlyClient;
import com.sprintly.cli.command.LoginCommand;
import com.sprintly.cli.command.LogoutCommand;
import com.sprintly.cli.command.RefreshCommand;
import com.sprintly.cli.command.RegisterCommand;
import com.sprintly.cli.command.notification.NotificationCommand;
import com.sprintly.cli.command.task.TaskCommand;
import com.sprintly.cli.config.CliConfig;
import com.sprintly.common.dto.ApiResponse;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(
        name = "sprintly",
        mixinStandardHelpOptions = true,
        version = "1.0.0",
        description = "Sprintly CLI - Real-Time Collaborative Task Management",
        subcommands = {
                RegisterCommand.class,
                LoginCommand.class,
                LogoutCommand.class,
                RefreshCommand.class,
                TaskCommand.class,
                NotificationCommand.class
        }
)
public class SprintlyCli implements Callable<Integer> {

    // ── Slash command registry (for Tab completion only) ──────────────────────
    private static final Map<String, String> SLASH_COMMANDS = new LinkedHashMap<>();
    static {
        SLASH_COMMANDS.put("/help",          "Show full command guide");
        SLASH_COMMANDS.put("/whoami",        "Who am I + unread count");
        SLASH_COMMANDS.put("/clear",         "Clear terminal screen");
        SLASH_COMMANDS.put("/exit",          "Exit Sprintly");
        SLASH_COMMANDS.put("/login",         "Login to your account");
        SLASH_COMMANDS.put("/logout",        "Logout from all devices");
        SLASH_COMMANDS.put("/refresh",       "Refresh expired token");
        SLASH_COMMANDS.put("/register",      "Create a new account");
        SLASH_COMMANDS.put("/tasks",         "List all tasks (table view)");
        SLASH_COMMANDS.put("/board",         "Kanban board view");
        SLASH_COMMANDS.put("/create",        "Create a new task");
        SLASH_COMMANDS.put("/get",           "Full task details  /get <id>");
        SLASH_COMMANDS.put("/update",        "Edit title/desc    /update <id>");
        SLASH_COMMANDS.put("/status",        "Change task status /status <id>");
        SLASH_COMMANDS.put("/bulk",          "Bulk status update");
        SLASH_COMMANDS.put("/n",             "Unread notifications (shortcut)");
        SLASH_COMMANDS.put("/notifications", "Show unread notifications");
        SLASH_COMMANDS.put("/notif-list",    "Show all notifications");
        SLASH_COMMANDS.put("/read-all",      "Mark all notifications as read");
    }

    record SlashCommand(String expansion) {}
    private static final Map<String, SlashCommand> EXPANSIONS = new LinkedHashMap<>();
    static {
        EXPANSIONS.put("/login",         new SlashCommand("login"));
        EXPANSIONS.put("/logout",        new SlashCommand("logout"));
        EXPANSIONS.put("/refresh",       new SlashCommand("refresh"));
        EXPANSIONS.put("/register",      new SlashCommand("register"));
        EXPANSIONS.put("/tasks",         new SlashCommand("task list"));
        EXPANSIONS.put("/board",         new SlashCommand("task board"));
        EXPANSIONS.put("/create",        new SlashCommand("task create"));
        EXPANSIONS.put("/bulk",          new SlashCommand("task bulk-status"));
        EXPANSIONS.put("/n",             new SlashCommand("notification unread"));
        EXPANSIONS.put("/notifications", new SlashCommand("notification unread"));
        EXPANSIONS.put("/notif-list",    new SlashCommand("notification list"));
        EXPANSIONS.put("/read-all",      new SlashCommand("notification read-all"));
    }

    private static int unreadCount = 0;
    private static final SprintlyClient client = new SprintlyClient();

    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        if (args.length > 0) {
            new CommandLine(new SprintlyCli()).execute(args);
        } else {
            startRepl();
        }
    }

    private static void startRepl() {
        try {
            Terminal terminal = TerminalBuilder.builder().system(true).build();

            // ── Tab completer for slash commands ──────────────────────────────
            // When user types "/" and presses Tab, shows matching slash commands.
            // Each candidate shows the command name + description as a hint.
            Completer slashCompleter = (reader, line, candidates) -> {
                String word = line.word();
                if (word.startsWith("/")) {
                    SLASH_COMMANDS.forEach((cmd, desc) -> {
                        if (cmd.startsWith(word)) {
                            candidates.add(new Candidate(
                                    cmd,    // value inserted on Tab
                                    cmd,    // display text
                                    null,   // group
                                    desc,   // description shown next to candidate
                                    null, null, true
                            ));
                        }
                    });
                }
            };

            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(slashCompleter)
                    // Persistent history — survives across sessions
                    .variable(LineReader.HISTORY_FILE,
                            Paths.get(System.getProperty("user.home"), ".sprintly_history"))
                    .option(LineReader.Option.HISTORY_IGNORE_DUPS, true)
                    // Show all completions on first Tab press
                    .option(LineReader.Option.AUTO_LIST, true)
                    // Show completions inline as a menu
                    .option(LineReader.Option.AUTO_MENU, true)
                    .build();

            printWelcome();
            refreshUnreadCount();

            while (true) {
                String line;
                try {
                    line = reader.readLine(buildPrompt());
                } catch (UserInterruptException e) {
                    continue;
                } catch (EndOfFileException e) {
                    break;
                }

                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) continue;

                // Exit
                if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")
                        || line.equals("/exit") || line.equals("/quit")) {
                    System.out.println("  Goodbye!");
                    break;
                }

                // help keyword
                if (line.equalsIgnoreCase("help")) {
                    printHelp();
                    continue;
                }

                // Slash command
                if (line.startsWith("/")) {
                    handleSlashCommand(line, terminal);
                    if (line.startsWith("/n") || line.startsWith("/notif")
                            || line.startsWith("/read") || line.startsWith("/login")
                            || line.startsWith("/logout")) {
                        refreshUnreadCount();
                    }
                    continue;
                }

                // Normal Picocli command
                ParsedLine pl = reader.getParser().parse(line, 0);
                new CommandLine(new SprintlyCli()).execute(pl.words().toArray(new String[0]));

                if (line.startsWith("login") || line.startsWith("notification")
                        || line.startsWith("refresh")) {
                    refreshUnreadCount();
                }
            }

            terminal.close();

        } catch (IOException e) {
            System.err.println("Terminal error: " + e.getMessage());
        }
    }

    // ── Slash command execution ───────────────────────────────────────────────

    private static void handleSlashCommand(String input, Terminal terminal) {
        String[] parts  = input.split("\\s+", 2);
        String slashCmd = parts[0].toLowerCase();
        String rest     = parts.length > 1 ? parts[1].trim() : "";

        // Built-in specials
        switch (slashCmd) {
            case "/help"   -> { printHelp();   return; }
            case "/whoami" -> { printWhoAmI(); return; }
            case "/clear"  -> {
                terminal.writer().print("\033[H\033[J");
                terminal.writer().flush();
                return;
            }
        }

        // Commands that take passthrough args
        if (slashCmd.equals("/status")) {
            executeExpanded("task status" + (rest.isEmpty() ? "" : " " + rest)); return;
        }
        if (slashCmd.equals("/get")) {
            executeExpanded("task get" + (rest.isEmpty() ? "" : " " + rest)); return;
        }
        if (slashCmd.equals("/update")) {
            executeExpanded("task update" + (rest.isEmpty() ? "" : " " + rest)); return;
        }

        // Registry lookup
        SlashCommand sc = EXPANSIONS.get(slashCmd);
        if (sc != null) {
            executeExpanded(sc.expansion());
            return;
        }

        System.out.println("\n  Unknown: " + slashCmd
                + "  \u2014 type / then Tab to see commands.\n");
    }

    private static void executeExpanded(String commandStr) {
        try {
            org.jline.reader.Parser p = new org.jline.reader.impl.DefaultParser();
            ParsedLine pl = p.parse(commandStr, commandStr.length());
            new CommandLine(new SprintlyCli()).execute(pl.words().toArray(new String[0]));
        } catch (Exception e) {
            new CommandLine(new SprintlyCli()).execute(commandStr.split("\\s+"));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String buildPrompt() {
        return unreadCount > 0
                ? "sprintly [" + unreadCount + " unread]> "
                : "sprintly> ";
    }

    private static void refreshUnreadCount() {
        if (!client.isLoggedIn()) { unreadCount = 0; return; }
        try {
            ApiResponse<Integer> r = client.get("/notifications/unread/count",
                    new TypeReference<>() {}, true);
            unreadCount = (r != null && r.isSuccess() && r.getData() != null)
                    ? r.getData() : 0;
        } catch (Exception ignored) { unreadCount = 0; }
    }

    private static void printWhoAmI() {
        CliConfig cfg = CliConfig.load();
        System.out.println();
        if (cfg == null || cfg.getAccessToken() == null) {
            System.out.println("  Not logged in.  Run: /login  or  login");
        } else {
            refreshUnreadCount();
            System.out.printf("  Logged in as : %s%n",
                    cfg.getName()  != null ? cfg.getName()  : "(unknown)");
            System.out.printf("  Email        : %s%n",
                    cfg.getEmail() != null ? cfg.getEmail() : "(unknown)");
            System.out.printf("  Unread notifs: %s%n",
                    unreadCount > 0 ? unreadCount + " \uD83D\uDD14" : "none \u2713");
        }
        System.out.println();
    }

    private static void printWelcome() {
        System.out.println();
        System.out.println("  \u2554\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2557");
        System.out.println("  \u2551   \u26A1  Sprintly CLI  \u2014  Task Management      \u2551");
        System.out.println("  \u255A\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u255D");
        System.out.println();
        System.out.println("  Type /    then Tab  \u2192  see all slash commands");
        System.out.println("  Press \u2191\u2193           \u2192  navigate command history");
        System.out.println("  Type help or /exit  \u2192  get help or quit");
        System.out.println();
    }

    private static void printHelp() {
        System.out.println();
        System.out.printf("  %-20s  %s%n", "SLASH COMMAND", "DESCRIPTION");
        System.out.println("  " + "\u2500".repeat(62));
        SLASH_COMMANDS.forEach((cmd, desc) ->
                System.out.printf("  %-20s  %s%n", cmd, desc));
        System.out.println();
        System.out.println("  Full commands also work: task list, task board, notification unread");
        System.out.println("  Tab:     press after / to autocomplete slash commands");
        System.out.println("  History: press \u2191 \u2193 to recall previous commands");
        System.out.println("  Badge:   prompt shows [N unread] when you have notifications");
        System.out.println();
    }

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }
}
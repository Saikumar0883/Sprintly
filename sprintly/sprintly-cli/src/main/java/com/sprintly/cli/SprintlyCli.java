package com.sprintly.cli;

import com.sprintly.cli.command.LoginCommand;
import com.sprintly.cli.command.LogoutCommand;
import com.sprintly.cli.command.RefreshCommand;
import com.sprintly.cli.command.RegisterCommand;
import com.sprintly.cli.command.notification.NotificationCommand;
import com.sprintly.cli.command.task.TaskCommand;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.io.IOException;
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

    public static void main(String[] args) {
        if (args.length > 0) {
            int exitCode = new CommandLine(new SprintlyCli()).execute(args);
            System.exit(exitCode);
        } else {
            startRepl();
        }
    }

    private static void startRepl() {
        printWelcome();
        try {
            Terminal terminal = TerminalBuilder.builder().system(true).build();
            LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();

            while (true) {
                String line = null;
                try {
                    line = reader.readLine("sprintly> ");
                } catch (UserInterruptException e) {
                    continue;
                } catch (EndOfFileException e) {
                    break;
                }

                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) continue;

                // Handle exit
                if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
                    System.out.println("  Goodbye! 👋");
                    break;
                }

                // Handle 'help' keyword in REPL — show full command guide
                if (line.equalsIgnoreCase("help")) {
                    printHelp();
                    continue;
                }

                org.jline.reader.ParsedLine pl = reader.getParser().parse(line, 0);
                String[] cmdArgs = pl.words().toArray(new String[0]);
                new CommandLine(new SprintlyCli()).execute(cmdArgs);
            }
        } catch (IOException e) {
            System.err.println("Error initializing terminal: " + e.getMessage());
        }
    }

    private static void printWelcome() {
        System.out.println();
        System.out.println("  ⚡  Welcome to Sprintly CLI!");
        System.out.println("  ─────────────────────────────────────────────");
        System.out.println("  Type 'help' for a full command guide.");
        System.out.println("  Type 'exit' or 'quit' to leave.");
        System.out.println();
    }

    /**
     * Full help guide shown when user types 'help' in REPL.
     * Covers every command with syntax, options, and examples.
     */
    private static void printHelp() {
        System.out.println();
        System.out.println("  ╔══════════════════════════════════════════════════════════════╗");
        System.out.println("  ║              SPRINTLY CLI — COMMAND GUIDE                    ║");
        System.out.println("  ╚══════════════════════════════════════════════════════════════╝");

        System.out.println();
        System.out.println("  ── AUTH ──────────────────────────────────────────────────────");
        System.out.println();
        System.out.println("  register");
        System.out.println("    Create a new Sprintly account.");
        System.out.println("    Example:  register");
        System.out.println("    Example:  register --name \"Ravi\" --email ravi@test.com --password Pass@123");
        System.out.println();
        System.out.println("  login");
        System.out.println("    Login to Sprintly. Saves token to ~/.sprintly-cli.json");
        System.out.println("    Example:  login");
        System.out.println("    Example:  login --email ravi@test.com --password Pass@123");
        System.out.println();
        System.out.println("  logout");
        System.out.println("    Logout from all devices. Clears saved token.");
        System.out.println("    Example:  logout");
        System.out.println();
        System.out.println("  refresh");
        System.out.println("    Refresh your access token using the saved refresh token.");
        System.out.println("    Use this if you get 'Session expired' errors.");
        System.out.println("    Example:  refresh");
        System.out.println();

        System.out.println("  ── TASKS ─────────────────────────────────────────────────────");
        System.out.println();
        System.out.println("  task list");
        System.out.println("    List all tasks (ID, Title, Status, Reporter, Assignee).");
        System.out.println("    Example:  task list");
        System.out.println("    Example:  task list --status TODO");
        System.out.println("    Example:  task list --status IN_PROGRESS");
        System.out.println();
        System.out.println("  task create");
        System.out.println("    Create a new task. Prompts for title, description, assignee.");
        System.out.println("    You (the logged-in user) become the Reporter automatically.");
        System.out.println("    The assignee will receive a real-time notification.");
        System.out.println("    Example:  task create");
        System.out.println("    Example:  task create --title \"Fix bug\" --description \"Details here\"");
        System.out.println();
        System.out.println("  task get <id>");
        System.out.println("    Get full details of a task: title, status, reporter, assignee,");
        System.out.println("    description, created/updated timestamps.");
        System.out.println("    Example:  task get 3");
        System.out.println("    Example:  task get        (prompts for ID)");
        System.out.println();
        System.out.println("  task update <id>");
        System.out.println("    Update the title or description of a task.");
        System.out.println("    Example:  task update 3");
        System.out.println("    Example:  task update 3 --title \"New title\"");
        System.out.println("    Example:  task update 3 --description \"Updated description\"");
        System.out.println("    Example:  task update 3 --title \"New\" --description \"New desc\"");
        System.out.println();
        System.out.println("  task status <id> [newStatus]");
        System.out.println("    Change the status of a task. ONLY the assignee can do this.");
        System.out.println("    Valid transitions:");
        System.out.println("      TODO        → IN_PROGRESS, CANCELLED");
        System.out.println("      IN_PROGRESS → IN_REVIEW, CANCELLED");
        System.out.println("      IN_REVIEW   → DONE, IN_PROGRESS, CANCELLED");
        System.out.println("    When moved to DONE, the reporter gets notified automatically.");
        System.out.println("    Example:  task status 3              (interactive menu)");
        System.out.println("    Example:  task status 3 IN_PROGRESS  (direct)");
        System.out.println("    Example:  task status 3 DONE");
        System.out.println();
        System.out.println("  task bulk-status");
        System.out.println("    Update the status of multiple tasks at once.");
        System.out.println("    Shows your assigned tasks → you pick which ones to update.");
        System.out.println("    Example:  task bulk-status");
        System.out.println("    Example:  task bulk-status --status IN_PROGRESS");
        System.out.println();

        System.out.println("  ── NOTIFICATIONS ─────────────────────────────────────────────");
        System.out.println();
        System.out.println("  notification unread");
        System.out.println("    Show your unread notifications. Prompts to mark all as read.");
        System.out.println("    Example:  notification unread");
        System.out.println();
        System.out.println("  notification list");
        System.out.println("    Show ALL notifications (read ✓ and unread 🔔).");
        System.out.println("    Example:  notification list");
        System.out.println();
        System.out.println("  notification read <id>");
        System.out.println("    Mark a single notification as read.");
        System.out.println("    Example:  notification read 5");
        System.out.println();
        System.out.println("  notification read-all");
        System.out.println("    Mark all notifications as read at once.");
        System.out.println("    Example:  notification read-all");
        System.out.println();

        System.out.println("  ── TIPS ──────────────────────────────────────────────────────");
        System.out.println();
        System.out.println("  • If you see 'Session expired': run  refresh  or  logout + login");
        System.out.println("  • If you see 'Access denied' on tasks: your token may be expired.");
        System.out.println("    Run: refresh");
        System.out.println("  • Config is stored at: ~/.sprintly-cli.json");
        System.out.println("    Delete it to force a fresh login: rm ~/.sprintly-cli.json");
        System.out.println("  • Add --help to any command for Picocli usage info.");
        System.out.println("    Example:  task status --help");
        System.out.println();
    }

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }
}
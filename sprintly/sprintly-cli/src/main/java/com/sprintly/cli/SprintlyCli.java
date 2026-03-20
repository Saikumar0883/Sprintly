package com.sprintly.cli;

import com.sprintly.cli.command.LoginCommand;
import com.sprintly.cli.command.LogoutCommand;
import com.sprintly.cli.command.RefreshCommand;
import com.sprintly.cli.command.RegisterCommand;
import com.sprintly.cli.command.task.TaskCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.EndOfFileException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import java.io.IOException;

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
        TaskCommand.class
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
        System.out.println("Welcome to Sprintly CLI!");
        System.out.println("Type 'help' to see available commands or 'exit' to quit.");
        try {
            Terminal terminal = TerminalBuilder.builder()
                    .system(true)
                    .build();
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .build();

            while (true) {
                String line = null;
                try {
                    line = reader.readLine("sprintly> ");
                } catch (UserInterruptException e) {
                    continue; // Ignore ^C and show prompt again
                } catch (EndOfFileException e) {
                    break; // Exit on ^D
                }

                if (line == null) {
                    break;
                }
                
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
                    break;
                }

                org.jline.reader.ParsedLine pl = reader.getParser().parse(line, 0);
                String[] cmdArgs = pl.words().toArray(new String[0]);
                new CommandLine(new SprintlyCli()).execute(cmdArgs);
            }
        } catch (IOException e) {
            System.err.println("Error initializing terminal: " + e.getMessage());
        }
    }

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }
}

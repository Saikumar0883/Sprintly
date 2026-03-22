package com.sprintly.cli.util;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.Console;
import java.util.Scanner;

public class CliPrompt {

    private static final Scanner scanner = new Scanner(System.in);
    private static final Console console = System.console();

    public static String prompt(String message) {
        if (console != null) {
            return console.readLine(message);
        } else {
            System.out.print(message);
            return scanner.nextLine();
        }
    }

    public static String promptPassword(String message) {
        if (console != null) {
            char[] passwordArray = console.readPassword(message);
            return passwordArray != null ? new String(passwordArray) : "";
        } else {
            System.out.print(message);
            return scanner.nextLine();
        }
    }

    public static Long promptLong(String message) {
        while (true) {
            String input = prompt(message);
            try {
                return Long.parseLong(input.trim());
            } catch (NumberFormatException e) {
                System.out.println("Invalid number. Please try again.");
            }
        }
    }

    /**
     * Shows the existing value pre-filled in the input buffer.
     * The user can edit it inline with arrow keys, backspace, Ctrl+A/E, etc.
     * Pressing Enter with no changes returns the original text unchanged.
     *
     * This is used by UpdateTaskCommand so the user edits the existing
     * description/title in place rather than typing over a blank field.
     *
     * Uses JLine3's LineReader.readLine(prompt, mask, buffer):
     *   - prompt      = label shown before the text
     *   - mask        = null (no password masking)
     *   - buffer      = pre-filled text the user can edit
     *
     * Keyboard shortcuts available to the user:
     *   Arrow Left/Right  = move cursor
     *   Ctrl+A            = jump to start of line
     *   Ctrl+E            = jump to end of line
     *   Backspace/Delete  = delete characters
     *   Enter             = confirm and return
     *
     * Falls back gracefully if JLine terminal is unavailable.
     *
     * @param promptLabel   Label shown before the editable text (e.g. "  Title: ")
     * @param existingValue Current text to pre-fill in the buffer
     * @return The text after user edits. Returns existingValue if user pressed
     *         Enter without changes. Returns "" if user cleared the field.
     */
    public static String promptWithPrefill(String promptLabel, String existingValue) {
        String prefill = existingValue != null ? existingValue : "";
        try {
            Terminal terminal = TerminalBuilder.builder()
                    .system(true)
                    .dumb(false)
                    .build();
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .build();

            // readLine with a pre-filled buffer — user sees and can edit the existing text
            String result = reader.readLine(promptLabel, null, prefill);
            terminal.close();
            return result != null ? result : prefill;

        } catch (Exception e) {
            // Fallback: show current value, let user type a replacement
            System.out.println();
            System.out.println("  Current: " + prefill);
            System.out.print("  " + promptLabel + "(Enter to keep, or type new value): ");
            String input = scanner.nextLine();
            if (input == null || input.isBlank()) {
                return prefill;
            }
            return input;
        }
    }
}
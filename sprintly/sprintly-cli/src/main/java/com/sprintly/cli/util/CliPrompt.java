package com.sprintly.cli.util;

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
            return scanner.nextLine(); // Fallback if no console is available
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
}

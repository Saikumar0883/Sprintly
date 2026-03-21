package com.sprintly.cli.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

/**
 * Manages the local CLI session config stored at ~/.sprintly-cli.json
 *
 * Stores:
 *   accessToken  — JWT for API calls
 *   refreshToken — for token renewal
 *   name         — logged-in user's name (for "already logged in" message)
 *   email        — logged-in user's email (for "already logged in" message)
 */
@Data
public class CliConfig {

    private String accessToken;
    private String refreshToken;
    private String name;    // ← NEW: saved on login/register for display
    private String email;   // ← NEW: saved on login/register for display

    private static final String CONFIG_FILE = ".sprintly-cli.json";
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Save session after login or register.
     * Now also saves name + email so commands can show "logged in as X".
     */
    public static void save(String accessToken, String refreshToken,
                            String name, String email) {
        CliConfig config = new CliConfig();
        config.setAccessToken(accessToken);
        config.setRefreshToken(refreshToken);
        config.setName(name);
        config.setEmail(email);
        try {
            mapper.writeValue(getConfigFile(), config);
        } catch (IOException e) {
            System.err.println("Failed to save config: " + e.getMessage());
        }
    }

    /**
     * Load the saved session config.
     * Returns null if the file doesn't exist or can't be parsed.
     */
    public static CliConfig load() {
        File file = getConfigFile();
        if (!file.exists()) return null;
        try {
            return mapper.readValue(file, CliConfig.class);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Delete the config file — called on logout.
     */
    public static void clear() {
        File file = getConfigFile();
        if (file.exists()) file.delete();
    }

    private static File getConfigFile() {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, CONFIG_FILE).toFile();
    }
}

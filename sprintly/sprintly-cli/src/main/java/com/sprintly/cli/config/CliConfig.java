package com.sprintly.cli.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Data
public class CliConfig {
    private String accessToken;
    private String refreshToken;

    private static final String CONFIG_FILE = ".sprintly-cli.json";
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void save(String accessToken, String refreshToken) {
        CliConfig config = new CliConfig();
        config.setAccessToken(accessToken);
        config.setRefreshToken(refreshToken);
        try {
            mapper.writeValue(getConfigFile(), config);
        } catch (IOException e) {
            System.err.println("Failed to save config: " + e.getMessage());
        }
    }

    public static CliConfig load() {
        File file = getConfigFile();
        if (!file.exists()) {
            return null;
        }
        try {
            return mapper.readValue(file, CliConfig.class);
        } catch (IOException e) {
            return null;
        }
    }

    public static void clear() {
        File file = getConfigFile();
        if (file.exists()) {
            file.delete();
        }
    }

    private static File getConfigFile() {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, CONFIG_FILE).toFile();
    }
}

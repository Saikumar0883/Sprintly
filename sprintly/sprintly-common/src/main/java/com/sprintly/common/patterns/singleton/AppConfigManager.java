package com.sprintly.common.patterns.singleton;

import java.util.HashMap;
import java.util.Map;

/**
 * Design Pattern: Singleton (Thread-Safe, Lazy Initialization)
 *
 * Purpose:
 *   Central runtime configuration store shared across the entire application.
 *   Prevents duplicate config objects and provides a single source of truth.
 *
 * Implementation:
 *   Uses the "double-checked locking" idiom with volatile for thread safety.
 *   The JVM guarantees that volatile writes are visible across all threads.
 *
 * Interview Note:
 *   "Why not just use @Value or @ConfigurationProperties?"
 *   → Those are Spring-managed. This Singleton is framework-agnostic and can
 *     be accessed even before the Spring context is fully initialized, or in
 *     utility/helper classes that aren't Spring beans.
 */
public class AppConfigManager {

    // volatile ensures the instance reference is always read from main memory,
    // preventing instruction reordering issues during construction.
    private static volatile AppConfigManager instance;

    private final Map<String, String> configStore;

    /** Private constructor — prevents external instantiation */
    private AppConfigManager() {
        configStore = new HashMap<>();
        loadDefaults();
    }

    /**
     * Double-checked locking: only synchronizes on the first call.
     * Subsequent calls skip the synchronized block entirely (fast path).
     */
    public static AppConfigManager getInstance() {
        if (instance == null) {                        // First check (no lock)
            synchronized (AppConfigManager.class) {
                if (instance == null) {                // Second check (with lock)
                    instance = new AppConfigManager();
                }
            }
        }
        return instance;
    }

    private void loadDefaults() {
        configStore.put("app.name", "TaskFlow");
        configStore.put("app.version", "1.0.0");
        configStore.put("app.max-tasks-per-user", "100");
        configStore.put("app.pagination.default-size", "20");
        configStore.put("app.pagination.max-size", "100");
    }

    public String get(String key) {
        return configStore.getOrDefault(key, "");
    }

    public String get(String key, String defaultValue) {
        return configStore.getOrDefault(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(configStore.getOrDefault(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public void set(String key, String value) {
        configStore.put(key, value);
    }
}

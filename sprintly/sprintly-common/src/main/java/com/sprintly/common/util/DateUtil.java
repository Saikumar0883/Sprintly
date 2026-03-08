package com.sprintly.common.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Date/time helpers used across modules */
public final class DateUtil {

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private DateUtil() {}

    public static String format(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DISPLAY_FORMAT) : "";
    }

    public static boolean isOverdue(LocalDateTime dueDate) {
        return dueDate != null && LocalDateTime.now().isAfter(dueDate);
    }
}

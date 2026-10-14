package com.vsms.util;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Helpers for turning search-form values into query parameters. */
public final class Filters {

    private Filters() {
    }

    /** "active" -> true, "inactive" -> false, anything else -> no filter (null). */
    public static Boolean active(String status) {
        if ("active".equalsIgnoreCase(status)) {
            return Boolean.TRUE;
        }
        if ("inactive".equalsIgnoreCase(status)) {
            return Boolean.FALSE;
        }
        return null;
    }

    /** First moment of the day, or null when no date was chosen. */
    public static LocalDateTime startOf(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }

    /** First moment of the day after {@code date}, so a "to" date includes the whole day. */
    public static LocalDateTime endOfInclusive(LocalDate date) {
        return date == null ? null : date.plusDays(1).atStartOfDay();
    }
}

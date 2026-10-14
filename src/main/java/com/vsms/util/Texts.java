package com.vsms.util;

import java.util.Locale;

/** Small helpers for cleaning up text typed into forms and search boxes. */
public final class Texts {

    private Texts() {
    }

    /** Trims the text; returns null when nothing is left. */
    public static String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Trims and collapses repeated spaces, e.g. "  John   Smith " becomes "John Smith". */
    public static String clean(String text) {
        String trimmed = trimToNull(text);
        return trimmed == null ? null : trimmed.replaceAll("\\s+", " ");
    }

    /** Search pattern for a SQL LIKE: "%text%" in lower case, or null for an empty search. */
    public static String like(String search) {
        String cleaned = clean(search);
        return cleaned == null ? null : "%" + cleaned.toLowerCase(Locale.ROOT) + "%";
    }

    /** Like {@link #like} but ignoring spaces and hyphens, for matching registration numbers. */
    public static String likeRegistration(String search) {
        String cleaned = clean(search);
        if (cleaned == null) {
            return null;
        }
        return "%" + cleaned.replaceAll("[\\s-]", "").toLowerCase(Locale.ROOT) + "%";
    }

    /** Registration numbers are stored upper-case without spaces or hyphens. */
    public static String normalizeRegistration(String registration) {
        String cleaned = trimToNull(registration);
        return cleaned == null ? null : cleaned.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    /**
     * Reads a record number from search text such as "INV-000012", "JC-7" or "15".
     * Returns null when the text has no digits or is not purely a number with an optional prefix.
     */
    public static Long numberFromSearch(String search) {
        String cleaned = trimToNull(search);
        if (cleaned == null) {
            return null;
        }
        String digits = cleaned.replaceFirst("^(?i)[a-z]{2,4}-?", "");
        if (!digits.matches("\\d{1,15}")) {
            return null;
        }
        return Long.parseLong(digits);
    }
}

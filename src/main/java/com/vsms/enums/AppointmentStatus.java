package com.vsms.enums;

import java.util.EnumSet;
import java.util.Set;

public enum AppointmentStatus {
    SCHEDULED("Scheduled", "status-scheduled"),
    IN_PROGRESS("In progress", "status-in-progress"),
    COMPLETED("Completed", "status-completed"),
    CANCELLED("Cancelled", "status-cancelled");

    /** Statuses that occupy a time slot. */
    public static final Set<AppointmentStatus> ACTIVE = EnumSet.of(SCHEDULED, IN_PROGRESS);
    /** Everything except cancelled appointments. */
    public static final Set<AppointmentStatus> NOT_CANCELLED = EnumSet.of(SCHEDULED, IN_PROGRESS, COMPLETED);

    private final String label;
    private final String badgeClass;

    AppointmentStatus(String label, String badgeClass) {
        this.label = label;
        this.badgeClass = badgeClass;
    }

    public String getLabel() {
        return label;
    }

    public String getBadgeClass() {
        return badgeClass;
    }

    /** Active appointments occupy their time slot. */
    public boolean isActive() {
        return this == SCHEDULED || this == IN_PROGRESS;
    }
}

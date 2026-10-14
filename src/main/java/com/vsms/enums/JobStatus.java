package com.vsms.enums;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle of a service job.
 *
 * <pre>
 * CREATED -> ASSIGNED -> IN_PROGRESS <-> ON_HOLD
 *                             |
 *                             v
 *                        COMPLETED -> INVOICED -> DELIVERED
 * CREATED / ASSIGNED -> CANCELLED
 * </pre>
 */
public enum JobStatus {
    CREATED("Created", "status-created"),
    ASSIGNED("Assigned", "status-assigned"),
    IN_PROGRESS("In progress", "status-in-progress"),
    ON_HOLD("On hold", "status-on-hold"),
    COMPLETED("Completed", "status-completed"),
    INVOICED("Invoiced", "status-invoiced"),
    DELIVERED("Delivered", "status-delivered"),
    CANCELLED("Cancelled", "status-cancelled");

    private static final Map<JobStatus, Set<JobStatus>> NEXT = new EnumMap<>(JobStatus.class);

    static {
        NEXT.put(CREATED, EnumSet.of(ASSIGNED, CANCELLED));
        NEXT.put(ASSIGNED, EnumSet.of(IN_PROGRESS, CANCELLED));
        NEXT.put(IN_PROGRESS, EnumSet.of(ON_HOLD, COMPLETED));
        NEXT.put(ON_HOLD, EnumSet.of(IN_PROGRESS));
        NEXT.put(COMPLETED, EnumSet.of(INVOICED));
        NEXT.put(INVOICED, EnumSet.of(DELIVERED));
        NEXT.put(DELIVERED, EnumSet.noneOf(JobStatus.class));
        NEXT.put(CANCELLED, EnumSet.noneOf(JobStatus.class));
    }

    private final String label;
    private final String badgeClass;

    JobStatus(String label, String badgeClass) {
        this.label = label;
        this.badgeClass = badgeClass;
    }

    public String getLabel() {
        return label;
    }

    public String getBadgeClass() {
        return badgeClass;
    }

    public Set<JobStatus> allowedNext() {
        return Set.copyOf(NEXT.get(this));
    }

    public boolean canTransitionTo(JobStatus target) {
        return NEXT.get(this).contains(target);
    }

    /** Cancellation is only possible before any work has started. */
    public boolean isCancellable() {
        return this == CREATED || this == ASSIGNED;
    }

    /** Details, tasks and parts can still be changed (until the job is invoiced). */
    public boolean isEditable() {
        return this == CREATED || this == ASSIGNED || this == IN_PROGRESS
                || this == ON_HOLD || this == COMPLETED;
    }

    /** Pending work for the dashboard: not started yet. */
    public boolean isPending() {
        return this == CREATED || this == ASSIGNED;
    }

    /** Work currently with the workshop. */
    public boolean isWorkInProgress() {
        return this == IN_PROGRESS || this == ON_HOLD;
    }

    /** Work finished (whether or not it is already invoiced or delivered). */
    public boolean isFinished() {
        return this == COMPLETED || this == INVOICED || this == DELIVERED;
    }

    /** Position on the progress bar of the job card (-1 for cancelled jobs, which have no progress bar). */
    public int getStepIndex() {
        return switch (this) {
            case CREATED -> 0;
            case ASSIGNED -> 1;
            case IN_PROGRESS, ON_HOLD -> 2;
            case COMPLETED -> 3;
            case INVOICED -> 4;
            case DELIVERED -> 5;
            case CANCELLED -> -1;
        };
    }

    /** The steps shown on the job card progress bar, in order. */
    public static List<JobStatus> progressSteps() {
        return List.of(CREATED, ASSIGNED, IN_PROGRESS, COMPLETED, INVOICED, DELIVERED);
    }

    public static final Set<JobStatus> PENDING = EnumSet.of(CREATED, ASSIGNED);
    public static final Set<JobStatus> IN_WORK = EnumSet.of(IN_PROGRESS, ON_HOLD);
    public static final Set<JobStatus> FINISHED = EnumSet.of(COMPLETED, INVOICED, DELIVERED);
    /** Statuses in which a mechanic is still responsible for the job. */
    public static final Set<JobStatus> MECHANIC_OPEN = EnumSet.of(ASSIGNED, IN_PROGRESS, ON_HOLD);
}

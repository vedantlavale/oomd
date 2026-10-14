package com.vsms.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JobStatusTest {

    /** The complete list of allowed moves; everything else must be rejected. */
    private static final Map<JobStatus, Set<JobStatus>> ALLOWED = new EnumMap<>(JobStatus.class);

    static {
        ALLOWED.put(JobStatus.CREATED, EnumSet.of(JobStatus.ASSIGNED, JobStatus.CANCELLED));
        ALLOWED.put(JobStatus.ASSIGNED, EnumSet.of(JobStatus.IN_PROGRESS, JobStatus.CANCELLED));
        ALLOWED.put(JobStatus.IN_PROGRESS, EnumSet.of(JobStatus.ON_HOLD, JobStatus.COMPLETED));
        ALLOWED.put(JobStatus.ON_HOLD, EnumSet.of(JobStatus.IN_PROGRESS));
        ALLOWED.put(JobStatus.COMPLETED, EnumSet.of(JobStatus.INVOICED));
        ALLOWED.put(JobStatus.INVOICED, EnumSet.of(JobStatus.DELIVERED));
        ALLOWED.put(JobStatus.DELIVERED, EnumSet.noneOf(JobStatus.class));
        ALLOWED.put(JobStatus.CANCELLED, EnumSet.noneOf(JobStatus.class));
    }

    @Test
    void everyPairOfStatusesFollowsTheTransitionTable() {
        for (JobStatus from : JobStatus.values()) {
            for (JobStatus to : JobStatus.values()) {
                assertEquals(ALLOWED.get(from).contains(to), from.canTransitionTo(to),
                        from + " -> " + to);
            }
        }
    }

    @Test
    void theHappyPathIsAllowedStepByStep() {
        JobStatus[] path = {JobStatus.CREATED, JobStatus.ASSIGNED, JobStatus.IN_PROGRESS, JobStatus.ON_HOLD,
                JobStatus.IN_PROGRESS, JobStatus.COMPLETED, JobStatus.INVOICED, JobStatus.DELIVERED};
        for (int i = 0; i < path.length - 1; i++) {
            assertTrue(path[i].canTransitionTo(path[i + 1]), path[i] + " -> " + path[i + 1]);
        }
    }

    @Test
    void cancellationIsOnlyPossibleInEarlyStates() {
        for (JobStatus status : JobStatus.values()) {
            boolean early = status == JobStatus.CREATED || status == JobStatus.ASSIGNED;
            assertEquals(early, status.canTransitionTo(JobStatus.CANCELLED), status.name());
            assertEquals(early, status.isCancellable(), status.name());
        }
    }

    @Test
    void finalStatesHaveNoWayOut() {
        assertTrue(JobStatus.DELIVERED.allowedNext().isEmpty());
        assertTrue(JobStatus.CANCELLED.allowedNext().isEmpty());
    }

    @Test
    void invoicedAndLaterJobsCannotBeEdited() {
        assertTrue(JobStatus.COMPLETED.isEditable());
        assertFalse(JobStatus.INVOICED.isEditable());
        assertFalse(JobStatus.DELIVERED.isEditable());
        assertFalse(JobStatus.CANCELLED.isEditable());
    }

    @Test
    void dashboardGroupsDoNotOverlap() {
        for (JobStatus status : JobStatus.values()) {
            int groups = (JobStatus.PENDING.contains(status) ? 1 : 0) + (JobStatus.IN_WORK.contains(status) ? 1 : 0)
                    + (JobStatus.FINISHED.contains(status) ? 1 : 0);
            assertTrue(groups <= 1, status + " belongs to more than one dashboard group");
        }
    }
}

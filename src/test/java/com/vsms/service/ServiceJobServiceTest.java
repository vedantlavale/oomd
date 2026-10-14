package com.vsms.service;

import static com.vsms.support.Rules.assertRejected;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vsms.AbstractIntegrationTest;
import com.vsms.entity.Appointment;
import com.vsms.entity.Mechanic;
import com.vsms.entity.PartUsage;
import com.vsms.entity.ServiceJob;
import com.vsms.entity.SparePart;
import com.vsms.enums.AppointmentStatus;
import com.vsms.enums.JobStatus;
import com.vsms.repository.SparePartRepository;
import com.vsms.security.Actor;
import com.vsms.support.TestData;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ServiceJobServiceTest extends AbstractIntegrationTest {

    @Autowired
    ServiceJobService jobService;
    @Autowired
    AppointmentService appointmentService;
    @Autowired
    SparePartRepository partRepository;
    @Autowired
    TestData data;

    private final Actor staff = new TestData(null, null, null, null, null, null, null).staff();

    // ------------------------------------------------------------------ creation

    @Test
    void jobIsCreatedFromAScheduledAppointment() {
        Appointment appointment = data.scheduledAppointment();
        ServiceJob job = jobService.createFromAppointment(data.jobForm(appointment, null, "750.00"), data.staff());

        assertEquals(JobStatus.CREATED, job.getStatus());
        assertEquals(new BigDecimal("750.00"), job.getLaborCharge());
        assertNull(job.getMechanic());
        assertEquals(AppointmentStatus.IN_PROGRESS, appointmentService.get(appointment.getId()).getStatus());
    }

    @Test
    void aJobCreatedWithAMechanicStartsAsAssigned() {
        Mechanic mechanic = data.mechanic();
        ServiceJob job = data.assignedJob(mechanic, "100.00");
        assertEquals(JobStatus.ASSIGNED, job.getStatus());
        assertEquals(mechanic.getId(), job.getMechanic().getId());
    }

    @Test
    void anAppointmentGetsOnlyOneJobCard() {
        Appointment appointment = data.scheduledAppointment();
        jobService.createFromAppointment(data.jobForm(appointment, null, "100.00"), data.staff());
        assertRejected("scheduled appointment", () -> jobService.createFromAppointment(data.jobForm(appointment, null, "100.00"), data.staff()));
    }

    @Test
    void cancelledAppointmentsCannotBecomeJobs() {
        Appointment appointment = data.scheduledAppointment();
        appointmentService.cancel(appointment.getId());
        assertRejected("scheduled appointment", () -> jobService.createFromAppointment(data.jobForm(appointment, null, "100.00"), data.staff()));
    }

    @Test
    void mechanicsCannotCreateJobs() {
        Mechanic mechanic = data.mechanic();
        Appointment appointment = data.scheduledAppointment();
        assertThrows(AccessDeniedException.class,
                () -> jobService.createFromAppointment(data.jobForm(appointment, null, "100.00"), data.mechanicActor(mechanic)));
    }

    // ---------------------------------------------------------------- assignment

    @Test
    void assigningAMechanicMovesACreatedJobToAssigned() {
        ServiceJob job = data.job("100.00");
        Mechanic mechanic = data.mechanic();
        jobService.assignMechanic(job.getId(), mechanic.getId(), data.staff());
        assertEquals(JobStatus.ASSIGNED, job.getStatus());
        assertEquals(mechanic.getId(), job.getMechanic().getId());
    }

    @Test
    void deactivatedMechanicsCannotBeAssigned() {
        ServiceJob job = data.job("100.00");
        Mechanic mechanic = data.mechanic();
        mechanic.setActive(false);
        assertRejected("deactivated", () -> jobService.assignMechanic(job.getId(), mechanic.getId(), data.staff()));
    }

    @Test
    void theMechanicCanBeChangedWhileWorkIsOngoingButNotAfterCompletion() {
        Mechanic first = data.mechanic();
        Mechanic second = data.mechanic();
        ServiceJob job = data.assignedJob(first, "100.00");
        jobService.changeStatus(job.getId(), JobStatus.IN_PROGRESS, data.staff());
        jobService.assignMechanic(job.getId(), second.getId(), data.staff());
        assertEquals(second.getId(), job.getMechanic().getId());

        jobService.changeStatus(job.getId(), JobStatus.COMPLETED, data.staff());
        assertRejected("can no longer be changed", () -> jobService.assignMechanic(job.getId(), first.getId(), data.staff()));
    }

    // ------------------------------------------------------------------- statuses

    @Test
    void aJobWalksThroughTheWholeLifecycle() {
        Mechanic mechanic = data.mechanic();
        Actor worker = data.mechanicActor(mechanic);
        ServiceJob job = data.job("100.00");
        jobService.assignMechanic(job.getId(), mechanic.getId(), data.staff());

        jobService.changeStatus(job.getId(), JobStatus.IN_PROGRESS, worker);
        assertNotNull(job.getStartedAt());
        jobService.changeStatus(job.getId(), JobStatus.ON_HOLD, worker);
        jobService.changeStatus(job.getId(), JobStatus.IN_PROGRESS, worker);
        jobService.changeStatus(job.getId(), JobStatus.COMPLETED, worker);
        assertNotNull(job.getCompletedAt());
        assertEquals(AppointmentStatus.COMPLETED, job.getAppointment().getStatus());
    }

    @Test
    void invalidTransitionsAreRejected() {
        ServiceJob created = data.job("100.00");
        assertRejected("cannot be changed", () -> jobService.changeStatus(created.getId(), JobStatus.IN_PROGRESS, data.staff()));
        assertRejected("cannot be changed", () -> jobService.changeStatus(created.getId(), JobStatus.COMPLETED, data.staff()));
        assertRejected("cannot be changed", () -> jobService.changeStatus(created.getId(), JobStatus.DELIVERED, data.staff()));

        ServiceJob assigned = data.assignedJob(data.mechanic(), "100.00");
        assertRejected("cannot be changed", () -> jobService.changeStatus(assigned.getId(), JobStatus.COMPLETED, data.staff()));
        assertRejected("cannot be changed", () -> jobService.changeStatus(assigned.getId(), JobStatus.ON_HOLD, data.staff()));
    }

    @Test
    void invoicedAndAssignedCannotBeSetByHand() {
        ServiceJob job = data.completedJob("100.00");
        assertRejected("automatically", () -> jobService.changeStatus(job.getId(), JobStatus.INVOICED, data.staff()));
        ServiceJob created = data.job("100.00");
        assertRejected("Choose a mechanic", () -> jobService.changeStatus(created.getId(), JobStatus.ASSIGNED, data.staff()));
    }

    @Test
    void cancellationIsOnlyPossibleBeforeWorkStarts() {
        ServiceJob created = data.job("100.00");
        jobService.changeStatus(created.getId(), JobStatus.CANCELLED, data.staff());
        assertEquals(JobStatus.CANCELLED, created.getStatus());
        assertEquals(AppointmentStatus.CANCELLED, created.getAppointment().getStatus());

        ServiceJob assigned = data.assignedJob(data.mechanic(), "100.00");
        jobService.changeStatus(assigned.getId(), JobStatus.CANCELLED, data.staff());
        assertEquals(JobStatus.CANCELLED, assigned.getStatus());

        ServiceJob started = data.assignedJob(data.mechanic(), "100.00");
        jobService.changeStatus(started.getId(), JobStatus.IN_PROGRESS, data.staff());
        assertRejected("cannot be changed", () -> jobService.changeStatus(started.getId(), JobStatus.CANCELLED, data.staff()));
    }

    @Test
    void cancelledJobsAreFinal() {
        ServiceJob job = data.job("100.00");
        jobService.changeStatus(job.getId(), JobStatus.CANCELLED, data.staff());
        assertRejected("cannot be changed", () -> jobService.changeStatus(job.getId(), JobStatus.ASSIGNED, data.staff()));
        assertRejected("cannot be changed", () -> jobService.changeStatus(job.getId(), JobStatus.IN_PROGRESS, data.staff()));
    }

    @Test
    void cancellingAJobPutsItsPartsBackInStock() {
        ServiceJob job = data.assignedJob(data.mechanic(), "100.00");
        SparePart part = data.part(10, "50.00");
        jobService.addPart(job.getId(), part.getId(), 4, data.staff());
        assertEquals(6, partRepository.findById(part.getId()).orElseThrow().getStockQuantity());

        jobService.changeStatus(job.getId(), JobStatus.CANCELLED, data.staff());
        assertEquals(10, partRepository.findById(part.getId()).orElseThrow().getStockQuantity());
        assertFalse(job.getPartUsages().get(0).isStockDeducted());
    }

    // ---------------------------------------------------------- who may do what

    @Test
    void onlyTheAssignedMechanicCanWorkOnAJob() {
        Mechanic owner = data.mechanic();
        Mechanic other = data.mechanic();
        ServiceJob job = data.assignedJob(owner, "100.00");

        assertThrows(AccessDeniedException.class, () -> jobService.changeStatus(job.getId(), JobStatus.IN_PROGRESS, data.mechanicActor(other)));
        assertThrows(AccessDeniedException.class, () -> jobService.get(job.getId(), data.mechanicActor(other)));
        assertThrows(AccessDeniedException.class, () -> jobService.addTask(job.getId(), "Sneaky", data.mechanicActor(other)));

        jobService.changeStatus(job.getId(), JobStatus.IN_PROGRESS, data.mechanicActor(owner));
        assertEquals(JobStatus.IN_PROGRESS, job.getStatus());
    }

    @Test
    void mechanicsCannotCancelAssignOrDeliver() {
        Mechanic owner = data.mechanic();
        Actor worker = data.mechanicActor(owner);
        ServiceJob job = data.assignedJob(owner, "100.00");
        assertThrows(AccessDeniedException.class, () -> jobService.changeStatus(job.getId(), JobStatus.CANCELLED, worker));
        assertThrows(AccessDeniedException.class, () -> jobService.assignMechanic(job.getId(), data.mechanic().getId(), worker));
    }

    @Test
    void aMechanicOnlySeesTheirOwnJobsInSearch() {
        Mechanic mine = data.mechanic();
        Mechanic theirs = data.mechanic();
        ServiceJob myJob = data.assignedJob(mine, "100.00");
        ServiceJob otherJob = data.assignedJob(theirs, "100.00");

        var visible = jobService.search(null, null, null, null, null, data.mechanicActor(mine), 0).getContent();
        assertTrue(visible.stream().anyMatch(j -> j.getId().equals(myJob.getId())));
        assertFalse(visible.stream().anyMatch(j -> j.getId().equals(otherJob.getId())));

        var staffView = jobService.search(null, null, null, null, null, data.staff(), 0).getContent();
        assertTrue(staffView.stream().anyMatch(j -> j.getId().equals(otherJob.getId())));
    }

    @Test
    void aMechanicCannotChangeACompletedJob() {
        Mechanic owner = data.mechanic();
        Actor worker = data.mechanicActor(owner);
        ServiceJob job = data.assignedJob(owner, "100.00");
        jobService.changeStatus(job.getId(), JobStatus.IN_PROGRESS, worker);
        jobService.changeStatus(job.getId(), JobStatus.COMPLETED, worker);
        assertRejected("completed", () -> jobService.addTask(job.getId(), "Too late", worker));
        // but the advisor still can
        jobService.addTask(job.getId(), "Advisor addition", data.staff());
    }

    // -------------------------------------------------------------------- parts

    @Test
    void usingAPartTakesItOutOfStockOnce() {
        ServiceJob job = data.assignedJob(data.mechanic(), "100.00");
        SparePart part = data.part(10, "125.50");
        PartUsage usage = jobService.addPart(job.getId(), part.getId(), 3, data.staff());

        assertEquals(7, partRepository.findById(part.getId()).orElseThrow().getStockQuantity());
        assertEquals(3, usage.getQuantity());
        assertTrue(usage.isStockDeducted());
        assertEquals(new BigDecimal("376.50"), usage.getLineTotal());
        assertEquals(new BigDecimal("376.50"), job.getPartsTotal());
    }

    @Test
    void notEnoughStockIsRejectedAndStockIsUntouched() {
        ServiceJob job = data.assignedJob(data.mechanic(), "100.00");
        SparePart part = data.part(2, "10.00");
        assertRejected("quantity", "Only 2", () -> jobService.addPart(job.getId(), part.getId(), 3, data.staff()));
        assertEquals(2, partRepository.findById(part.getId()).orElseThrow().getStockQuantity());
        assertTrue(job.getPartUsages().isEmpty());
    }

    @Test
    void zeroAndNegativeQuantitiesAreRejected() {
        ServiceJob job = data.assignedJob(data.mechanic(), "100.00");
        SparePart part = data.part(5, "10.00");
        assertRejected("quantity", "at least 1", () -> jobService.addPart(job.getId(), part.getId(), 0, data.staff()));
        assertRejected("quantity", "at least 1", () -> jobService.addPart(job.getId(), part.getId(), -4, data.staff()));
        assertEquals(5, partRepository.findById(part.getId()).orElseThrow().getStockQuantity());
    }

    @Test
    void usingAllStockIsAllowedAndNeverGoesNegative() {
        ServiceJob job = data.assignedJob(data.mechanic(), "100.00");
        SparePart part = data.part(3, "10.00");
        jobService.addPart(job.getId(), part.getId(), 3, data.staff());
        assertEquals(0, partRepository.findById(part.getId()).orElseThrow().getStockQuantity());
        assertRejected("Only 0", () -> jobService.addPart(job.getId(), part.getId(), 1, data.staff()));
    }

    @Test
    void theChargedPriceIsKeptWhenTheCataloguePriceChanges() {
        ServiceJob job = data.assignedJob(data.mechanic(), "100.00");
        SparePart part = data.part(10, "100.00");
        PartUsage usage = jobService.addPart(job.getId(), part.getId(), 2, data.staff());

        part.setUnitPrice(new BigDecimal("999.00"));
        partRepository.saveAndFlush(part);

        assertEquals(new BigDecimal("100.00"), usage.getUnitPriceAtUse());
        assertEquals(new BigDecimal("200.00"), job.getPartsTotal());
        PartUsage later = jobService.addPart(job.getId(), part.getId(), 1, data.staff());
        assertEquals(new BigDecimal("999.00"), later.getUnitPriceAtUse());
        assertEquals(new BigDecimal("1199.00"), job.getPartsTotal());
    }

    @Test
    void removingAPartRestoresStockExactlyOnce() {
        ServiceJob job = data.assignedJob(data.mechanic(), "100.00");
        SparePart part = data.part(10, "10.00");
        jobService.addPart(job.getId(), part.getId(), 4, data.staff());
        partRepository.flush();
        Long usageId = job.getPartUsages().get(0).getId();
        assertNotNull(usageId);

        jobService.removePart(job.getId(), usageId, data.staff());
        assertEquals(10, partRepository.findById(part.getId()).orElseThrow().getStockQuantity());
        assertTrue(job.getPartUsages().isEmpty());
        assertThrows(com.vsms.exception.ResourceNotFoundException.class,
                () -> jobService.removePart(job.getId(), usageId, data.staff()));
        assertEquals(10, partRepository.findById(part.getId()).orElseThrow().getStockQuantity());
    }

    @Test
    void partsCannotBeChangedOnceTheJobIsInvoiced() {
        ServiceJob job = data.completedJob("100.00");
        SparePart part = data.part(5, "10.00");
        jobService.addPart(job.getId(), part.getId(), 1, data.staff()); // still allowed while COMPLETED
        job.setStatus(JobStatus.INVOICED);
        assertRejected("can no longer be changed", () -> jobService.addPart(job.getId(), part.getId(), 1, data.staff()));
    }

    // -------------------------------------------------------------------- tasks

    @Test
    void tasksCanBeAddedTickedAndRemoved() {
        ServiceJob job = data.assignedJob(data.mechanic(), "100.00");
        var task = jobService.addTask(job.getId(), "  Replace   oil filter ", data.staff());
        assertEquals("Replace oil filter", task.getDescription());
        assertFalse(task.isCompleted());

        partRepository.flush();
        Long taskId = job.getTasks().get(0).getId();
        assertTrue(jobService.toggleTask(job.getId(), taskId, data.staff()).isCompleted());
        assertEquals(1, job.getCompletedTaskCount());
        assertFalse(jobService.toggleTask(job.getId(), taskId, data.staff()).isCompleted());

        jobService.removeTask(job.getId(), taskId, data.staff());
        assertTrue(job.getTasks().isEmpty());
    }

    @Test
    void notesCanBeSavedByTheAssignedMechanic() {
        Mechanic owner = data.mechanic();
        ServiceJob job = data.assignedJob(owner, "100.00");
        var form = new com.vsms.dto.JobNotesForm();
        form.setDiagnosis("  Worn belt ");
        form.setRepairNotes("");
        jobService.updateNotes(job.getId(), form, data.mechanicActor(owner));
        assertEquals("Worn belt", job.getDiagnosis());
        assertNull(job.getRepairNotes());
    }
}

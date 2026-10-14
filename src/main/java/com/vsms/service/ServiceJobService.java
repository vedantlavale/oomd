package com.vsms.service;

import com.vsms.dto.JobCreateForm;
import com.vsms.dto.JobEditForm;
import com.vsms.dto.JobNotesForm;
import com.vsms.entity.Appointment;
import com.vsms.entity.Invoice;
import com.vsms.entity.Mechanic;
import com.vsms.entity.PartUsage;
import com.vsms.entity.ServiceJob;
import com.vsms.entity.ServiceTask;
import com.vsms.entity.SparePart;
import com.vsms.enums.AppointmentStatus;
import com.vsms.enums.JobStatus;
import com.vsms.enums.PaymentStatus;
import com.vsms.exception.BusinessRuleException;
import com.vsms.exception.ResourceNotFoundException;
import com.vsms.repository.AppointmentRepository;
import com.vsms.repository.InvoiceRepository;
import com.vsms.repository.MechanicRepository;
import com.vsms.repository.ServiceJobRepository;
import com.vsms.repository.SparePartRepository;
import com.vsms.security.Actor;
import com.vsms.util.Filters;
import com.vsms.util.MoneyUtil;
import com.vsms.util.Paging;
import com.vsms.util.Texts;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Everything that happens to a job card: creating it, assigning a mechanic, moving it through its
 * statuses, and recording tasks and spare parts (with stock).
 */
@Service
@Transactional
public class ServiceJobService {

    /** Statuses in which a mechanic may still be (re)assigned. */
    private static final Set<JobStatus> ASSIGNABLE = EnumSet.of(
            JobStatus.CREATED, JobStatus.ASSIGNED, JobStatus.IN_PROGRESS, JobStatus.ON_HOLD);
    /** The only status changes a mechanic may make themselves. */
    private static final Set<JobStatus> MECHANIC_TARGETS = EnumSet.of(
            JobStatus.IN_PROGRESS, JobStatus.ON_HOLD, JobStatus.COMPLETED);

    private final ServiceJobRepository jobs;
    private final AppointmentRepository appointments;
    private final MechanicRepository mechanics;
    private final SparePartRepository parts;
    private final InvoiceRepository invoices;
    private final Clock clock;

    public ServiceJobService(ServiceJobRepository jobs, AppointmentRepository appointments,
                             MechanicRepository mechanics, SparePartRepository parts,
                             InvoiceRepository invoices, Clock clock) {
        this.jobs = jobs;
        this.appointments = appointments;
        this.mechanics = mechanics;
        this.parts = parts;
        this.invoices = invoices;
        this.clock = clock;
    }

    /** A button on the job card that moves the job to another status. */
    public record StatusAction(JobStatus target, String label, String icon, String variant,
                               String confirm, boolean enabled, String disabledReason) {
    }

    /** Everything the job card page needs, including what the signed-in person may do. */
    public record JobCard(ServiceJob job, Invoice invoice, List<StatusAction> actions,
                          boolean canEditDetails, boolean canWork, boolean canAssign, boolean canInvoice) {
    }

    // ------------------------------------------------------------------ reading

    @Transactional(readOnly = true)
    public Page<ServiceJob> search(String query, JobStatus status, Long mechanicId, LocalDate from, LocalDate to,
                                   Actor actor, int page) {
        Long mechanicFilter = mechanicId;
        if (actor != null && actor.isMechanic()) {
            // A mechanic only ever sees their own jobs.
            mechanicFilter = actor.mechanicId() != null ? actor.mechanicId() : -1L;
        }
        return jobs.search(Texts.like(query), Texts.likeRegistration(query), Texts.numberFromSearch(query),
                status, mechanicFilter, Filters.startOf(from), Filters.endOfInclusive(to),
                Paging.of(page, Sort.by(Sort.Order.desc("id"))));
    }

    @Transactional(readOnly = true)
    public ServiceJob get(Long id, Actor actor) {
        ServiceJob job = find(id);
        requireWorkAccess(job, actor);
        return job;
    }

    @Transactional(readOnly = true)
    public JobCard card(Long id, Actor actor) {
        ServiceJob job = get(id, actor);
        Invoice invoice = invoices.findByServiceJobId(id).orElse(null);
        JobStatus status = job.getStatus();
        boolean staff = actor.isStaff();
        boolean canWork = status.isEditable() && (staff || (actor.isMechanic() && status != JobStatus.COMPLETED));
        return new JobCard(job, invoice, actionsFor(job, invoice, actor),
                staff && status.isEditable(),
                canWork,
                staff && ASSIGNABLE.contains(status),
                staff && status == JobStatus.COMPLETED && invoice == null);
    }

    // ----------------------------------------------------------------- creating

    /** Creates the job card for a scheduled appointment (optionally with a mechanic right away). */
    public ServiceJob createFromAppointment(JobCreateForm form, Actor actor) {
        requireStaff(actor);
        Appointment appointment = appointments.findById(form.getAppointmentId())
                .orElseThrow(() -> new BusinessRuleException("This appointment does not exist."));
        if (appointment.getStatus() != AppointmentStatus.SCHEDULED) {
            throw new BusinessRuleException("A job card can only be created for a scheduled appointment. This one is "
                    + appointment.getStatus().getLabel().toLowerCase() + ".");
        }
        if (jobs.existsByAppointmentId(appointment.getId())) {
            throw new BusinessRuleException("A job card already exists for this appointment.");
        }
        ServiceJob job = new ServiceJob();
        job.setAppointment(appointment);
        job.setCustomerComplaint(Texts.clean(form.getCustomerComplaint()));
        job.setLaborCharge(MoneyUtil.money(form.getLaborCharge()));
        job.setStatus(JobStatus.CREATED);
        if (form.getMechanicId() != null) {
            job.setMechanic(activeMechanic(form.getMechanicId(), "mechanicId"));
            job.setStatus(JobStatus.ASSIGNED);
        }
        appointment.setStatus(AppointmentStatus.IN_PROGRESS);
        try {
            return jobs.saveAndFlush(job);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessRuleException("A job card already exists for this appointment.");
        }
    }

    // ---------------------------------------------------------------- job fields

    /** Service advisor / admin: complaint and labour charge. */
    public ServiceJob updateDetails(Long jobId, JobEditForm form, Actor actor) {
        requireStaff(actor);
        ServiceJob job = lock(jobId);
        requireEditable(job);
        job.setCustomerComplaint(Texts.clean(form.getCustomerComplaint()));
        job.setLaborCharge(MoneyUtil.money(form.getLaborCharge()));
        return job;
    }

    /** Diagnosis and repair notes: staff, or the mechanic the job is assigned to. */
    public ServiceJob updateNotes(Long jobId, JobNotesForm form, Actor actor) {
        ServiceJob job = workableJob(jobId, actor);
        job.setDiagnosis(Texts.trimToNull(form.getDiagnosis()));
        job.setRepairNotes(Texts.trimToNull(form.getRepairNotes()));
        return job;
    }

    public ServiceJob assignMechanic(Long jobId, Long mechanicId, Actor actor) {
        requireStaff(actor);
        ServiceJob job = lock(jobId);
        if (!ASSIGNABLE.contains(job.getStatus())) {
            throw new BusinessRuleException("The mechanic can no longer be changed because the job is "
                    + job.getStatus().getLabel().toLowerCase() + ".");
        }
        job.setMechanic(activeMechanic(mechanicId, null));
        if (job.getStatus() == JobStatus.CREATED) {
            job.setStatus(JobStatus.ASSIGNED);
        }
        return job;
    }

    // ------------------------------------------------------------------- status

    /** Moves the job to {@code target} if the change is valid for the job and allowed for the person. */
    public ServiceJob changeStatus(Long jobId, JobStatus target, Actor actor) {
        ServiceJob job = lock(jobId);
        requireWorkAccess(job, actor);
        JobStatus current = job.getStatus();

        // Two moves are valid in the lifecycle but are never done with a status button.
        if (target == JobStatus.INVOICED && current == JobStatus.COMPLETED) {
            throw new BusinessRuleException("A job becomes \"Invoiced\" automatically when its invoice is generated.");
        }
        if (target == JobStatus.ASSIGNED && current == JobStatus.CREATED) {
            throw new BusinessRuleException("Choose a mechanic to assign this job; it then becomes \"Assigned\".");
        }
        if (!current.canTransitionTo(target)) {
            throw new BusinessRuleException("A job that is " + current.getLabel().toLowerCase()
                    + " cannot be changed to " + target.getLabel().toLowerCase() + ".");
        }
        if (actor.isMechanic() && !MECHANIC_TARGETS.contains(target)) {
            throw new AccessDeniedException("Mechanics can only start, hold, resume or complete their jobs.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        switch (target) {
            case IN_PROGRESS -> {
                if (job.getStartedAt() == null) {
                    job.setStartedAt(now);
                }
            }
            case COMPLETED -> {
                job.setCompletedAt(now);
                job.getAppointment().setStatus(AppointmentStatus.COMPLETED);
            }
            case CANCELLED -> {
                for (PartUsage usage : job.getPartUsages()) {
                    returnToStock(usage);
                }
                job.getAppointment().setStatus(AppointmentStatus.CANCELLED);
            }
            case DELIVERED -> {
                Invoice invoice = invoices.findByServiceJobId(jobId)
                        .orElseThrow(() -> new BusinessRuleException("This job has no invoice yet."));
                if (invoice.getPaymentStatus() != PaymentStatus.PAID) {
                    throw new BusinessRuleException("The vehicle can be delivered only after the invoice is fully paid. "
                            + "Outstanding balance: " + invoice.getBalanceDue().toPlainString() + ".");
                }
                job.setDeliveredAt(now);
            }
            default -> {
                // ON_HOLD needs no extra bookkeeping
            }
        }
        job.setStatus(target);
        return job;
    }

    // -------------------------------------------------------------------- tasks

    public ServiceTask addTask(Long jobId, String description, Actor actor) {
        ServiceJob job = workableJob(jobId, actor);
        ServiceTask task = new ServiceTask();
        task.setDescription(Texts.clean(description));
        job.addTask(task);
        return task;
    }

    public ServiceTask toggleTask(Long jobId, Long taskId, Actor actor) {
        ServiceJob job = workableJob(jobId, actor);
        ServiceTask task = findTask(job, taskId);
        task.setCompleted(!task.isCompleted());
        task.setCompletedAt(task.isCompleted() ? LocalDateTime.now(clock) : null);
        return task;
    }

    public void removeTask(Long jobId, Long taskId, Actor actor) {
        ServiceJob job = workableJob(jobId, actor);
        job.getTasks().remove(findTask(job, taskId));
    }

    // -------------------------------------------------------------------- parts

    /**
     * Records a part used on the job: checks and deducts stock (once, under a row lock) and keeps
     * the unit price at this moment.
     */
    public PartUsage addPart(Long jobId, Long sparePartId, int quantity, Actor actor) {
        ServiceJob job = workableJob(jobId, actor);
        if (quantity < 1) {
            throw new BusinessRuleException("quantity", "The quantity must be at least 1.");
        }
        SparePart part = parts.findByIdForUpdate(sparePartId)
                .orElseThrow(() -> new BusinessRuleException("sparePartId", "Choose an existing spare part."));
        if (part.getStockQuantity() < quantity) {
            throw new BusinessRuleException("quantity", "Only " + part.getStockQuantity() + " x " + part.getName()
                    + " in stock; you asked for " + quantity + ".");
        }
        part.setStockQuantity(part.getStockQuantity() - quantity);

        PartUsage usage = new PartUsage();
        usage.setSparePart(part);
        usage.setQuantity(quantity);
        usage.setUnitPriceAtUse(MoneyUtil.money(part.getUnitPrice()));
        usage.setStockDeducted(true);
        usage.setUsedAt(LocalDateTime.now(clock));
        job.addPartUsage(usage);
        return usage;
    }

    /** Removes a part line from the job and puts the quantity back into stock. */
    public void removePart(Long jobId, Long usageId, Actor actor) {
        ServiceJob job = workableJob(jobId, actor);
        PartUsage usage = job.getPartUsages().stream()
                .filter(candidate -> usageId.equals(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Part line", usageId));
        returnToStock(usage);
        job.getPartUsages().remove(usage);
    }

    // ------------------------------------------------------------------ helpers

    private ServiceJob find(Long id) {
        return jobs.findById(id).orElseThrow(() -> new ResourceNotFoundException("Job card", id));
    }

    /** Loads the job and locks its row so two people cannot change it at the same moment. */
    private ServiceJob lock(Long id) {
        return jobs.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Job card", id));
    }

    /** A job whose tasks, parts and notes the person may change right now. */
    private ServiceJob workableJob(Long jobId, Actor actor) {
        ServiceJob job = lock(jobId);
        requireWorkAccess(job, actor);
        requireEditable(job);
        if (actor.isMechanic() && job.getStatus() == JobStatus.COMPLETED) {
            throw new BusinessRuleException("This job is completed. Ask the service advisor to make changes.");
        }
        return job;
    }

    private void requireEditable(ServiceJob job) {
        if (!job.getStatus().isEditable()) {
            throw new BusinessRuleException("This job is " + job.getStatus().getLabel().toLowerCase()
                    + ", so it can no longer be changed.");
        }
    }

    private void requireStaff(Actor actor) {
        if (actor == null || !actor.isStaff()) {
            throw new AccessDeniedException("Only administrators and service advisors can do this.");
        }
    }

    /** Staff may work on any job; a mechanic only on the jobs assigned to them. */
    private void requireWorkAccess(ServiceJob job, Actor actor) {
        if (actor == null) {
            throw new AccessDeniedException("Please sign in.");
        }
        if (actor.isStaff()) {
            return;
        }
        if (actor.isMechanic() && job.getMechanic() != null && actor.mechanicId() != null
                && job.getMechanic().getId().equals(actor.mechanicId())) {
            return;
        }
        throw new AccessDeniedException("This job is not assigned to you.");
    }

    private Mechanic activeMechanic(Long mechanicId, String field) {
        Mechanic mechanic = mechanics.findById(mechanicId)
                .orElseThrow(() -> new BusinessRuleException(field, "Choose an existing mechanic."));
        if (!mechanic.isActive()) {
            throw new BusinessRuleException(field, mechanic.getName() + " is deactivated and cannot be assigned.");
        }
        return mechanic;
    }

    private ServiceTask findTask(ServiceJob job, Long taskId) {
        return job.getTasks().stream()
                .filter(task -> taskId.equals(task.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
    }

    /** Puts a part line's quantity back into stock - exactly once per line. */
    private void returnToStock(PartUsage usage) {
        if (!usage.isStockDeducted()) {
            return;
        }
        SparePart part = parts.findByIdForUpdate(usage.getSparePart().getId()).orElseThrow();
        part.setStockQuantity(part.getStockQuantity() + usage.getQuantity());
        usage.setStockDeducted(false);
    }

    private List<StatusAction> actionsFor(ServiceJob job, Invoice invoice, Actor actor) {
        List<StatusAction> actions = new ArrayList<>();
        boolean staff = actor.isStaff();
        StatusAction cancel = new StatusAction(JobStatus.CANCELLED, "Cancel job", "bi-x-lg", "outline-danger",
                "Cancel this job card? Any spare parts already recorded go back into stock and the appointment is cancelled.",
                true, null);
        switch (job.getStatus()) {
            case CREATED -> {
                if (staff) {
                    actions.add(cancel);
                }
            }
            case ASSIGNED -> {
                actions.add(new StatusAction(JobStatus.IN_PROGRESS, "Start work", "bi-play-fill", "primary", null, true, null));
                if (staff) {
                    actions.add(cancel);
                }
            }
            case IN_PROGRESS -> {
                actions.add(new StatusAction(JobStatus.COMPLETED, "Mark completed", "bi-check2-circle", "success",
                        "Mark this job as completed? Tasks and parts can then only be changed by the service advisor.", true, null));
                actions.add(new StatusAction(JobStatus.ON_HOLD, "Put on hold", "bi-pause-fill", "outline-secondary", null, true, null));
            }
            case ON_HOLD -> actions.add(new StatusAction(JobStatus.IN_PROGRESS, "Resume work", "bi-play-fill", "primary", null, true, null));
            case INVOICED -> {
                if (staff) {
                    boolean paid = invoice != null && invoice.getPaymentStatus() == PaymentStatus.PAID;
                    actions.add(new StatusAction(JobStatus.DELIVERED, "Hand over vehicle", "bi-key", "success",
                            "Confirm that the vehicle has been handed over to the customer?", paid,
                            paid ? null : "Available once the invoice is fully paid."));
                }
            }
            default -> {
                // COMPLETED: the next step is generating the invoice (a separate page); DELIVERED/CANCELLED are final
            }
        }
        return actions;
    }
}

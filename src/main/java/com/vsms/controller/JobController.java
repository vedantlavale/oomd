package com.vsms.controller;

import com.vsms.dto.AddPartForm;
import com.vsms.dto.AddTaskForm;
import com.vsms.dto.JobCreateForm;
import com.vsms.dto.JobEditForm;
import com.vsms.dto.JobNotesForm;
import com.vsms.entity.Appointment;
import com.vsms.entity.ServiceJob;
import com.vsms.enums.JobStatus;
import com.vsms.exception.BusinessRuleException;
import com.vsms.exception.ResourceNotFoundException;
import com.vsms.security.Actor;
import com.vsms.service.AppointmentService;
import com.vsms.service.MechanicService;
import com.vsms.service.ServiceJobService.JobCard;
import com.vsms.service.ServiceJobService;
import com.vsms.service.SparePartService;
import com.vsms.util.FormErrors;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Job cards. Staff see every job; mechanics see and work on only the jobs assigned to them
 * (the service layer enforces that, this controller only decides what to show).
 */
@Controller
@RequestMapping("/jobs")
@PreAuthorize("hasAnyRole('ADMIN','SERVICE_ADVISOR','MECHANIC')")
public class JobController {

    private static final String STAFF = "hasAnyRole('ADMIN','SERVICE_ADVISOR')";

    private final ServiceJobService jobService;
    private final AppointmentService appointmentService;
    private final MechanicService mechanicService;
    private final SparePartService partService;

    public JobController(ServiceJobService jobService, AppointmentService appointmentService,
                         MechanicService mechanicService, SparePartService partService) {
        this.jobService = jobService;
        this.appointmentService = appointmentService;
        this.mechanicService = mechanicService;
        this.partService = partService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) JobStatus status,
                       @RequestParam(required = false) Long mechanicId,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(defaultValue = "0") int page, Actor actor, Model model) {
        model.addAttribute("page", jobService.search(q, status, mechanicId, from, to, actor, page));
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        model.addAttribute("mechanicId", mechanicId);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("statuses", JobStatus.values());
        model.addAttribute("mechanics", actor.isStaff() ? mechanicService.findAll() : List.of());
        return "jobs/list";
    }

    // ------------------------------------------------------------------ create

    @GetMapping("/new")
    @PreAuthorize(STAFF)
    public String newForm(@RequestParam Long appointmentId, Model model) {
        Appointment appointment = appointmentService.get(appointmentId);
        JobCreateForm form = new JobCreateForm();
        form.setAppointmentId(appointmentId);
        form.setCustomerComplaint(appointment.getNotes() != null ? appointment.getNotes()
                : appointment.getServiceItem().getName());
        form.setLaborCharge(appointment.getServiceItem().getStandardPrice());
        model.addAttribute("jobCreateForm", form);
        return createView(model, appointment);
    }

    @PostMapping
    @PreAuthorize(STAFF)
    public String create(@Valid @ModelAttribute("jobCreateForm") JobCreateForm form, BindingResult result,
                         Actor actor, Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                ServiceJob job = jobService.createFromAppointment(form, actor);
                redirect.addFlashAttribute("success", "Job card " + job.getJobNumber() + " was created.");
                return "redirect:/jobs/" + job.getId();
            } catch (BusinessRuleException ex) {
                FormErrors.apply(result, ex);
            }
        }
        if (form.getAppointmentId() == null) {
            throw new ResourceNotFoundException("Appointment", null);
        }
        return createView(model, appointmentService.get(form.getAppointmentId()));
    }

    // -------------------------------------------------------------------- view

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Actor actor, Model model) {
        JobCard card = jobService.card(id, actor);
        model.addAttribute("card", card);
        model.addAttribute("job", card.job());
        model.addAttribute("steps", JobStatus.progressSteps());
        model.addAttribute("mechanics", card.canAssign() ? mechanicService.findActive() : List.of());
        model.addAttribute("partOptions", card.canWork() ? partService.findAll() : List.of());
        return "jobs/view";
    }

    // -------------------------------------------------------------------- edit

    @GetMapping("/{id}/edit")
    @PreAuthorize(STAFF)
    public String editForm(@PathVariable Long id, Actor actor, Model model) {
        ServiceJob job = jobService.get(id, actor);
        model.addAttribute("jobEditForm", JobEditForm.from(job));
        model.addAttribute("job", job);
        return "jobs/edit";
    }

    @PostMapping("/{id}")
    @PreAuthorize(STAFF)
    public String update(@PathVariable Long id, @Valid @ModelAttribute("jobEditForm") JobEditForm form,
                         BindingResult result, Actor actor, Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                jobService.updateDetails(id, form, actor);
                redirect.addFlashAttribute("success", "Job details were updated.");
                return "redirect:/jobs/" + id;
            } catch (BusinessRuleException ex) {
                FormErrors.apply(result, ex);
            }
        }
        model.addAttribute("job", jobService.get(id, actor));
        return "jobs/edit";
    }

    @PostMapping("/{id}/notes")
    public String notes(@PathVariable Long id, @Valid @ModelAttribute JobNotesForm form, BindingResult result,
                        Actor actor, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            redirect.addFlashAttribute("error", FormErrors.firstMessage(result));
        } else {
            jobService.updateNotes(id, form, actor);
            redirect.addFlashAttribute("success", "Diagnosis and repair notes were saved.");
        }
        return "redirect:/jobs/" + id + "#notes";
    }

    // ------------------------------------------------------- mechanic and status

    @PostMapping("/{id}/assign")
    @PreAuthorize(STAFF)
    public String assign(@PathVariable Long id, @RequestParam(required = false) Long mechanicId, Actor actor,
                         RedirectAttributes redirect) {
        if (mechanicId == null) {
            redirect.addFlashAttribute("error", "Choose a mechanic first.");
        } else {
            ServiceJob job = jobService.assignMechanic(id, mechanicId, actor);
            redirect.addFlashAttribute("success", job.getMechanic().getName() + " is now assigned to this job.");
        }
        return "redirect:/jobs/" + id;
    }

    @PostMapping("/{id}/status")
    public String changeStatus(@PathVariable Long id, @RequestParam JobStatus target, Actor actor,
                               RedirectAttributes redirect) {
        ServiceJob job = jobService.changeStatus(id, target, actor);
        redirect.addFlashAttribute("success", "Job " + job.getJobNumber() + " is now \"" + job.getStatus().getLabel() + "\".");
        return "redirect:/jobs/" + id;
    }

    // ------------------------------------------------------------------- tasks

    @PostMapping("/{id}/tasks")
    public String addTask(@PathVariable Long id, @Valid @ModelAttribute AddTaskForm form, BindingResult result,
                          Actor actor, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            redirect.addFlashAttribute("error", FormErrors.firstMessage(result));
        } else {
            jobService.addTask(id, form.getDescription(), actor);
            redirect.addFlashAttribute("success", "Task added.");
        }
        return "redirect:/jobs/" + id + "#tasks";
    }

    @PostMapping("/{id}/tasks/{taskId}/toggle")
    public String toggleTask(@PathVariable Long id, @PathVariable Long taskId, Actor actor) {
        jobService.toggleTask(id, taskId, actor);
        return "redirect:/jobs/" + id + "#tasks";
    }

    @PostMapping("/{id}/tasks/{taskId}/delete")
    public String removeTask(@PathVariable Long id, @PathVariable Long taskId, Actor actor, RedirectAttributes redirect) {
        jobService.removeTask(id, taskId, actor);
        redirect.addFlashAttribute("success", "Task removed.");
        return "redirect:/jobs/" + id + "#tasks";
    }

    // ------------------------------------------------------------------- parts

    @PostMapping("/{id}/parts")
    public String addPart(@PathVariable Long id, @Valid @ModelAttribute AddPartForm form, BindingResult result,
                          Actor actor, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            redirect.addFlashAttribute("error", FormErrors.firstMessage(result));
        } else {
            try {
                jobService.addPart(id, form.getSparePartId(), form.getQuantity(), actor);
                redirect.addFlashAttribute("success", "Part added to the job and removed from stock.");
            } catch (BusinessRuleException ex) {
                redirect.addFlashAttribute("error", ex.getMessage());
            }
        }
        return "redirect:/jobs/" + id + "#parts";
    }

    @PostMapping("/{id}/parts/{usageId}/delete")
    public String removePart(@PathVariable Long id, @PathVariable Long usageId, Actor actor, RedirectAttributes redirect) {
        jobService.removePart(id, usageId, actor);
        redirect.addFlashAttribute("success", "Part removed from the job and returned to stock.");
        return "redirect:/jobs/" + id + "#parts";
    }

    private String createView(Model model, Appointment appointment) {
        model.addAttribute("appointment", appointment);
        model.addAttribute("mechanics", mechanicService.findActive());
        return "jobs/new";
    }
}

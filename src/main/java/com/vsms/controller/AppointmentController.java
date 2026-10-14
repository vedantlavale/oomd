package com.vsms.controller;

import com.vsms.dto.AppointmentForm;
import com.vsms.dto.RescheduleForm;
import com.vsms.entity.Appointment;
import com.vsms.enums.AppointmentStatus;
import com.vsms.exception.BusinessRuleException;
import com.vsms.service.AppointmentService;
import com.vsms.service.CustomerService;
import com.vsms.service.ServiceCatalogService;
import com.vsms.service.VehicleService;
import com.vsms.util.FormErrors;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/appointments")
@PreAuthorize("hasAnyRole('ADMIN','SERVICE_ADVISOR')")
public class AppointmentController {

    private static final DateTimeFormatter SLOT_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final AppointmentService appointmentService;
    private final CustomerService customerService;
    private final VehicleService vehicleService;
    private final ServiceCatalogService catalogService;

    public AppointmentController(AppointmentService appointmentService, CustomerService customerService,
                                 VehicleService vehicleService, ServiceCatalogService catalogService) {
        this.appointmentService = appointmentService;
        this.customerService = customerService;
        this.vehicleService = vehicleService;
        this.catalogService = catalogService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) AppointmentStatus status,
                       @RequestParam(required = false) Long serviceId,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("page", appointmentService.search(q, status, from, to, serviceId, page));
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        model.addAttribute("serviceId", serviceId);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("statuses", AppointmentStatus.values());
        model.addAttribute("services", catalogService.findAll());
        return "appointments/list";
    }

    @GetMapping("/new")
    public String newForm(@RequestParam(required = false) Long customerId,
                          @RequestParam(required = false) Long vehicleId, Model model) {
        AppointmentForm form = new AppointmentForm();
        form.setCustomerId(customerId);
        form.setVehicleId(vehicleId);
        model.addAttribute("appointmentForm", form);
        return bookingView(model);
    }

    @PostMapping
    public String book(@Valid @ModelAttribute("appointmentForm") AppointmentForm form, BindingResult result,
                       Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                Appointment saved = appointmentService.book(form);
                redirect.addFlashAttribute("success", "Appointment booked for "
                        + saved.getAppointmentDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy")) + " at "
                        + saved.getAppointmentTime().format(DateTimeFormatter.ofPattern("hh:mm a")) + ".");
                return "redirect:/appointments/" + saved.getId();
            } catch (BusinessRuleException ex) {
                FormErrors.apply(result, ex);
            }
        }
        return bookingView(model);
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        model.addAttribute("details", appointmentService.details(id));
        return "appointments/view";
    }

    @GetMapping("/{id}/reschedule")
    public String rescheduleForm(@PathVariable Long id, Model model) {
        Appointment appointment = appointmentService.get(id);
        model.addAttribute("rescheduleForm", RescheduleForm.from(appointment));
        return rescheduleView(model, appointment);
    }

    @PostMapping("/{id}/reschedule")
    public String reschedule(@PathVariable Long id, @Valid @ModelAttribute("rescheduleForm") RescheduleForm form,
                             BindingResult result, Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                appointmentService.reschedule(id, form);
                redirect.addFlashAttribute("success", "The appointment was rescheduled.");
                return "redirect:/appointments/" + id;
            } catch (BusinessRuleException ex) {
                FormErrors.apply(result, ex);
            }
        }
        return rescheduleView(model, appointmentService.get(id));
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes redirect) {
        appointmentService.cancel(id);
        redirect.addFlashAttribute("success", "The appointment was cancelled.");
        return "redirect:/appointments/" + id;
    }

    /** Times (HH:mm) already booked for a service on a day; used by the booking form to grey them out. */
    @GetMapping("/slots")
    @ResponseBody
    public List<String> takenSlots(@RequestParam Long serviceId,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return appointmentService.takenTimes(serviceId, date).stream().map(t -> t.format(SLOT_FORMAT)).toList();
    }

    private String bookingView(Model model) {
        model.addAttribute("customers", customerService.findAll());
        model.addAttribute("vehicles", vehicleService.findAll());
        model.addAttribute("services", catalogService.findActive());
        addSlotAttributes(model);
        return "appointments/form";
    }

    private String rescheduleView(Model model, Appointment appointment) {
        model.addAttribute("appointment", appointment);
        addSlotAttributes(model);
        return "appointments/reschedule";
    }

    private void addSlotAttributes(Model model) {
        model.addAttribute("timeSlots", appointmentService.timeSlots());
        model.addAttribute("minDate", appointmentService.currentDate());
        model.addAttribute("maxDate", appointmentService.latestBookingDate());
    }
}

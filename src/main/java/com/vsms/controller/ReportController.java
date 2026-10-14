package com.vsms.controller;

import com.vsms.enums.AppointmentStatus;
import com.vsms.enums.JobStatus;
import com.vsms.enums.PaymentMethod;
import com.vsms.enums.PaymentRecordStatus;
import com.vsms.enums.PaymentStatus;
import com.vsms.service.MechanicService;
import com.vsms.service.ReportService;
import com.vsms.service.ServiceCatalogService;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Printable reports with date-range filters. */
@Controller
@RequestMapping("/reports")
@PreAuthorize("hasAnyRole('ADMIN','SERVICE_ADVISOR')")
public class ReportController {

    private final ReportService reportService;
    private final ServiceCatalogService catalogService;
    private final MechanicService mechanicService;
    private final Clock clock;

    public ReportController(ReportService reportService, ServiceCatalogService catalogService,
                            MechanicService mechanicService, Clock clock) {
        this.reportService = reportService;
        this.catalogService = catalogService;
        this.mechanicService = mechanicService;
        this.clock = clock;
    }

    /** Revenue summary; defaults to the current month so far. */
    @GetMapping
    public String revenue(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          Model model) {
        LocalDate today = LocalDate.now(clock);
        LocalDate start = from != null ? from : today.withDayOfMonth(1);
        LocalDate end = to != null ? to : today;
        if (start.isAfter(end)) {
            model.addAttribute("warning", "The start date was after the end date, so the two dates were swapped.");
            LocalDate swap = start;
            start = end;
            end = swap;
        }
        model.addAttribute("from", start);
        model.addAttribute("to", end);
        model.addAttribute("summary", reportService.revenue(start, end));
        return "reports/revenue";
    }

    @GetMapping("/appointments")
    public String appointments(@RequestParam(required = false) String q,
                               @RequestParam(required = false) AppointmentStatus status,
                               @RequestParam(required = false) Long serviceId,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                               Model model) {
        LocalDate[] range = orderedRange(from, to, model);
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        model.addAttribute("serviceId", serviceId);
        model.addAttribute("from", range[0]);
        model.addAttribute("to", range[1]);
        model.addAttribute("statuses", AppointmentStatus.values());
        model.addAttribute("services", catalogService.findAll());
        model.addAttribute("report", reportService.appointments(q, status, serviceId, range[0], range[1]));
        model.addAttribute("limit", ReportService.ROW_LIMIT);
        return "reports/appointments";
    }

    @GetMapping("/jobs")
    public String jobs(@RequestParam(required = false) String q,
                       @RequestParam(required = false) JobStatus status,
                       @RequestParam(required = false) Long mechanicId,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       Model model) {
        LocalDate[] range = orderedRange(from, to, model);
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        model.addAttribute("mechanicId", mechanicId);
        model.addAttribute("from", range[0]);
        model.addAttribute("to", range[1]);
        model.addAttribute("statuses", JobStatus.values());
        model.addAttribute("mechanics", mechanicService.findAll());
        model.addAttribute("report", reportService.jobs(q, status, mechanicId, range[0], range[1]));
        model.addAttribute("limit", ReportService.ROW_LIMIT);
        return "reports/jobs";
    }

    @GetMapping("/invoices")
    public String invoices(@RequestParam(required = false) String q,
                           @RequestParam(required = false) PaymentStatus status,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           Model model) {
        LocalDate[] range = orderedRange(from, to, model);
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        model.addAttribute("from", range[0]);
        model.addAttribute("to", range[1]);
        model.addAttribute("statuses", PaymentStatus.values());
        model.addAttribute("report", reportService.invoices(q, status, range[0], range[1]));
        model.addAttribute("limit", ReportService.ROW_LIMIT);
        return "reports/invoices";
    }

    @GetMapping("/payments")
    public String payments(@RequestParam(required = false) String q,
                           @RequestParam(required = false) PaymentMethod method,
                           @RequestParam(required = false) PaymentRecordStatus status,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           Model model) {
        LocalDate[] range = orderedRange(from, to, model);
        model.addAttribute("q", q);
        model.addAttribute("method", method);
        model.addAttribute("status", status);
        model.addAttribute("from", range[0]);
        model.addAttribute("to", range[1]);
        model.addAttribute("methods", PaymentMethod.values());
        model.addAttribute("recordStatuses", PaymentRecordStatus.values());
        model.addAttribute("report", reportService.payments(q, method, status, range[0], range[1]));
        model.addAttribute("limit", ReportService.ROW_LIMIT);
        return "reports/payments";
    }

    /** Both dates are optional; if they are the wrong way round they are swapped with a notice. */
    private LocalDate[] orderedRange(LocalDate from, LocalDate to, Model model) {
        if (from != null && to != null && from.isAfter(to)) {
            model.addAttribute("warning", "The start date was after the end date, so the two dates were swapped.");
            return new LocalDate[]{to, from};
        }
        return new LocalDate[]{from, to};
    }
}
